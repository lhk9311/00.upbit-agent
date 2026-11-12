package com.lhk.upbitagent.settings;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppSettingsTest {

    private static AppSettings with(double fee, double slip, int cooldown, AppSettings.StrategyDefaults d) {
        return new AppSettings(false, fee, slip, cooldown, d);
    }

    @Test
    void 기본값은_통과() {
        assertTrue(with(0.0005, 0.001, 60, AppSettings.StrategyDefaults.standard()).validate().isEmpty());
    }

    @Test
    void 범위를_벗어나면_항목별로_알려줌() {
        AppSettings.StrategyDefaults bad = new AppSettings.StrategyDefaults(1_000.0, 0, 150.0, -1.0, null, 0, -5.0);
        List<String> errors = with(0.05, -0.1, -1, bad).validate();
        assertEquals(9, errors.size(), String.join(" / ", errors));
    }

    @Test
    void 선택_규칙은_null_허용() {
        AppSettings.StrategyDefaults d = new AppSettings.StrategyDefaults(5_000.0, 1, null, null, null, null, null);
        assertTrue(with(0, 0, 0, d).validate().isEmpty());
    }

    @Test
    void 분을_밀리초로() {
        assertEquals(3_600_000L, with(0, 0, 60, AppSettings.StrategyDefaults.standard()).reentryCooldownMs());
    }
}
