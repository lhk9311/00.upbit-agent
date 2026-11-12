package com.lhk.upbitagent.agent;

import com.lhk.upbitagent.screener.ScreenerResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEngineTest {

    private static final long HOUR = 3_600_000L;
    private static final long NOW = 100 * HOUR;

    /** 수수료·슬리피지 0 으로 두면 계산을 눈으로 확인하기 쉽다 */
    private final AgentEngine engine = new AgentEngine(new AgentEngine.Config(0, 0));

    private static Strategy strategy(int maxPositions, Double tp, Double sl, Double trail, Integer hold, Double dailyLimit) {
        return new Strategy(1L, "테스트", null, null, 100_000, maxPositions, tp, sl, trail, hold, dailyLimit,
                TradeMode.PAPER, true, 0L, null);
    }

    private static Trade open(long id, String market, double buyPrice, double highest, long buyAt) {
        return new Trade(id, 1L, "테스트", market, market, TradeStatus.OPEN, TradeMode.PAPER,
                buyPrice, 1, buyPrice, 0, buyAt, highest,
                null, null, null, null, null, null, null);
    }

    private static ScreenerResult.Item item(String market, double price) {
        return new ScreenerResult.Item(market, market, price, 0, 1e10, null, null, null, null, List.of());
    }

    // ---------- 매도 규칙 ----------

    @Test
    void 손절() {
        Strategy s = strategy(3, 5.0, 3.0, null, null, null);
        var d = engine.decide(s, List.of(open(1, "KRW-A", 100, 100, NOW)), Map.of("KRW-A", 96.9),
                List.of(), 0, Set.of(), NOW);
        assertEquals(1, d.sells().size());
        assertTrue(d.sells().get(0).reason().startsWith("손절"));
    }

    @Test
    void 익절() {
        Strategy s = strategy(3, 5.0, 3.0, null, null, null);
        var d = engine.decide(s, List.of(open(1, "KRW-A", 100, 100, NOW)), Map.of("KRW-A", 105.0),
                List.of(), 0, Set.of(), NOW);
        assertTrue(d.sells().get(0).reason().startsWith("익절"));
    }

    @Test
    void 규칙_범위_안이면_보유() {
        Strategy s = strategy(3, 5.0, 3.0, null, null, null);
        var d = engine.decide(s, List.of(open(1, "KRW-A", 100, 100, NOW)), Map.of("KRW-A", 102.0),
                List.of(), 0, Set.of(), NOW);
        assertEquals(0, d.sells().size());
    }

    @Test
    void 트레일링은_수익구간에서만_작동() {
        Strategy s = strategy(3, null, null, 2.0, null, null);
        // 최고가 110 → 현재 107.5 (최고가 대비 -2.27%) → 매도
        var d1 = engine.decide(s, List.of(open(1, "KRW-A", 100, 110, NOW)), Map.of("KRW-A", 107.5),
                List.of(), 0, Set.of(), NOW);
        assertTrue(d1.sells().get(0).reason().startsWith("트레일링"));

        // 한 번도 매수가를 넘지 못한 경우(최고가 = 매수가)는 트레일링 대상 아님
        var d2 = engine.decide(s, List.of(open(1, "KRW-A", 100, 100, NOW)), Map.of("KRW-A", 97.0),
                List.of(), 0, Set.of(), NOW);
        assertEquals(0, d2.sells().size());
    }

    @Test
    void 최고가_갱신() {
        Strategy s = strategy(3, null, null, 2.0, null, null);
        var d = engine.decide(s, List.of(open(7, "KRW-A", 100, 103, NOW)), Map.of("KRW-A", 104.0),
                List.of(), 0, Set.of(), NOW);
        assertEquals(1, d.highs().size());
        assertEquals(104.0, d.highs().get(0).highestPrice(), 1e-9);
        assertEquals(0, d.sells().size());
    }

    @Test
    void 보유시간_초과() {
        Strategy s = strategy(3, null, null, null, 24, null);
        var d = engine.decide(s, List.of(open(1, "KRW-A", 100, 100, NOW - 25 * HOUR)), Map.of("KRW-A", 100.0),
                List.of(), 0, Set.of(), NOW);
        assertTrue(d.sells().get(0).reason().startsWith("보유시간"));
    }

    @Test
    void 현재가_없으면_판단_보류() {
        Strategy s = strategy(3, 5.0, 3.0, null, null, null);
        var d = engine.decide(s, List.of(open(1, "KRW-A", 100, 100, NOW)), Map.of(),
                List.of(), 0, Set.of(), NOW);
        assertEquals(0, d.sells().size());
    }

    // ---------- 매수 ----------

    @Test
    void 빈자리만큼만_매수하고_보유중_재진입대기_종목은_제외() {
        Strategy s = strategy(3, null, null, null, null, null);
        List<Trade> holding = List.of(open(1, "KRW-A", 100, 100, NOW));
        List<ScreenerResult.Item> cands = List.of(
                item("KRW-A", 100),  // 보유 중
                item("KRW-B", 100),  // 재진입 대기
                item("KRW-C", 100),
                item("KRW-D", 100),
                item("KRW-E", 100)); // 자리 없음

        var d = engine.decide(s, holding, Map.of("KRW-A", 100.0), cands, 0, Set.of("KRW-B"), NOW);
        assertEquals(List.of("KRW-C", "KRW-D"), d.buys().stream().map(AgentEngine.BuyOrder::market).toList());
    }

    @Test
    void 매도한_자리는_같은_주기에_다시_채우되_같은_종목은_안산다() {
        Strategy s = strategy(1, 5.0, null, null, null, null);
        List<Trade> holding = List.of(open(1, "KRW-A", 100, 100, NOW));
        var d = engine.decide(s, holding, Map.of("KRW-A", 106.0),
                List.of(item("KRW-A", 106), item("KRW-B", 50)), 0, Set.of(), NOW);
        assertEquals(1, d.sells().size());
        assertEquals(List.of("KRW-B"), d.buys().stream().map(AgentEngine.BuyOrder::market).toList());
    }

    @Test
    void 일일손실한도_도달시_매수중단() {
        Strategy s = strategy(3, null, null, null, null, 30_000.0);
        var d = engine.decide(s, List.of(), Map.of(), List.of(item("KRW-A", 100)), -30_000, Set.of(), NOW);
        assertEquals(0, d.buys().size());
        assertEquals(1, d.notes().size());
    }

    @Test
    void 정지된_전략은_매도만() {
        Strategy on = strategy(3, 5.0, null, null, null, null);
        Strategy off = new Strategy(1L, "x", null, null, 100_000, 3, 5.0, null, null, null, null,
                TradeMode.PAPER, false, 0L, null);
        var d = engine.decide(off, List.of(open(1, "KRW-A", 100, 100, NOW)), Map.of("KRW-A", 106.0),
                List.of(item("KRW-B", 100)), 0, Set.of(), NOW);
        assertEquals(1, d.sells().size());
        assertEquals(0, d.buys().size());
        assertEquals(1, engine.decide(on, List.of(), Map.of(), List.of(item("KRW-B", 100)), 0, Set.of(), NOW).buys().size());
    }

    @Test
    void 데이터_수집중이면_매수판단_안함() {
        Strategy s = strategy(3, null, null, null, null, null);
        var d = engine.decide(s, List.of(), Map.of(), null, 0, Set.of(), NOW);
        assertEquals(0, d.buys().size());
        assertEquals(1, d.notes().size());
    }

    // ---------- 체결 계산 ----------

    @Test
    void 수수료와_슬리피지를_반영한_손익() {
        AgentEngine real = new AgentEngine(new AgentEngine.Config(0.0005, 0.001));
        Strategy s = strategy(3, null, null, null, null, null);

        Trade bought = real.openTrade(s, new AgentEngine.BuyOrder("KRW-A", "A", 1000), NOW);
        assertEquals(1001.0, bought.buyPrice(), 1e-9);                   // 0.1% 불리하게 체결
        assertEquals(100_000.0, bought.cost(), 1e-6);                    // 수수료 포함 주문금액 그대로
        assertNull(bought.sellPrice());

        Trade sold = real.closeTrade(bought, 1000, "테스트", NOW + HOUR);
        // 가격이 그대로여도 수수료 2번 + 슬리피지 2번만큼 손실
        assertTrue(sold.pnl() < 0);
        assertEquals(-0.2995, sold.pnlRate(), 1e-3);
        assertEquals(TradeStatus.CLOSED, sold.status());
    }
}
