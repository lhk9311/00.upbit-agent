package com.lhk.upbitagent.agent;

import com.lhk.upbitagent.domain.CandleUnit;
import com.lhk.upbitagent.screener.ScreenerCondition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyRequestTest {

    private static final ScreenerCondition COND = new ScreenerCondition(CandleUnit.MIN60, null, null, null, null,
            null, true, null, null, null, null, null, null, null, null, null, null, null, null, null, 10);

    private static String errorOf(StrategyRequest r) {
        try {
            r.toStrategy(null, 0);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    @Test
    void 정상_요청은_모의매매_전략으로_변환() {
        StrategyRequest r = new StrategyRequest("  눌림목  ", COND, " 일봉 눌림목 ", 100_000.0, 3,
                5.0, 3.0, null, null, 30_000.0, null);
        Strategy s = r.toStrategy(7L, 123L);
        assertEquals("눌림목", s.name());
        assertEquals("일봉 눌림목", s.naturalText());
        assertEquals(TradeMode.PAPER, s.mode());
        assertTrue(s.enabled());
        assertEquals(7L, s.id().longValue());
    }

    @Test
    void 최소_주문금액_미만이면_거절() {
        String err = errorOf(new StrategyRequest("a", COND, null, 4_000.0, 3, 5.0, 3.0, null, null, null, true));
        assertTrue(err != null && err.contains("5,000원"), err);
    }

    @Test
    void 매도규칙이_하나도_없으면_거절() {
        String err = errorOf(new StrategyRequest("a", COND, null, 10_000.0, 3, null, null, null, null, null, true));
        assertTrue(err != null && err.contains("매도 규칙"), err);
    }

    @Test
    void 최대보유수_범위와_음수_퍼센트_검사() {
        String err = errorOf(new StrategyRequest("a", COND, null, 10_000.0, 11, -1.0, 3.0, null, null, null, true));
        assertTrue(err.contains("최대 보유 종목 수"), err);
        assertTrue(err.contains("익절 %"), err);
    }

    @Test
    void 이름이_비면_기본이름() {
        Strategy s = new StrategyRequest(" ", COND, null, 10_000.0, 1, null, null, null, 24, null, false)
                .toStrategy(3L, 0);
        assertEquals("전략 3", s.name());
        assertTrue(!s.enabled());
    }
}
