package com.lhk.upbitagent.backtest;

import com.lhk.upbitagent.agent.AgentEngine;
import com.lhk.upbitagent.agent.Strategy;
import com.lhk.upbitagent.agent.Trade;
import com.lhk.upbitagent.domain.Candle;
import com.lhk.upbitagent.domain.CandleUnit;
import com.lhk.upbitagent.screener.MarketSnapshot;
import com.lhk.upbitagent.screener.ScreenerEngine;
import com.lhk.upbitagent.screener.ScreenerResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 백테스트: 과거 캔들을 한 봉씩 넘기며 "그때 이 전략이 돌았다면" 을 재현한다.
 *
 * 규칙 (실제 에이전트와 최대한 같게, 애매하면 불리하게)
 * - 매수: 봉이 끝난 시점에 조건 검색 → 종가로 매수 (슬리피지·수수료 반영)
 * - 매도: 다음 봉부터 봉 안의 고가·저가로 판단
 *   · 한 봉에서 손절가와 익절가에 모두 닿으면 손절로 처리 (어느 쪽이 먼저인지 알 수 없으므로 보수적으로)
 *   · 시가가 이미 손절가 아래로 갭 하락했으면 시가에 매도
 *   · 트레일링은 직전 봉까지의 최고가 기준
 *   · 보유시간 초과는 그 봉의 종가에 매도
 * - 일일 손실 한도(KST 기준 하루), 재진입 대기, 최대 보유 수 모두 적용
 * - 기간이 끝날 때 남은 포지션은 마지막 종가로 평가해 "기간 종료"로 표시
 *
 * Spring 에 의존하지 않는 순수 Java.
 */
public class BacktestEngine {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long DAY_MS = 24 * 60 * 60 * 1000L;
    private static final String BENCHMARK = "KRW-BTC";

    private final AgentEngine agent;
    private final ScreenerEngine screener = new ScreenerEngine();
    private final long cooldownMs;

    public BacktestEngine(AgentEngine.Config config, long cooldownMs) {
        this.agent = new AgentEngine(config);
        this.cooldownMs = cooldownMs;
    }

    public record BtTrade(String market, String koreanName,
                          long buyAt, double buyPrice, long sellAt, double sellPrice,
                          String reason, double pnl, double pnlRate, boolean openAtEnd) {
    }

    /** 수익곡선 한 점: 그 시점까지 실현손익 + 보유 종목 평가손익 */
    public record EquityPoint(long time, double pnl) {
    }

    public record Result(
            String unit,
            Long from, Long to,           // 매매를 시뮬레이션한 구간(봉 종료 시각)
            int bars,
            int marketCount,
            int tradeCount,
            int winCount,
            Double winRate,               // %
            double totalPnl,              // 원 (수수료 차감)
            double capital,               // 1회 금액 × 최대 보유 수
            Double returnPct,             // totalPnl / capital
            double maxDrawdown,           // 원
            Double maxDrawdownPct,        // capital 대비 %
            Double avgHoldHours,
            Double benchmarkReturnPct,    // 같은 기간 비트코인 보유 수익률
            List<BtTrade> trades,
            List<EquityPoint> equity,
            List<String> notes
    ) {
    }

    public Result run(Strategy s, CandleUnit unit,
                      Map<String, List<Candle>> candlesByMarket,
                      Map<String, String> names,
                      Set<String> warningMarkets) {

        long barMs = unit.millis();
        int barsPerDay = (int) Math.max(1, DAY_MS / barMs);

        // 종목별 "시각 → 인덱스"
        Map<String, Map<Long, Integer>> indexOf = new HashMap<>();
        TreeSet<Long> allTimes = new TreeSet<>();
        for (Map.Entry<String, List<Candle>> e : candlesByMarket.entrySet()) {
            Map<Long, Integer> idx = new HashMap<>();
            List<Candle> list = e.getValue();
            for (int i = 0; i < list.size(); i++) {
                idx.put(list.get(i).time(), i);
                allTimes.add(list.get(i).time());
            }
            indexOf.put(e.getKey(), idx);
        }

        List<Long> times = new ArrayList<>(allTimes);
        int warmup = ScreenerEngine.MIN_CANDLES - 1; // 지표 계산에 필요한 앞부분은 매매하지 않음
        List<String> notes = defaultNotes();
        if (s.buyWindow() != null) {
            notes.add(0, s.buyWindow().minutes() * 60_000L < barMs
                    ? "매수 시간대(" + s.buyWindow().label() + ")가 기준봉(" + unit.label() + ")보다 짧아서, 시간대가 걸친 봉이 끝날 때 산 것으로 근사했어요. "
                      + "실제로는 시간대 안에서 더 일찍 사므로 결과가 다를 수 있어요."
                    : "매수 시간대 " + s.buyWindow().label() + "(한국 시간)에 걸친 봉에서만 매수했어요.");
        }

        if (times.size() <= warmup + 1) {
            notes.add(0, "캔들이 부족해요 (" + times.size() + "봉). 지표 계산에 " + ScreenerEngine.MIN_CANDLES
                    + "봉이 필요해서 매매할 구간이 없어요. 서버가 해당 기준봉을 다 모았는지 확인해주세요.");
            return new Result(unit.name(), null, null, 0, candlesByMarket.size(), 0, 0, null, 0,
                    capital(s), null, 0, null, null, null, List.of(), List.of(), notes);
        }

        List<Trade> open = new ArrayList<>();
        List<BtTrade> closed = new ArrayList<>();
        List<EquityPoint> equity = new ArrayList<>();
        Map<String, Long> lastSoldAt = new HashMap<>();
        Map<LocalDate, Double> realizedByDay = new HashMap<>();
        double realized = 0;
        long seq = 1;

        for (int k = warmup; k < times.size(); k++) {
            long barStart = times.get(k);
            long barClose = barStart + barMs;

            // ---------- 1. 매도 (이번 봉의 고가·저가로) ----------
            List<Trade> stillOpen = new ArrayList<>();
            for (Trade t : open) {
                Integer i = indexOf.get(t.market()).get(barStart);
                if (i == null || barStart < t.buyAt()) {
                    stillOpen.add(t); // 이번 봉 데이터가 없거나 아직 매수한 봉이면 판단 보류
                    continue;
                }
                Candle c = candlesByMarket.get(t.market()).get(i);
                Exit exit = exitFor(s, t, c, barClose);
                if (exit == null) {
                    // 최고가 갱신 (다음 봉의 트레일링 기준)
                    stillOpen.add(withHighest(t, Math.max(t.highestPrice(), c.high())));
                    continue;
                }
                Trade done = agent.closeTrade(t, exit.price(), exit.reason(), exit.at());
                realized += done.pnl();
                realizedByDay.merge(kstDate(exit.at()), done.pnl(), Double::sum);
                lastSoldAt.put(t.market(), exit.at());
                closed.add(toBt(done, false));
            }
            open = stillOpen;

            // ---------- 2~3. 매수 ----------
            double today = realizedByDay.getOrDefault(kstDate(barClose), 0.0);
            boolean lossLimitHit = s.dailyLossLimitKrw() != null && today <= -s.dailyLossLimitKrw();
            int slots = s.maxPositions() - open.size();

            // 매수 시간대: 이 봉 안에 시간대가 조금이라도 걸치면 봉 마감 때 매수 판단 (근사)
            boolean inWindow = s.buyWindow() == null || s.buyWindow().overlaps(barStart, barClose);

            if (inWindow && !lossLimitHit && slots > 0 && s.orderKrw() >= AgentEngine.MIN_ORDER_KRW) {
                List<MarketSnapshot> snaps = snapshotsAt(barStart, barMs, candlesByMarket, indexOf, names,
                        warningMarkets, barsPerDay);
                List<ScreenerResult.Item> candidates = screener.evaluate(snaps, s.condition()).items();

                Set<String> held = new HashSet<>();
                for (Trade t : open) {
                    held.add(t.market());
                }
                for (ScreenerResult.Item item : candidates) {
                    if (slots <= 0) {
                        break;
                    }
                    Long sold = lastSoldAt.get(item.market());
                    if (held.contains(item.market()) || (sold != null && barClose - sold < cooldownMs)) {
                        continue;
                    }
                    Trade bought = agent.openTrade(s, new AgentEngine.BuyOrder(item.market(), item.koreanName(),
                            item.tradePrice()), barClose);
                    // buyAt 을 "다음 봉 시작"으로 두면 다음 봉부터 매도 판단 (같은 봉 고가·저가로 팔지 않게)
                    Trade withId = new Trade(seq++, bought.strategyId(), bought.strategyName(), bought.market(),
                            bought.koreanName(), bought.status(), bought.mode(), bought.buyPrice(), bought.quantity(),
                            bought.buyAmount(), bought.buyFee(), barClose, bought.highestPrice(),
                            null, null, null, null, null, null, null);
                    open.add(withId);
                    held.add(item.market());
                    slots--;
                }
            }

            // ---------- 수익곡선 ----------
            double unrealized = 0;
            for (Trade t : open) {
                Double close = closeAt(t.market(), barStart, candlesByMarket, indexOf);
                if (close != null) {
                    unrealized += agent.closeTrade(t, close, "", barClose).pnl();
                }
            }
            equity.add(new EquityPoint(barClose, realized + unrealized));
        }

        // ---------- 기간 종료: 남은 포지션은 마지막 종가로 평가 ----------
        long lastStart = times.get(times.size() - 1);
        long lastClose = lastStart + barMs;
        for (Trade t : open) {
            List<Candle> list = candlesByMarket.get(t.market());
            Candle last = list.get(list.size() - 1);
            Trade done = agent.closeTrade(t, last.close(), "기간 종료(평가)", lastClose);
            realized += done.pnl();
            closed.add(toBt(done, true));
        }

        return summarize(s, unit, times.get(warmup) + barMs, lastClose, times.size() - warmup,
                candlesByMarket, indexOf, closed, equity, realized, notes, times.get(warmup));
    }

    // ---------- 매도 판단 (봉 하나) ----------

    record Exit(double price, String reason, long at) {
    }

    Exit exitFor(Strategy s, Trade t, Candle c, long barClose) {
        double buy = t.buyPrice();

        if (s.stopLossPct() != null) {
            double stop = buy * (1 - s.stopLossPct() / 100);
            if (c.low() <= stop) {
                return new Exit(Math.min(c.open(), stop), "손절", barClose);
            }
        }
        if (s.takeProfitPct() != null) {
            double take = buy * (1 + s.takeProfitPct() / 100);
            if (c.high() >= take) {
                return new Exit(Math.max(c.open(), take), "익절", barClose);
            }
        }
        if (s.trailingStopPct() != null && t.highestPrice() > buy) {
            double trail = t.highestPrice() * (1 - s.trailingStopPct() / 100);
            if (c.low() <= trail) {
                return new Exit(Math.min(c.open(), trail), "트레일링", barClose);
            }
        }
        if (s.maxHoldHours() != null && barClose - t.buyAt() >= s.maxHoldHours() * 3_600_000L) {
            return new Exit(c.close(), "보유시간 초과", barClose);
        }
        return null;
    }

    // ---------- 과거 시점의 스냅샷 ----------

    /**
     * barStart 봉이 끝난 시점의 종목 스냅샷.
     * 24시간 거래대금은 최근 24시간 캔들 합으로 근사한다.
     * 전일 대비 등락률은 업비트처럼 "오늘 09:00(한국 시간, UTC 0시) 직전 종가" 대비로 계산한다.
     */
    private List<MarketSnapshot> snapshotsAt(long barStart, long barMs,
                                             Map<String, List<Candle>> candlesByMarket,
                                             Map<String, Map<Long, Integer>> indexOf,
                                             Map<String, String> names,
                                             Set<String> warningMarkets,
                                             int barsPerDay) {
        List<MarketSnapshot> out = new ArrayList<>();
        for (Map.Entry<String, List<Candle>> e : candlesByMarket.entrySet()) {
            Integer i = indexOf.get(e.getKey()).get(barStart);
            if (i == null) {
                continue; // 그 시각에 거래가 없던 종목 (신규 상장 전 등)
            }
            List<Candle> list = e.getValue();
            Candle c = list.get(i);

            double amount24h = 0;
            for (int j = Math.max(0, i - barsPerDay + 1); j <= i; j++) {
                amount24h += list.get(j).tradeAmount();
            }
            Integer prevIdx = indexOf.get(e.getKey()).get(referenceBarStart(barStart, barMs));
            double changeRate = prevIdx != null && list.get(prevIdx).close() > 0
                    ? (c.close() - list.get(prevIdx).close()) / list.get(prevIdx).close() * 100
                    : 0;

            out.add(new MarketSnapshot(e.getKey(), names.getOrDefault(e.getKey(), e.getKey()),
                    warningMarkets.contains(e.getKey()), c.close(), changeRate, amount24h,
                    list.subList(0, i + 1)));
        }
        return out;
    }

    /**
     * 등락률 기준이 되는 봉(시작 시각). 업비트는 매일 UTC 0시(한국 09:00) 가격을 "전일 종가"로 쓴다.
     * 봉이 끝나기 직전 시점 기준으로, 그날 UTC 0시에 끝난 봉 = (UTC 0시 - 봉 길이) 에 시작한 봉.
     *  - 15분봉 09:00~09:15 → 08:45 봉 종가 대비 (오늘 9시 이후 상승률)
     *  - 일봉 → 전날 일봉 종가 대비
     */
    static long referenceBarStart(long barStart, long barMs) {
        long decisionAt = barStart + barMs - 1;
        long utcDayStart = Math.floorDiv(decisionAt, DAY_MS) * DAY_MS;
        return utcDayStart - barMs;
    }

    // ---------- 결과 정리 ----------

    private Result summarize(Strategy s, CandleUnit unit, long from, long to, int bars,
                             Map<String, List<Candle>> candlesByMarket,
                             Map<String, Map<Long, Integer>> indexOf,
                             List<BtTrade> trades, List<EquityPoint> equity, double totalPnl,
                             List<String> notes, long firstBarStart) {
        int wins = 0;
        double holdMs = 0;
        for (BtTrade t : trades) {
            if (t.pnl() > 0) wins++;
            holdMs += t.sellAt() - t.buyAt();
        }

        // 최대 낙폭: 수익곡선의 고점 대비 가장 크게 떨어진 금액
        double peak = 0;
        double mdd = 0;
        for (EquityPoint p : equity) {
            peak = Math.max(peak, p.pnl());
            mdd = Math.max(mdd, peak - p.pnl());
        }

        double capital = capital(s);
        Double benchmark = benchmark(candlesByMarket, indexOf, firstBarStart);

        trades.sort((a, b) -> Long.compare(a.buyAt(), b.buyAt()));

        return new Result(unit.name(), from, to, bars, candlesByMarket.size(),
                trades.size(), wins,
                trades.isEmpty() ? null : wins * 100.0 / trades.size(),
                totalPnl, capital,
                capital > 0 ? totalPnl / capital * 100 : null,
                mdd,
                capital > 0 ? mdd / capital * 100 : null,
                trades.isEmpty() ? null : holdMs / trades.size() / 3_600_000.0,
                benchmark,
                trades, equity, notes);
    }

    /** 같은 구간 동안 비트코인을 그냥 들고 있었다면 (비교용) */
    private Double benchmark(Map<String, List<Candle>> candlesByMarket,
                             Map<String, Map<Long, Integer>> indexOf, long firstBarStart) {
        List<Candle> btc = candlesByMarket.get(BENCHMARK);
        if (btc == null || btc.isEmpty()) {
            return null;
        }
        Integer i = indexOf.get(BENCHMARK).get(firstBarStart);
        if (i == null) {
            return null;
        }
        double start = btc.get(i).close();
        double end = btc.get(btc.size() - 1).close();
        return start > 0 ? (end - start) / start * 100 : null;
    }

    private static double capital(Strategy s) {
        return s.orderKrw() * s.maxPositions();
    }

    private static Double closeAt(String market, long barStart,
                                  Map<String, List<Candle>> candlesByMarket,
                                  Map<String, Map<Long, Integer>> indexOf) {
        Integer i = indexOf.get(market).get(barStart);
        return i == null ? null : candlesByMarket.get(market).get(i).close();
    }

    private static Trade withHighest(Trade t, double highest) {
        return new Trade(t.id(), t.strategyId(), t.strategyName(), t.market(), t.koreanName(), t.status(), t.mode(),
                t.buyPrice(), t.quantity(), t.buyAmount(), t.buyFee(), t.buyAt(), highest,
                null, null, null, null, null, null, null);
    }

    private static BtTrade toBt(Trade t, boolean openAtEnd) {
        return new BtTrade(t.market(), t.koreanName(), t.buyAt(), t.buyPrice(), t.sellAt(), t.sellPrice(),
                t.sellReason(), t.pnl(), t.pnlRate(), openAtEnd);
    }

    private static LocalDate kstDate(long ms) {
        return Instant.ofEpochMilli(ms).atZone(KST).toLocalDate();
    }

    private static List<String> defaultNotes() {
        List<String> n = new ArrayList<>();
        n.add("서버가 지금 가진 캔들(종목당 최대 200봉)로 계산해요. 1시간봉이면 약 5일, 일봉이면 약 4개월 구간이에요.");
        n.add("지금 상장된 종목만 대상이에요. 그사이 상장폐지된 종목은 빠져 있어 결과가 실제보다 좋게 나올 수 있어요.");
        n.add("24시간 거래대금은 캔들로 근사했고, 전일 대비 등락률은 업비트처럼 매일 09:00 가격 기준이에요. 유의 종목 여부는 현재 기준이에요.");
        n.add("한 봉 안에서 손절가와 익절가에 모두 닿으면 손절로 처리했어요 (보수적).");
        n.add("과거 결과가 미래 수익을 보장하지 않아요.");
        return n;
    }
}
