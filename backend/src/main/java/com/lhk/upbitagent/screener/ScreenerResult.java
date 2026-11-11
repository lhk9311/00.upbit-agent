package com.lhk.upbitagent.screener;

import java.util.List;

/**
 * 조건 검색 결과.
 * evaluatedCount: 검사한 종목 수, matchedCount: 조건을 모두 통과한 종목 수,
 * items: 그중 정렬 기준 상위 최대 10개.
 */
public record ScreenerResult(
        String unit,
        int evaluatedCount,
        int matchedCount,
        Long cacheUpdatedAt,
        List<Item> items
) {

    /** 지표 값이 계산 불가하면 null (JSON 에 NaN 이 들어가지 않도록) */
    public record Item(
            String market,
            String koreanName,
            double tradePrice,
            double changeRate,
            double accTradePrice24h,
            Double rsi,
            Double volumeRatio,
            Double atrPct,
            Double dropFromHigh,
            List<String> matched
    ) {
    }

    public ScreenerResult withCacheUpdatedAt(Long updatedAt) {
        return new ScreenerResult(unit, evaluatedCount, matchedCount, updatedAt, items);
    }
}
