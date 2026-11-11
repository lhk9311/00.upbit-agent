package com.lhk.upbitagent.market;

import com.lhk.upbitagent.domain.Candle;
import com.lhk.upbitagent.domain.CandleUnit;
import com.lhk.upbitagent.upbit.UpbitClient;
import com.lhk.upbitagent.upbit.UpbitDtos.UpbitMarket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

/**
 * 업비트 시세 수집기.
 * - 현재가: 10초마다 전체 KRW 마켓을 한 번의 요청으로 갱신
 * - 캔들: 기준봉을 하나씩 돌아가며(1시간 → 15분 → 4시간 → 일) 전체 종목 수집
 *   종목 수(약 200) × 요청 간격 때문에 한 기준봉에 30초 정도 걸린다.
 */
@Component
public class MarketCollector {

    private static final Logger log = LoggerFactory.getLogger(MarketCollector.class);

    /** 수집 순서. 화면 기본값인 1시간봉을 가장 먼저 */
    private static final CandleUnit[] ORDER = {
            CandleUnit.MIN60, CandleUnit.MIN15, CandleUnit.MIN240, CandleUnit.DAY
    };

    private static final long MARKET_REFRESH_MS = 60 * 60 * 1000L;

    private final UpbitClient upbit;
    private final MarketDataStore store;

    @Value("${collector.request-gap-ms:130}")
    private long requestGapMs;

    @Value("${collector.candle-count:200}")
    private int candleCount;

    private int unitIndex = 0;
    private long marketsLoadedAt = 0;

    public MarketCollector(UpbitClient upbit, MarketDataStore store) {
        this.upbit = upbit;
        this.store = store;
    }

    @Scheduled(fixedDelay = 10_000, initialDelay = 0)
    public void refreshTickers() {
        try {
            if (store.markets().isEmpty() || System.currentTimeMillis() - marketsLoadedAt > MARKET_REFRESH_MS) {
                store.updateMarkets(upbit.krwMarkets());
                marketsLoadedAt = System.currentTimeMillis();
                log.info("마켓 목록 갱신: {}개", store.markets().size());
            }
            List<String> codes = store.markets().stream().map(UpbitMarket::market).toList();
            store.updateTickers(upbit.tickers(codes));
        } catch (Exception e) {
            log.warn("현재가 갱신 실패: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${collector.candle-delay-ms:5000}", initialDelay = 3_000)
    public void refreshNextUnit() {
        if (store.markets().isEmpty()) {
            return;
        }
        CandleUnit unit = ORDER[unitIndex];
        unitIndex = (unitIndex + 1) % ORDER.length;

        long started = System.currentTimeMillis();
        int ok = 0;
        int done = 0;
        List<UpbitMarket> markets = store.markets();
        int total = markets.size();
        log.info("{} 캔들 수집 시작 ({}개 종목, 약 {}초 소요)", unit.label(), total, total * (requestGapMs + 50) / 1000);
        for (UpbitMarket m : markets) {
            store.updateProgress(unit, done, total);
            if (done > 0 && done % 100 == 0) {
                log.info("{} 캔들 수집 중 {}/{}", unit.label(), done, total);
            }
            done++;
            try {
                List<Candle> candles = upbit.candles(unit, m.market(), candleCount);
                store.updateCandles(unit, m.market(), candles);
                ok++;
            } catch (HttpClientErrorException.TooManyRequests e) {
                log.debug("요청 제한(429), 잠시 대기: {}", m.market());
                sleep(1_000);
            } catch (Exception e) {
                log.warn("캔들 수집 실패 {} {}: {}", unit, m.market(), e.getMessage());
            }
            sleep(requestGapMs);
        }
        store.updateProgress(null, 0, 0);
        store.markCandlesUpdated(unit);
        log.info("{} 캔들 수집 완료: {}/{}개, {}초",
                unit.label(), ok, total, (System.currentTimeMillis() - started) / 1000);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
