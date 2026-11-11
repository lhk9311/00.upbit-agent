package com.lhk.upbitagent.nl;

import com.lhk.upbitagent.domain.CandleUnit;
import com.lhk.upbitagent.screener.ScreenerCondition;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NlConditionMapperTest {

    @Test
    void 정상_값은_그대로_매핑하고_거래대금은_억을_원으로() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("explanation", "1시간봉, 거래대금 100억 이상, 과매도(RSI 30 이하)로 해석했어요");
        raw.put("unsupported", List.of());
        raw.put("unit", "MIN60");
        raw.put("minTradeAmountEok", 100);
        raw.put("rsiMax", 30);
        raw.put("bbLowerTouchWithin", 3);
        raw.put("excludeWarning", true);
        raw.put("sortBy", "RSI_ASC");

        NlConditionMapper.Result r = NlConditionMapper.map(raw);
        ScreenerCondition c = r.condition();

        assertEquals(CandleUnit.MIN60, c.unit());
        assertEquals(10_000_000_000.0, c.minTradeAmount(), 1e-6);
        assertEquals(30.0, c.rsiMax(), 1e-9);
        assertNull(c.rsiMin());
        assertEquals(3, c.bbLowerTouchWithin().intValue());
        assertEquals(Boolean.TRUE, c.excludeWarning());
        assertEquals(ScreenerCondition.SortBy.RSI_ASC, c.sortBy());
        assertEquals(10, c.limit().intValue());
        assertTrue(r.adjustments().isEmpty());
    }

    @Test
    void 말하지_않은_조건은_모두_꺼짐() {
        NlConditionMapper.Result r = NlConditionMapper.map(Map.of("explanation", "x", "unsupported", List.of()));
        assertTrue(!r.condition().needsIndicators());
        assertNull(r.condition().unit());
        assertNull(r.condition().minTradeAmount());
        assertNull(r.condition().excludeWarning());
    }

    @Test
    void 범위를_벗어난_값은_바로잡고_기록() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("rsiMin", 150);
        raw.put("rsiMax", 20);           // 150 → 100 으로 조정 후 min>max 라 서로 바뀜
        raw.put("goldenCrossWithin", 0);
        raw.put("volumeSurgeRatio", -2);
        raw.put("unit", "WEEK");          // 지원하지 않는 기준봉

        NlConditionMapper.Result r = NlConditionMapper.map(raw);
        assertEquals(20.0, r.condition().rsiMin(), 1e-9);
        assertEquals(100.0, r.condition().rsiMax(), 1e-9);
        assertEquals(1, r.condition().goldenCrossWithin().intValue());
        assertNull(r.condition().volumeSurgeRatio());
        assertNull(r.condition().unit());
        assertEquals(5, r.adjustments().size());
    }

    @Test
    void false_는_조건을_끈_것으로_처리() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("maAligned", false);
        raw.put("priceAboveMa20", true);
        NlConditionMapper.Result r = NlConditionMapper.map(raw);
        assertNull(r.condition().maAligned());
        assertEquals(Boolean.TRUE, r.condition().priceAboveMa20());
    }

    @Test
    void 지원하지_않는_요청은_그대로_전달() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("unsupported", List.of("호재 뉴스 있는", " "));
        NlConditionMapper.Result r = NlConditionMapper.map(raw);
        assertEquals(List.of("호재 뉴스 있는"), r.unsupported());
    }
}
