package com.lhk.upbitagent.upbit;

import com.lhk.upbitagent.domain.Candle;
import com.lhk.upbitagent.domain.CandleUnit;
import com.lhk.upbitagent.upbit.UpbitDtos.UpbitCandle;
import com.lhk.upbitagent.upbit.UpbitDtos.UpbitMarket;
import com.lhk.upbitagent.upbit.UpbitDtos.UpbitTicker;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 업비트 시세(Quotation) API 호출. 인증이 필요 없는 공개 API만 사용한다.
 * 주문(Exchange) API 는 3단계 에이전트에서 별도 클라이언트로 추가 예정.
 */
@Component
public class UpbitClient {

    private final RestClient rest = RestClient.builder()
            .baseUrl("https://api.upbit.com/v1")
            .build();

    /** KRW 마켓 목록 (유의 종목 정보 포함) */
    public List<UpbitMarket> krwMarkets() {
        List<UpbitMarket> all = rest.get()
                .uri("/market/all?isDetails=true")
                .retrieve()
                .body(new ParameterizedTypeReference<List<UpbitMarket>>() {
                });
        if (all == null) {
            return List.of();
        }
        return all.stream()
                .filter(m -> m.market().startsWith("KRW-"))
                .toList();
    }

    /** 여러 마켓 현재가를 한 번에 조회 */
    public List<UpbitTicker> tickers(List<String> markets) {
        if (markets.isEmpty()) {
            return List.of();
        }
        List<UpbitTicker> body = rest.get()
                .uri(b -> b.path("/ticker").queryParam("markets", String.join(",", markets)).build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<UpbitTicker>>() {
                });
        return body == null ? List.of() : body;
    }

    /** 캔들 조회. 반환은 오래된 → 최신 순서 */
    public List<Candle> candles(CandleUnit unit, String market, int count) {
        List<UpbitCandle> body = rest.get()
                .uri(b -> b.path("/candles/" + unit.path())
                        .queryParam("market", market)
                        .queryParam("count", count)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<UpbitCandle>>() {
                });
        if (body == null) {
            return List.of();
        }
        List<Candle> candles = new ArrayList<>(body.size());
        for (UpbitCandle c : body) {
            candles.add(c.toCandle());
        }
        Collections.reverse(candles); // 업비트는 최신순으로 내려준다
        return candles;
    }
}
