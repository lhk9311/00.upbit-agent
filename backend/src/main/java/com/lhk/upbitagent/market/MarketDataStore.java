package com.lhk.upbitagent.market;

import com.lhk.upbitagent.domain.Candle;
import com.lhk.upbitagent.domain.CandleUnit;
import com.lhk.upbitagent.screener.MarketSnapshot;
import com.lhk.upbitagent.upbit.UpbitDtos.UpbitMarket;
import com.lhk.upbitagent.upbit.UpbitDtos.UpbitTicker;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 수집기가 모은 시세를 메모리에 보관한다.
 * 조건 검색은 업비트를 직접 부르지 않고 여기 캐시만 읽는다 (요청 수 제한 회피).
 */
@Component
public class MarketDataStore {

    private volatile List<UpbitMarket> markets = List.of();
    private final Map<String, UpbitTicker> tickers = new ConcurrentHashMap<>();
    private final Map<CandleUnit, Map<String, List<Candle>>> candles = new EnumMap<>(CandleUnit.class);
    private final Map<CandleUnit, Long> candleUpdatedAt = new ConcurrentHashMap<>();
    private volatile Long tickerUpdatedAt;

    public MarketDataStore() {
        for (CandleUnit unit : CandleUnit.values()) {
            candles.put(unit, new ConcurrentHashMap<>());
        }
    }

    public List<UpbitMarket> markets() {
        return markets;
    }

    public void updateMarkets(List<UpbitMarket> markets) {
        this.markets = List.copyOf(markets);
    }

    public void updateTickers(List<UpbitTicker> list) {
        for (UpbitTicker t : list) {
            tickers.put(t.market(), t);
        }
        tickerUpdatedAt = System.currentTimeMillis();
    }

    public void updateCandles(CandleUnit unit, String market, List<Candle> list) {
        candles.get(unit).put(market, List.copyOf(list));
    }

    public void markCandlesUpdated(CandleUnit unit) {
        candleUpdatedAt.put(unit, System.currentTimeMillis());
    }

    /** 해당 기준봉으로 한 바퀴 이상 수집이 끝났는지 */
    public boolean isReady(CandleUnit unit) {
        return candleUpdatedAt.containsKey(unit) && !tickers.isEmpty();
    }

    public Long candleUpdatedAt(CandleUnit unit) {
        return candleUpdatedAt.get(unit);
    }

    /** 조건 검색용 스냅샷. 현재가가 있는 종목만 포함 */
    public List<MarketSnapshot> snapshots(CandleUnit unit) {
        Map<String, List<Candle>> unitCandles = candles.get(unit);
        List<MarketSnapshot> out = new ArrayList<>();
        for (UpbitMarket m : markets) {
            UpbitTicker t = tickers.get(m.market());
            if (t == null) {
                continue;
            }
            out.add(new MarketSnapshot(
                    m.market(),
                    m.koreanName(),
                    m.warning(),
                    t.tradePrice(),
                    t.signedChangeRate() * 100,
                    t.accTradePrice24h(),
                    unitCandles.getOrDefault(m.market(), List.of())));
        }
        return out;
    }

    /** 수집 현황 (화면 표시·디버깅용) */
    public Map<String, Object> status() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("marketCount", markets.size());
        s.put("tickerUpdatedAt", tickerUpdatedAt);
        Map<String, Object> units = new LinkedHashMap<>();
        for (CandleUnit unit : CandleUnit.values()) {
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("label", unit.label());
            u.put("collected", candles.get(unit).size());
            u.put("updatedAt", candleUpdatedAt.get(unit));
            u.put("ready", isReady(unit));
            units.put(unit.name(), u);
        }
        s.put("units", units);
        return s;
    }
}
