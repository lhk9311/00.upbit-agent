package com.lhk.upbitagent.upbit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.lhk.upbitagent.domain.Candle;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 업비트 시세 API 응답 DTO 모음 (필요한 필드만 매핑).
 */
public final class UpbitDtos {

    private UpbitDtos() {
    }

    /** GET /v1/market/all?isDetails=true */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpbitMarket(
            @JsonProperty("market") String market,
            @JsonProperty("korean_name") String koreanName,
            @JsonProperty("english_name") String englishName,
            @JsonProperty("market_warning") String marketWarning,
            @JsonProperty("market_event") JsonNode marketEvent
    ) {
        /** 유의 종목 여부. 구 필드(market_warning)와 신 필드(market_event.warning) 모두 확인 */
        public boolean warning() {
            if ("CAUTION".equals(marketWarning)) {
                return true;
            }
            return marketEvent != null && marketEvent.path("warning").asBoolean(false);
        }
    }

    /** GET /v1/ticker */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpbitTicker(
            @JsonProperty("market") String market,
            @JsonProperty("trade_price") double tradePrice,
            @JsonProperty("signed_change_rate") double signedChangeRate,
            @JsonProperty("acc_trade_price_24h") double accTradePrice24h
    ) {
    }

    /** GET /v1/candles/{unit} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpbitCandle(
            @JsonProperty("candle_date_time_utc") String candleDateTimeUtc,
            @JsonProperty("opening_price") double openingPrice,
            @JsonProperty("high_price") double highPrice,
            @JsonProperty("low_price") double lowPrice,
            @JsonProperty("trade_price") double tradePrice,
            @JsonProperty("candle_acc_trade_volume") double candleAccTradeVolume
    ) {
        public Candle toCandle() {
            long time = LocalDateTime.parse(candleDateTimeUtc).toInstant(ZoneOffset.UTC).toEpochMilli();
            return new Candle(time, openingPrice, highPrice, lowPrice, tradePrice, candleAccTradeVolume);
        }
    }
}
