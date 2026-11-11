package com.lhk.upbitagent.domain;

/**
 * 캔들 한 개. time 은 캔들 시작 시각(UTC epoch ms).
 */
public record Candle(
        long time,
        double open,
        double high,
        double low,
        double close,
        double volume
) {
}
