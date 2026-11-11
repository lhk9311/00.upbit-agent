package com.lhk.upbitagent.backtest;

import com.lhk.upbitagent.agent.AgentEngine;
import com.lhk.upbitagent.agent.Strategy;
import com.lhk.upbitagent.agent.TradeMode;
import com.lhk.upbitagent.domain.Candle;
import com.lhk.upbitagent.domain.CandleUnit;
import com.lhk.upbitagent.screener.ScreenerCondition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktestEngineTest {

    private static final long H = 3_600_000L;
    private static final long T0 = 1_700_000_000_000L / H * H; // 정시에 맞춘 시작 시각

    /** 수수료·슬리피지 0, 재진입 대기 없음 → 숫자를 눈으로 확인하기 쉽게 */
    private final BacktestEngine engine = new BacktestEngine(new AgentEngine.Config(0, 0), 0);

    /** 조건이 전부 꺼진 검색 (모든 종목 통과) */
    private static ScreenerCondition all(ScreenerCondition.SortBy sort) {
        return new ScreenerCondition(CandleUnit.MIN60, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, sort, 10);
    }

    private static Strategy strategy(int maxPositions, Double tp, Double sl, Integer hold) {
        return new Strategy(1L, "bt", all(ScreenerCondition.SortBy.TRADE_AMOUNT), null, 100_000, maxPositions,
                tp, sl, null, hold, null, TradeMode.PAPER, true, 0L);
    }

    /** 같은 가격이 이어지다가 지정한 봉부터 가격이 바뀌는 캔들 */
    private static List<Candle> series(int n, double base, int changeAt, double high, double low, double close) {
        List<Candle> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (i == changeAt) {
                list.add(new Candle(T0 + i * H, base, high, low, close, 10, 1e9));
            } else {
                double p = i > changeAt ? close : base;
                list.add(new Candle(T0 + i * H, p, p, p, p, 10, 1e9));
            }
        }
        return list;
    }

    @Test
    void 캔들이_부족하면_안내만_하고_거래없음() {
        Map<String, List<Candle>> data = Map.of("KRW-A", series(30, 100, 99, 100, 100, 100));
        BacktestEngine.Result r = engine.run(strategy(1, 5.0, 3.0, null), CandleUnit.MIN60, data, Map.of(), Set.of());
        assertEquals(0, r.tradeCount());
        assertTrue(r.notes().get(0).contains("캔들이 부족"));
    }

    @Test
    void 익절_봉의_고가로_체결() {
        // 69번 봉 종가(100)에 매수 → 80번 봉에서 고가 106 → 익절가 105 에 매도
        Map<String, List<Candle>> data = Map.of("KRW-A", series(100, 100, 80, 106, 99, 104));
        BacktestEngine.Result r = engine.run(strategy(1, 5.0, 3.0, null), CandleUnit.MIN60, data,
                Map.of("KRW-A", "에이"), Set.of());

        BacktestEngine.BtTrade first = r.trades().get(0);
        assertEquals("익절", first.reason());
        assertEquals(100.0, first.buyPrice(), 1e-9);
        assertEquals(105.0, first.sellPrice(), 1e-9);
        assertEquals(5.0, first.pnlRate(), 1e-9);
        assertEquals(T0 + 70 * H, first.buyAt());      // 69번 봉이 끝난 시각
        assertEquals(T0 + 81 * H, first.sellAt());     // 80번 봉이 끝난 시각
    }

    @Test
    void 한_봉에서_손절가와_익절가_모두_닿으면_손절() {
        Map<String, List<Candle>> data = Map.of("KRW-A", series(100, 100, 80, 110, 90, 100));
        BacktestEngine.Result r = engine.run(strategy(1, 5.0, 3.0, null), CandleUnit.MIN60, data, Map.of(), Set.of());
        assertEquals("손절", r.trades().get(0).reason());
        assertEquals(97.0, r.trades().get(0).sellPrice(), 1e-9);
    }

    @Test
    void 갭하락이면_시가에_손절() {
        List<Candle> list = series(100, 100, 999, 100, 100, 100);
        list.set(80, new Candle(T0 + 80 * H, 90, 91, 88, 89, 10, 1e9)); // 시가부터 손절가(97) 아래
        BacktestEngine.Result r = engine.run(strategy(1, 5.0, 3.0, null), CandleUnit.MIN60,
                Map.of("KRW-A", list), Map.of(), Set.of());
        assertEquals("손절", r.trades().get(0).reason());
        assertEquals(90.0, r.trades().get(0).sellPrice(), 1e-9);
    }

    @Test
    void 보유시간_초과는_종가에_매도하고_자리가_나면_다시_매수() {
        Map<String, List<Candle>> data = Map.of("KRW-A", series(100, 100, 999, 100, 100, 100));
        BacktestEngine.Result r = engine.run(strategy(1, null, null, 5), CandleUnit.MIN60, data, Map.of(), Set.of());
        assertTrue(r.tradeCount() >= 5, "trades=" + r.tradeCount());
        assertEquals("보유시간 초과", r.trades().get(0).reason());
        assertEquals(5 * H, r.trades().get(0).sellAt() - r.trades().get(0).buyAt());
        assertEquals(0.0, r.totalPnl(), 1e-9); // 가격이 그대로고 수수료 0 이면 손익 0
    }

    @Test
    void 최대보유수만큼만_매수하고_기간종료시_평가() {
        Map<String, List<Candle>> data = new HashMap<>();
        data.put("KRW-A", series(100, 100, 999, 100, 100, 100));
        data.put("KRW-B", series(100, 50, 999, 50, 50, 50));
        data.put("KRW-C", series(100, 10, 999, 10, 10, 10));
        BacktestEngine.Result r = engine.run(strategy(2, 50.0, 50.0, null), CandleUnit.MIN60, data, Map.of(), Set.of());

        assertEquals(2, r.tradeCount());
        assertTrue(r.trades().stream().allMatch(BacktestEngine.BtTrade::openAtEnd));
        assertEquals(200_000.0, r.capital(), 1e-9);
    }

    @Test
    void 수수료와_슬리피지가_있으면_가격이_그대로여도_손실() {
        BacktestEngine real = new BacktestEngine(new AgentEngine.Config(0.0005, 0.001), 0);
        Map<String, List<Candle>> data = Map.of("KRW-A", series(100, 100, 999, 100, 100, 100));
        BacktestEngine.Result r = real.run(strategy(1, null, null, 5), CandleUnit.MIN60, data, Map.of(), Set.of());
        assertTrue(r.totalPnl() < 0);
        assertTrue(r.maxDrawdown() > 0);
        assertNull(r.benchmarkReturnPct()); // 비트코인 데이터가 없으면 비교값 없음
    }

    @Test
    void 비트코인_보유_수익률_비교() {
        Map<String, List<Candle>> data = Map.of("KRW-BTC", series(100, 100, 90, 110, 100, 110));
        BacktestEngine.Result r = engine.run(strategy(1, null, null, 1000), CandleUnit.MIN60, data, Map.of(), Set.of());
        assertEquals(10.0, r.benchmarkReturnPct(), 1e-9);
    }
}
