package com.lhk.upbitagent.screener;

import com.lhk.upbitagent.domain.Candle;
import com.lhk.upbitagent.domain.CandleUnit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenerEngineTest {

    private final ScreenerEngine engine = new ScreenerEngine();

    /** 조건이 전부 꺼진 상태 */
    private static ScreenerCondition none() {
        return new ScreenerCondition(CandleUnit.MIN60, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** 원하는 필드만 바꾼 조건을 만드는 작은 빌더 */
    private static final class C {
        Double minTradeAmount, rsiMax, volumeSurgeRatio;
        Boolean excludeWarning, maAligned;
        ScreenerCondition.SortBy sortBy;
        Integer limit;

        ScreenerCondition build() {
            return new ScreenerCondition(CandleUnit.MIN60, null, null, minTradeAmount, null, null, excludeWarning,
                    maAligned, null, null, null, rsiMax, null, null, volumeSurgeRatio, null, null, null, null,
                    sortBy, limit);
        }
    }

    /** 일정 비율로 오르거나(step>0) 내리는(step<0) 캔들 100개 */
    private static List<Candle> trend(double start, double step) {
        List<Candle> list = new ArrayList<>();
        double p = start;
        for (int i = 0; i < 100; i++) {
            double next = p * (1 + step);
            list.add(new Candle(i * 3_600_000L, p, Math.max(p, next) * 1.001, Math.min(p, next) * 0.999, next, 100));
            p = next;
        }
        return list;
    }

    private static MarketSnapshot snap(String market, double amount, boolean warning, List<Candle> candles) {
        double price = candles.isEmpty() ? 100 : candles.get(candles.size() - 1).close();
        return new MarketSnapshot(market, market, warning, price, 1.0, amount, candles);
    }

    private static List<MarketSnapshot> universe(int count) {
        List<MarketSnapshot> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(snap("KRW-C" + i, (i + 1) * 1e9, false, trend(100, i % 2 == 0 ? 0.01 : -0.01)));
        }
        return list;
    }

    @Test
    void 조건이_없어도_최대_10개만_반환() {
        ScreenerResult r = engine.evaluate(universe(30), none());
        assertEquals(30, r.evaluatedCount());
        assertEquals(30, r.matchedCount());
        assertEquals(10, r.items().size());
    }

    @Test
    void 조건을_추가할수록_결과가_줄어든다() {
        List<MarketSnapshot> u = universe(30);

        C c = new C();
        c.minTradeAmount = 10e9; // 상위 21개만 통과
        int step1 = engine.evaluate(u, c.build()).matchedCount();

        c.maAligned = true;      // 그중 상승 추세만
        int step2 = engine.evaluate(u, c.build()).matchedCount();

        c.rsiMax = 50.0;         // 상승 추세는 RSI 가 높으므로 0개
        int step3 = engine.evaluate(u, c.build()).matchedCount();

        assertEquals(21, step1);
        assertTrue(step2 < step1, step2 + " < " + step1);
        assertEquals(0, step3);
    }

    @Test
    void 유의종목_제외() {
        List<MarketSnapshot> u = List.of(
                snap("KRW-OK", 1e10, false, trend(100, 0.01)),
                snap("KRW-WARN", 1e10, true, trend(100, 0.01)));
        C c = new C();
        c.excludeWarning = true;
        ScreenerResult r = engine.evaluate(u, c.build());
        assertEquals(1, r.matchedCount());
        assertEquals("KRW-OK", r.items().get(0).market());
    }

    @Test
    void 지표조건이_켜졌는데_캔들이_부족하면_탈락() {
        List<MarketSnapshot> u = List.of(snap("KRW-NEW", 1e10, false, trend(100, 0.01).subList(0, 30)));
        C c = new C();
        c.maAligned = true;
        assertEquals(0, engine.evaluate(u, c.build()).matchedCount());

        // 지표 조건이 없으면 캔들이 부족해도 통과 (지표 값은 null)
        ScreenerResult r = engine.evaluate(u, none());
        assertEquals(1, r.matchedCount());
        assertNull(r.items().get(0).rsi());
    }

    @Test
    void 거래량_급증_조건과_정렬() {
        List<Candle> surge = new ArrayList<>(trend(100, 0.001));
        // 직전 완성봉(뒤에서 두 번째) 거래량을 평균의 5배로
        Candle c98 = surge.get(98);
        surge.set(98, new Candle(c98.time(), c98.open(), c98.high(), c98.low(), c98.close(), 500));

        List<MarketSnapshot> u = List.of(
                snap("KRW-FLAT", 5e10, false, trend(100, 0.001)),
                snap("KRW-SURGE", 1e10, false, surge));

        C c = new C();
        c.volumeSurgeRatio = 3.0;
        ScreenerResult r = engine.evaluate(u, c.build());
        assertEquals(1, r.matchedCount());
        assertEquals("KRW-SURGE", r.items().get(0).market());
        assertEquals(5.0, r.items().get(0).volumeRatio(), 1e-9);
        assertTrue(r.items().get(0).matched().contains("거래량 3배↑"));
    }

    @Test
    void 거래대금순_정렬_및_limit() {
        C c = new C();
        c.sortBy = ScreenerCondition.SortBy.TRADE_AMOUNT;
        c.limit = 3;
        ScreenerResult r = engine.evaluate(universe(30), c.build());
        assertEquals(3, r.items().size());
        assertEquals("KRW-C29", r.items().get(0).market());
        assertEquals("KRW-C28", r.items().get(1).market());

        c.limit = 50; // 10개 초과 요청도 10개로 제한
        assertEquals(10, engine.evaluate(universe(30), c.build()).items().size());
    }
}
