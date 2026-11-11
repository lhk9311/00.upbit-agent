package com.lhk.upbitagent.screener;

import com.lhk.upbitagent.domain.CandleUnit;

/**
 * 조건 검색 요청. 값이 null 이면 그 조건은 사용하지 않는다(OFF).
 * 켜진 조건은 모두 AND 로 적용되므로, 조건을 세밀하게 줄수록 결과가 줄어든다.
 *
 * 등락률·ATR·하락률은 % 단위(예: 3.5 = 3.5%), 거래대금은 원 단위.
 */
public record ScreenerCondition(
        // 기본
        CandleUnit unit,                // 기준봉 (기본 1시간봉)
        Double minPrice,
        Double maxPrice,
        Double minTradeAmount,          // 24시간 누적 거래대금 최소(원)
        Double minChangeRate,           // 전일 대비 등락률 최소(%)
        Double maxChangeRate,           // 전일 대비 등락률 최대(%)
        Boolean excludeWarning,         // 유의 종목 제외

        // 추세
        Boolean maAligned,              // 이평선 정배열 MA5 > MA20 > MA60
        Boolean priceAboveMa20,         // 현재가 > MA20
        Integer goldenCrossWithin,      // 최근 N봉 이내 MA5 가 MA20 상향 돌파

        // 모멘텀
        Double rsiMin,                  // RSI(14) 최소
        Double rsiMax,                  // RSI(14) 최대
        Integer macdCrossWithin,        // 최근 N봉 이내 MACD 가 시그널 상향 돌파
        Integer bbLowerTouchWithin,     // 최근 N봉 이내 볼린저 하단 터치 + 현재는 하단 위

        // 거래량·변동성
        Double volumeSurgeRatio,        // 직전 완성봉 거래량 ≥ 이전 20봉 평균 × X
        Double atrPctMin,               // ATR(14) / 현재가 (%) 최소
        Double atrPctMax,               // ATR(14) / 현재가 (%) 최대
        Double dropFromHighMin,         // 최근 20봉 고점 대비 하락률(%) 최소
        Double dropFromHighMax,         // 최근 20봉 고점 대비 하락률(%) 최대

        // 결과
        SortBy sortBy,
        Integer limit                   // 최대 10
) {

    public static final int MAX_LIMIT = 10;

    public enum SortBy {
        VOLUME_SURGE,   // 거래량 급증 배수 높은 순
        TRADE_AMOUNT,   // 거래대금 높은 순
        CHANGE_RATE,    // 등락률 높은 순
        RSI_ASC         // RSI 낮은 순 (과매도 우선)
    }

    public CandleUnit unitOrDefault() {
        return unit == null ? CandleUnit.MIN60 : unit;
    }

    public SortBy sortByOrDefault() {
        return sortBy == null ? SortBy.VOLUME_SURGE : sortBy;
    }

    public int limitOrDefault() {
        if (limit == null) {
            return MAX_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    /** 캔들 지표가 필요한 조건이 하나라도 켜져 있는지 */
    public boolean needsIndicators() {
        return isTrue(maAligned) || isTrue(priceAboveMa20) || goldenCrossWithin != null
                || rsiMin != null || rsiMax != null || macdCrossWithin != null
                || bbLowerTouchWithin != null || volumeSurgeRatio != null
                || atrPctMin != null || atrPctMax != null
                || dropFromHighMin != null || dropFromHighMax != null;
    }

    static boolean isTrue(Boolean b) {
        return Boolean.TRUE.equals(b);
    }
}
