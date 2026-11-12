package com.lhk.upbitagent.agent;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuyWindowTest {

    /** 한국 시간 2026-11-12 hh:mm:ss → epoch ms */
    private static long kst(int h, int m, int s) {
        return LocalDateTime.of(2026, 11, 12, h, m, s).atZone(BuyWindow.KST).toInstant().toEpochMilli();
    }

    @Test
    void 아홉시부터_아홉시십분까지_끝은_포함_안함() {
        BuyWindow w = BuyWindow.of("9:00", "09:10");
        assertEquals("09:00", w.from());
        assertFalse(w.contains(kst(8, 59, 59)));
        assertTrue(w.contains(kst(9, 0, 0)));
        assertTrue(w.contains(kst(9, 9, 59)));
        assertFalse(w.contains(kst(9, 10, 0)));
        assertEquals(kst(9, 0, 0), w.openedAt(kst(9, 5, 0)).longValue());
        assertEquals(10, w.minutes());
    }

    @Test
    void 자정을_넘는_시간대() {
        BuyWindow w = BuyWindow.of("23:00", "01:00");
        assertTrue(w.contains(kst(23, 30, 0)));
        assertTrue(w.contains(kst(0, 30, 0)));
        assertFalse(w.contains(kst(1, 0, 0)));
        assertEquals(kst(23, 0, 0) - 24 * 3_600_000L, w.openedAt(kst(0, 30, 0)).longValue()); // 어제 23시 시작
        assertEquals(120, w.minutes());
    }

    @Test
    void 비우면_제한_없음_하나만_쓰면_오류() {
        assertNull(BuyWindow.of(null, " "));
        assertThrows(IllegalArgumentException.class, () -> BuyWindow.of("09:00", null));
        assertThrows(IllegalArgumentException.class, () -> BuyWindow.of("9시", "09:10"));
        assertThrows(IllegalArgumentException.class, () -> BuyWindow.of("09:00", "09:00"));
    }

    @Test
    void 봉과_겹치는지() {
        BuyWindow w = BuyWindow.of("09:00", "09:10");
        assertTrue(w.overlaps(kst(9, 0, 0), kst(9, 15, 0)));    // 15분봉 09:00~09:15
        assertTrue(w.overlaps(kst(8, 0, 0), kst(9, 0, 0) + 1)); // 1ms 라도 걸침
        assertFalse(w.overlaps(kst(8, 45, 0), kst(9, 0, 0)));  // 08:45~09:00 (끝 미포함)
        assertFalse(w.overlaps(kst(9, 15, 0), kst(9, 30, 0)));
        assertTrue(w.overlaps(kst(9, 5, 0), kst(9, 20, 0)));
    }

    @Test
    void 시간대_밖이면_매도만_하고_매수는_안함() {
        AgentEngine engine = new AgentEngine(new AgentEngine.Config(0, 0));
        Strategy s = new Strategy(1L, "단타", null, null, 10_000, 3, 8.0, 3.0, null, null, null,
                TradeMode.PAPER, true, 0L, BuyWindow.of("09:00", "09:10"));
        Trade held = new Trade(1L, 1L, "단타", "KRW-A", "A", TradeStatus.OPEN, TradeMode.PAPER,
                100, 100, 10_000, 0, 0L, 100, null, null, null, null, null, null, null);
        var items = List.of(new com.lhk.upbitagent.screener.ScreenerResult.Item("KRW-B", "B", 100, 6, 1e10,
                null, null, null, null, List.of()));

        var outside = engine.decide(s, List.of(held), Map.of("KRW-A", 109.0), items, 0, Set.of(), kst(10, 0, 0));
        assertEquals(1, outside.sells().size()); // +9% 익절은 시간과 상관없이
        assertTrue(outside.buys().isEmpty());

        var inside = engine.decide(s, List.of(), Map.of(), items, 0, Set.of(), kst(9, 3, 0));
        assertEquals(1, inside.buys().size());
    }

    @Test
    void 요청_검증() {
        StrategyRequest bad = new StrategyRequest("a", null, null, 10_000.0, 3, 8.0, 3.0, null, null, null,
                true, "09:00", null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> bad.toStrategy(null, 0));
        assertTrue(e.getMessage().contains("매수 시간대"), e.getMessage());
    }
}
