package com.lhk.upbitagent.indicator;

import java.util.Arrays;

/**
 * 기술적 지표 계산. 입력 배열은 오래된 값 → 최신 값 순서.
 * 계산할 데이터가 부족한 앞부분은 NaN 으로 채운다.
 * Spring 에 의존하지 않는 순수 함수라 단위 테스트가 쉽다.
 */
public final class Indicators {

    private Indicators() {
    }

    /** 단순 이동평균 */
    public static double[] sma(double[] values, int period) {
        double[] out = nanArray(values.length);
        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i];
            if (i >= period) {
                sum -= values[i - period];
            }
            if (i >= period - 1) {
                out[i] = sum / period;
            }
        }
        return out;
    }

    /** 지수 이동평균. 첫 값은 앞 period 개의 SMA 로 시작한다. NaN 구간은 건너뛴다. */
    public static double[] ema(double[] values, int period) {
        double[] out = nanArray(values.length);
        int start = firstValid(values);
        if (start < 0 || values.length - start < period) {
            return out;
        }
        double k = 2.0 / (period + 1);
        double seed = 0;
        for (int i = start; i < start + period; i++) {
            seed += values[i];
        }
        int seedIdx = start + period - 1;
        out[seedIdx] = seed / period;
        for (int i = seedIdx + 1; i < values.length; i++) {
            out[i] = values[i] * k + out[i - 1] * (1 - k);
        }
        return out;
    }

    /** RSI (Wilder 평활) */
    public static double[] rsi(double[] closes, int period) {
        double[] out = nanArray(closes.length);
        if (closes.length <= period) {
            return out;
        }
        double gain = 0;
        double loss = 0;
        for (int i = 1; i <= period; i++) {
            double diff = closes[i] - closes[i - 1];
            if (diff > 0) gain += diff;
            else loss -= diff;
        }
        gain /= period;
        loss /= period;
        out[period] = toRsi(gain, loss);
        for (int i = period + 1; i < closes.length; i++) {
            double diff = closes[i] - closes[i - 1];
            gain = (gain * (period - 1) + Math.max(diff, 0)) / period;
            loss = (loss * (period - 1) + Math.max(-diff, 0)) / period;
            out[i] = toRsi(gain, loss);
        }
        return out;
    }

    /** MACD(fast, slow, signal) */
    public static Macd macd(double[] closes, int fast, int slow, int signal) {
        double[] emaFast = ema(closes, fast);
        double[] emaSlow = ema(closes, slow);
        double[] line = nanArray(closes.length);
        for (int i = 0; i < closes.length; i++) {
            line[i] = emaFast[i] - emaSlow[i];
        }
        double[] signalLine = ema(line, signal);
        return new Macd(line, signalLine);
    }

    /** 볼린저 밴드(period, k 표준편차) */
    public static Bollinger bollinger(double[] closes, int period, double k) {
        double[] mid = sma(closes, period);
        double[] upper = nanArray(closes.length);
        double[] lower = nanArray(closes.length);
        for (int i = period - 1; i < closes.length; i++) {
            double variance = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double d = closes[j] - mid[i];
                variance += d * d;
            }
            double sd = Math.sqrt(variance / period);
            upper[i] = mid[i] + k * sd;
            lower[i] = mid[i] - k * sd;
        }
        return new Bollinger(upper, mid, lower);
    }

    /** ATR (Wilder 평활) */
    public static double[] atr(double[] highs, double[] lows, double[] closes, int period) {
        int n = closes.length;
        double[] out = nanArray(n);
        if (n <= period) {
            return out;
        }
        double[] tr = new double[n];
        tr[0] = highs[0] - lows[0];
        for (int i = 1; i < n; i++) {
            tr[i] = Math.max(highs[i] - lows[i],
                    Math.max(Math.abs(highs[i] - closes[i - 1]), Math.abs(lows[i] - closes[i - 1])));
        }
        double sum = 0;
        for (int i = 1; i <= period; i++) {
            sum += tr[i];
        }
        out[period] = sum / period;
        for (int i = period + 1; i < n; i++) {
            out[i] = (out[i - 1] * (period - 1) + tr[i]) / period;
        }
        return out;
    }

    /**
     * 최근 within 개 봉 안에서 fast 가 slow 를 아래에서 위로 돌파했는지.
     */
    public static boolean crossedUpWithin(double[] fast, double[] slow, int within) {
        int n = fast.length;
        int from = Math.max(1, n - within);
        for (int i = from; i < n; i++) {
            if (valid(fast[i - 1], slow[i - 1], fast[i], slow[i])
                    && fast[i - 1] <= slow[i - 1]
                    && fast[i] > slow[i]) {
                return true;
            }
        }
        return false;
    }

    public static double last(double[] values) {
        return values.length == 0 ? Double.NaN : values[values.length - 1];
    }

    public record Macd(double[] line, double[] signal) {
    }

    public record Bollinger(double[] upper, double[] middle, double[] lower) {
    }

    private static double toRsi(double avgGain, double avgLoss) {
        if (avgLoss == 0) {
            return avgGain == 0 ? 50 : 100;
        }
        double rs = avgGain / avgLoss;
        return 100 - 100 / (1 + rs);
    }

    private static boolean valid(double... values) {
        for (double v : values) {
            if (Double.isNaN(v)) return false;
        }
        return true;
    }

    private static int firstValid(double[] values) {
        for (int i = 0; i < values.length; i++) {
            if (!Double.isNaN(values[i])) return i;
        }
        return -1;
    }

    private static double[] nanArray(int n) {
        double[] a = new double[n];
        Arrays.fill(a, Double.NaN);
        return a;
    }
}
