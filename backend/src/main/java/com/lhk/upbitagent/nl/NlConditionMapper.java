package com.lhk.upbitagent.nl;

import com.lhk.upbitagent.domain.CandleUnit;
import com.lhk.upbitagent.screener.ScreenerCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM 이 돌려준 값(Map)을 ScreenerCondition 으로 바꾸고, 범위를 벗어난 값을 바로잡는다.
 * 구조화된 출력이 JSON 모양은 보장하지만 숫자 범위(RSI 0~100 등)까지는 보장하지 않으므로
 * 여기서 한 번 더 검증한다. Spring·Jackson 에 의존하지 않는 순수 Java.
 */
public final class NlConditionMapper {

    private static final int MAX_WITHIN = 50;

    private NlConditionMapper() {
    }

    public record Result(
            ScreenerCondition condition,
            String explanation,
            List<String> unsupported,
            List<String> adjustments   // 서버가 바로잡은 내용 (화면에 경고로 표시)
    ) {
    }

    public static Result map(Map<String, Object> raw) {
        List<String> adj = new ArrayList<>();

        CandleUnit unit = enumOrNull(CandleUnit.class, raw.get("unit"), "unit", adj);
        ScreenerCondition.SortBy sortBy = enumOrNull(ScreenerCondition.SortBy.class, raw.get("sortBy"), "sortBy", adj);

        Double minPrice = nonNegative(raw, "minPrice", adj);
        Double maxPrice = nonNegative(raw, "maxPrice", adj);
        Double[] price = ordered(minPrice, maxPrice, "가격", adj);

        Double eok = nonNegative(raw, "minTradeAmountEok", adj);
        Double minTradeAmount = eok == null ? null : eok * 100_000_000;

        Double[] change = ordered(num(raw, "minChangeRate"), num(raw, "maxChangeRate"), "등락률", adj);

        Double[] rsi = ordered(
                clamp(num(raw, "rsiMin"), 0, 100, "rsiMin", adj),
                clamp(num(raw, "rsiMax"), 0, 100, "rsiMax", adj), "RSI", adj);

        Double[] atr = ordered(nonNegative(raw, "atrPctMin", adj), nonNegative(raw, "atrPctMax", adj), "ATR", adj);

        Double[] drop = ordered(
                clamp(num(raw, "dropFromHighMin"), 0, 100, "dropFromHighMin", adj),
                clamp(num(raw, "dropFromHighMax"), 0, 100, "dropFromHighMax", adj), "고점 대비 하락률", adj);

        Double surge = nonNegative(raw, "volumeSurgeRatio", adj);

        ScreenerCondition condition = new ScreenerCondition(
                unit,
                price[0], price[1],
                minTradeAmount,
                change[0], change[1],
                trueOrNull(raw.get("excludeWarning")),
                trueOrNull(raw.get("maAligned")),
                trueOrNull(raw.get("priceAboveMa20")),
                within(raw, "goldenCrossWithin", adj),
                rsi[0], rsi[1],
                within(raw, "macdCrossWithin", adj),
                within(raw, "bbLowerTouchWithin", adj),
                surge,
                atr[0], atr[1],
                drop[0], drop[1],
                sortBy,
                ScreenerCondition.MAX_LIMIT);

        String explanation = raw.get("explanation") instanceof String s ? s : "";
        List<String> unsupported = new ArrayList<>();
        if (raw.get("unsupported") instanceof List<?> list) {
            for (Object o : list) {
                if (o != null && !o.toString().isBlank()) {
                    unsupported.add(o.toString());
                }
            }
        }
        return new Result(condition, explanation, unsupported, adj);
    }

    // ---------- 변환 도우미 ----------

    private static Double num(Map<String, Object> raw, String key) {
        Object v = raw.get(key);
        if (v instanceof Number n) {
            double d = n.doubleValue();
            return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
        }
        return null;
    }

    private static Double nonNegative(Map<String, Object> raw, String key, List<String> adj) {
        Double v = num(raw, key);
        if (v != null && v < 0) {
            adj.add(key + " 가 음수(" + v + ")라 무시했어요");
            return null;
        }
        return v;
    }

    private static Double clamp(Double v, double min, double max, String key, List<String> adj) {
        if (v == null) {
            return null;
        }
        if (v < min || v > max) {
            double c = Math.max(min, Math.min(max, v));
            adj.add(key + " 값 " + v + " 을(를) " + c + " 로 조정했어요");
            return c;
        }
        return v;
    }

    /** 최소 > 최대로 들어오면 서로 바꾼다 */
    private static Double[] ordered(Double min, Double max, String label, List<String> adj) {
        if (min != null && max != null && min > max) {
            adj.add(label + " 최소·최대가 뒤바뀌어 있어 바꿨어요");
            return new Double[]{max, min};
        }
        return new Double[]{min, max};
    }

    private static Integer within(Map<String, Object> raw, String key, List<String> adj) {
        Double v = num(raw, key);
        if (v == null) {
            return null;
        }
        int n = (int) Math.round(v);
        if (n < 1 || n > MAX_WITHIN) {
            int c = Math.max(1, Math.min(MAX_WITHIN, n));
            adj.add(key + " 값 " + n + " 을(를) " + c + " 로 조정했어요");
            return c;
        }
        return n;
    }

    private static Boolean trueOrNull(Object v) {
        return Boolean.TRUE.equals(v) ? Boolean.TRUE : null;
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, Object v, String key, List<String> adj) {
        if (!(v instanceof String s) || s.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            adj.add(key + " 값 '" + s + "' 은(는) 알 수 없어 무시했어요");
            return null;
        }
    }
}
