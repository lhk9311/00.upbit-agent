package com.lhk.upbitagent.screener;

import com.lhk.upbitagent.domain.Candle;

import java.util.List;

/**
 * 조건 검색에 넣는 종목 한 개의 스냅샷.
 * changeRate 는 % 단위, candles 는 오래된 → 최신 순서(마지막은 진행 중인 봉).
 */
public record MarketSnapshot(
        String market,
        String koreanName,
        boolean warning,
        double tradePrice,
        double changeRate,
        double accTradePrice24h,
        List<Candle> candles
) {
}
