package com.lhk.upbitagent.indicator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndicatorsTest {

    private static final double EPS = 1e-6;

    @Test
    void sma_앞부분은_NaN_이후는_평균() {
        double[] r = Indicators.sma(new double[]{1, 2, 3, 4, 5}, 3);
        assertTrue(Double.isNaN(r[0]));
        assertTrue(Double.isNaN(r[1]));
        assertEquals(2.0, r[2], EPS);
        assertEquals(3.0, r[3], EPS);
        assertEquals(4.0, r[4], EPS);
    }

    @Test
    void ema_첫값은_SMA_이후는_지수평활() {
        double[] r = Indicators.ema(new double[]{1, 2, 3, 4}, 3);
        assertEquals(2.0, r[2], EPS);              // (1+2+3)/3
        assertEquals(4 * 0.5 + 2.0 * 0.5, r[3], EPS); // k = 2/(3+1) = 0.5
    }

    @Test
    void rsi_계속_오르면_100_계속_내리면_0() {
        double[] up = new double[30];
        double[] down = new double[30];
        for (int i = 0; i < 30; i++) {
            up[i] = 100 + i;
            down[i] = 100 - i;
        }
        assertEquals(100.0, Indicators.last(Indicators.rsi(up, 14)), EPS);
        assertEquals(0.0, Indicators.last(Indicators.rsi(down, 14)), EPS);
    }

    @Test
    void rsi_오르내림이_같으면_50() {
        double[] zigzag = new double[40];
        for (int i = 0; i < 40; i++) {
            zigzag[i] = (i % 2 == 0) ? 100 : 101;
        }
        double rsi = Indicators.last(Indicators.rsi(zigzag, 14));
        assertTrue(rsi > 45 && rsi < 55, "rsi=" + rsi);
    }

    @Test
    void 볼린저_변동없으면_밴드폭_0() {
        double[] flat = new double[25];
        java.util.Arrays.fill(flat, 10);
        Indicators.Bollinger bb = Indicators.bollinger(flat, 20, 2);
        assertEquals(10.0, Indicators.last(bb.upper()), EPS);
        assertEquals(10.0, Indicators.last(bb.lower()), EPS);
    }

    @Test
    void atr_고가저가_차이가_일정하면_그_값() {
        int n = 30;
        double[] h = new double[n], l = new double[n], c = new double[n];
        for (int i = 0; i < n; i++) {
            h[i] = 102;
            l[i] = 98;
            c[i] = 100;
        }
        assertEquals(4.0, Indicators.last(Indicators.atr(h, l, c, 14)), EPS);
    }

    @Test
    void 상향돌파_감지() {
        double[] fast = {1, 1, 1, 3};
        double[] slow = {2, 2, 2, 2};
        assertTrue(Indicators.crossedUpWithin(fast, slow, 1));
        assertTrue(Indicators.crossedUpWithin(fast, slow, 3));

        double[] fastEarly = {1, 3, 3, 3};
        assertFalse(Indicators.crossedUpWithin(fastEarly, slow, 2)); // 돌파가 3봉 전
        assertTrue(Indicators.crossedUpWithin(fastEarly, slow, 3));
    }
}
