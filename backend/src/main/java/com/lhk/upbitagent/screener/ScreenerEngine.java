package com.lhk.upbitagent.screener;

import com.lhk.upbitagent.domain.Candle;
import com.lhk.upbitagent.indicator.Indicators;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 조건 검색 엔진. Spring 에 의존하지 않아 단위 테스트로 바로 검증할 수 있다.
 *
 * 규칙
 * - 켜진 조건은 모두 AND. 하나라도 통과 못 하면 탈락.
 * - 지표 조건이 켜져 있는데 캔들이 부족한 종목(신규 상장 등)은 탈락.
 * - 지표는 진행 중인 마지막 봉까지 포함해 계산한다.
 *   단, 거래량 급증은 진행 중인 봉의 거래량이 작게 잡히므로 "직전 완성봉" 기준.
 */
public class ScreenerEngine {

    /** 지표 계산에 필요한 최소 캔들 수 (MA60 + 여유) */
    public static final int MIN_CANDLES = 70;

    private static final int VOLUME_LOOKBACK = 20;
    private static final int HIGH_LOOKBACK = 20;

    public ScreenerResult evaluate(List<MarketSnapshot> snapshots, ScreenerCondition cond) {
        List<Scored> passed = new ArrayList<>();

        for (MarketSnapshot s : snapshots) {
            Scored scored = evaluateOne(s, cond);
            if (scored != null) {
                passed.add(scored);
            }
        }

        passed.sort(comparator(cond.sortByOrDefault()));

        List<ScreenerResult.Item> items = passed.stream()
                .limit(cond.limitOrDefault())
                .map(Scored::item)
                .toList();

        return new ScreenerResult(cond.unitOrDefault().name(), snapshots.size(), passed.size(), null, items);
    }

    /** 조건을 모두 통과하면 결과를, 하나라도 실패하면 null 을 반환 */
    Scored evaluateOne(MarketSnapshot s, ScreenerCondition c) {
        List<String> matched = new ArrayList<>();

        // ---------- 기본 (현재가 기준) ----------
        if (ScreenerCondition.isTrue(c.excludeWarning())) {
            if (s.warning()) return null;
            matched.add("유의종목 제외");
        }
        if (c.minPrice() != null) {
            if (s.tradePrice() < c.minPrice()) return null;
            matched.add("가격 ≥ " + num(c.minPrice()));
        }
        if (c.maxPrice() != null) {
            if (s.tradePrice() > c.maxPrice()) return null;
            matched.add("가격 ≤ " + num(c.maxPrice()));
        }
        if (c.minTradeAmount() != null) {
            if (s.accTradePrice24h() < c.minTradeAmount()) return null;
            matched.add("거래대금 ≥ " + eok(c.minTradeAmount()));
        }
        if (c.minChangeRate() != null) {
            if (s.changeRate() < c.minChangeRate()) return null;
            matched.add("등락률 ≥ " + num(c.minChangeRate()) + "%");
        }
        if (c.maxChangeRate() != null) {
            if (s.changeRate() > c.maxChangeRate()) return null;
            matched.add("등락률 ≤ " + num(c.maxChangeRate()) + "%");
        }

        // ---------- 지표 계산 ----------
        List<Candle> candles = s.candles() == null ? List.of() : s.candles();
        boolean enough = candles.size() >= MIN_CANDLES;

        if (c.needsIndicators() && !enough) {
            return null;
        }

        Values v = enough ? Values.of(candles) : Values.empty();

        // ---------- 추세 ----------
        if (ScreenerCondition.isTrue(c.maAligned())) {
            if (!(v.ma5 > v.ma20 && v.ma20 > v.ma60)) return null;
            matched.add("정배열(5>20>60)");
        }
        if (ScreenerCondition.isTrue(c.priceAboveMa20())) {
            if (!(v.close > v.ma20)) return null;
            matched.add("현재가 > MA20");
        }
        if (c.goldenCrossWithin() != null) {
            if (!Indicators.crossedUpWithin(v.ma5Series, v.ma20Series, c.goldenCrossWithin())) return null;
            matched.add("골든크로스 " + c.goldenCrossWithin() + "봉 이내");
        }

        // ---------- 모멘텀 ----------
        if (c.rsiMin() != null) {
            if (!(v.rsi >= c.rsiMin())) return null;
            matched.add("RSI ≥ " + num(c.rsiMin()));
        }
        if (c.rsiMax() != null) {
            if (!(v.rsi <= c.rsiMax())) return null;
            matched.add("RSI ≤ " + num(c.rsiMax()));
        }
        if (c.macdCrossWithin() != null) {
            if (!Indicators.crossedUpWithin(v.macdLine, v.macdSignal, c.macdCrossWithin())) return null;
            matched.add("MACD 상향돌파 " + c.macdCrossWithin() + "봉 이내");
        }
        if (c.bbLowerTouchWithin() != null) {
            if (!v.bbLowerTouchedWithin(c.bbLowerTouchWithin())) return null;
            matched.add("볼린저 하단 반등 " + c.bbLowerTouchWithin() + "봉 이내");
        }

        // ---------- 거래량·변동성 ----------
        if (c.volumeSurgeRatio() != null) {
            if (!(v.volumeRatio >= c.volumeSurgeRatio())) return null;
            matched.add("거래량 " + num(c.volumeSurgeRatio()) + "배↑");
        }
        if (c.atrPctMin() != null) {
            if (!(v.atrPct >= c.atrPctMin())) return null;
            matched.add("ATR ≥ " + num(c.atrPctMin()) + "%");
        }
        if (c.atrPctMax() != null) {
            if (!(v.atrPct <= c.atrPctMax())) return null;
            matched.add("ATR ≤ " + num(c.atrPctMax()) + "%");
        }
        if (c.dropFromHighMin() != null) {
            if (!(v.dropFromHigh >= c.dropFromHighMin())) return null;
            matched.add("고점대비 -" + num(c.dropFromHighMin()) + "%↓");
        }
        if (c.dropFromHighMax() != null) {
            if (!(v.dropFromHigh <= c.dropFromHighMax())) return null;
            matched.add("고점대비 -" + num(c.dropFromHighMax()) + "% 이내");
        }

        ScreenerResult.Item item = new ScreenerResult.Item(
                s.market(), s.koreanName(), s.tradePrice(), s.changeRate(), s.accTradePrice24h(),
                nullIfNaN(v.rsi), nullIfNaN(v.volumeRatio), nullIfNaN(v.atrPct), nullIfNaN(v.dropFromHigh),
                matched);
        return new Scored(item, v);
    }

    private static Comparator<Scored> comparator(ScreenerCondition.SortBy sortBy) {
        return switch (sortBy) {
            case TRADE_AMOUNT -> Comparator.comparingDouble((Scored s) -> s.item().accTradePrice24h()).reversed();
            case CHANGE_RATE -> Comparator.comparingDouble((Scored s) -> s.item().changeRate()).reversed();
            case RSI_ASC -> Comparator.comparingDouble((Scored s) -> nanLast(s.values().rsi));
            case VOLUME_SURGE -> Comparator.comparingDouble((Scored s) -> nanFirst(s.values().volumeRatio)).reversed();
        };
    }

    record Scored(ScreenerResult.Item item, Values values) {
    }

    /** 종목 한 개의 지표 계산 결과 */
    static final class Values {
        double close = Double.NaN;
        double ma5 = Double.NaN, ma20 = Double.NaN, ma60 = Double.NaN;
        double rsi = Double.NaN, volumeRatio = Double.NaN, atrPct = Double.NaN, dropFromHigh = Double.NaN;
        double[] ma5Series = new double[0], ma20Series = new double[0];
        double[] macdLine = new double[0], macdSignal = new double[0];
        double[] lows = new double[0], bbLower = new double[0];

        static Values empty() {
            return new Values();
        }

        static Values of(List<Candle> candles) {
            int n = candles.size();
            double[] closes = new double[n], highs = new double[n], lows = new double[n], vols = new double[n];
            for (int i = 0; i < n; i++) {
                Candle c = candles.get(i);
                closes[i] = c.close();
                highs[i] = c.high();
                lows[i] = c.low();
                vols[i] = c.volume();
            }

            Values v = new Values();
            v.close = closes[n - 1];
            v.ma5Series = Indicators.sma(closes, 5);
            v.ma20Series = Indicators.sma(closes, 20);
            v.ma5 = Indicators.last(v.ma5Series);
            v.ma20 = Indicators.last(v.ma20Series);
            v.ma60 = Indicators.last(Indicators.sma(closes, 60));
            v.rsi = Indicators.last(Indicators.rsi(closes, 14));

            Indicators.Macd macd = Indicators.macd(closes, 12, 26, 9);
            v.macdLine = macd.line();
            v.macdSignal = macd.signal();

            v.lows = lows;
            v.bbLower = Indicators.bollinger(closes, 20, 2).lower();

            double atr = Indicators.last(Indicators.atr(highs, lows, closes, 14));
            v.atrPct = v.close > 0 ? atr / v.close * 100 : Double.NaN;

            // 거래량 급증: 직전 완성봉(n-2) vs 그 이전 20봉 평균
            int target = n - 2;
            double sum = 0;
            for (int i = target - VOLUME_LOOKBACK; i < target; i++) {
                sum += vols[i];
            }
            double avg = sum / VOLUME_LOOKBACK;
            v.volumeRatio = avg > 0 ? vols[target] / avg : Double.NaN;

            // 최근 20봉 고점 대비 하락률
            double high = Double.NEGATIVE_INFINITY;
            for (int i = n - HIGH_LOOKBACK; i < n; i++) {
                high = Math.max(high, highs[i]);
            }
            v.dropFromHigh = high > 0 ? (high - v.close) / high * 100 : Double.NaN;
            return v;
        }

        /** 최근 within 봉 안에 저가가 볼린저 하단 이하로 내려갔고, 현재 종가는 하단 위인지 */
        boolean bbLowerTouchedWithin(int within) {
            int n = lows.length;
            if (n == 0 || Double.isNaN(bbLower[n - 1]) || !(close > bbLower[n - 1])) {
                return false;
            }
            for (int i = Math.max(0, n - within); i < n; i++) {
                if (!Double.isNaN(bbLower[i]) && lows[i] <= bbLower[i]) {
                    return true;
                }
            }
            return false;
        }
    }

    private static Double nullIfNaN(double d) {
        return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
    }

    private static double nanLast(double d) {
        return Double.isNaN(d) ? Double.MAX_VALUE : d;
    }

    private static double nanFirst(double d) {
        return Double.isNaN(d) ? -Double.MAX_VALUE : d;
    }

    private static String num(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.2f", d);
    }

    private static String eok(double won) {
        return num(won / 100_000_000) + "억";
    }
}
