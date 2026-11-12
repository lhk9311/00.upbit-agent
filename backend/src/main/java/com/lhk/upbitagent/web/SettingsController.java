package com.lhk.upbitagent.web;

import com.lhk.upbitagent.market.MarketDataStore;
import com.lhk.upbitagent.nl.ClaudeConditionParser;
import com.lhk.upbitagent.settings.AppSettings;
import com.lhk.upbitagent.settings.SettingsService;
import com.lhk.upbitagent.store.AgentLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SettingsController {

    private final SettingsService settings;
    private final MarketDataStore market;
    private final ClaudeConditionParser parser;
    private final AgentLogRepository logs;
    private final long startedAt = System.currentTimeMillis();

    public SettingsController(SettingsService settings, MarketDataStore market,
                              ClaudeConditionParser parser, AgentLogRepository logs) {
        this.settings = settings;
        this.market = market;
        this.parser = parser;
        this.logs = logs;
    }

    @GetMapping("/settings")
    public AppSettings get() {
        return settings.get();
    }

    @PutMapping("/settings")
    public AppSettings update(@RequestBody AppSettings next, Authentication authentication) {
        boolean wasPaused = settings.get().buyingPaused();
        AppSettings saved = settings.update(next);
        if (wasPaused != saved.buyingPaused()) {
            logs.add(null, "WARN", saved.buyingPaused()
                    ? "설정: 모든 전략 신규 매수 중단 (" + authentication.getName() + ")"
                    : "설정: 신규 매수 재개 (" + authentication.getName() + ")");
        } else {
            logs.add(null, "INFO", "설정 저장 (" + authentication.getName() + ")");
        }
        return saved;
    }

    /** 설정 탭의 시스템 상태 */
    @GetMapping("/system/status")
    public Map<String, Object> status(Authentication authentication) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("username", authentication.getName());
        s.put("serverStartedAt", startedAt);
        s.put("naturalSearchEnabled", parser.isConfigured());
        s.put("liveTradingSupported", false);
        s.put("collector", market.status());
        return s;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
