package com.lhk.upbitagent.domain;

/**
 * 캔들 한 개. time 은 캔들 시작 시각(UTC epoch ms).
 * volume 은 거래량(코인 개수), tradeAmount 는 거래대금(원).
 */
public record Candle(
        long time,
        double open,
        double high,
        double low,
        double close,
        double volume,
        double tradeAmount
) {
}
