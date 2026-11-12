package com.lhk.upbitagent.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 설정값 읽기·저장.
 * - 처음엔 application.yml 의 agent.* 값으로 시작하고, 설정 탭에서 저장하면 DB 값이 우선한다.
 * - 에이전트가 1분마다 읽으므로 메모리에 들고 있다가(current) 저장할 때만 바꾼다.
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);
    private static final String KEY = "app";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AppSettings defaults;

    /** volatile: 저장(웹 요청 스레드)과 읽기(스케줄러 스레드)가 서로 다른 스레드라 최신 값을 보게 함 */
    private volatile AppSettings current;

    public SettingsService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper,
                           @Value("${agent.fee-rate:0.0005}") double feeRate,
                           @Value("${agent.paper-slippage:0.001}") double slippage,
                           @Value("${agent.reentry-cooldown-minutes:60}") int cooldownMinutes) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.defaults = new AppSettings(false, feeRate, slippage, cooldownMinutes,
                AppSettings.StrategyDefaults.standard());
    }

    public AppSettings get() {
        AppSettings s = current;
        if (s == null) {
            s = load();
            current = s;
        }
        return s;
    }

    public AppSettings update(AppSettings next) {
        if (next == null) {
            throw new IllegalArgumentException("설정 값이 비어 있어요");
        }
        List<String> errors = next.validate();
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" / ", errors));
        }
        String json = toJson(next);
        MapSqlParameterSource p = new MapSqlParameterSource("k", KEY).addValue("v", json);
        // 있으면 수정, 없으면 추가 (H2·MariaDB 둘 다에서 동작하는 방식)
        int updated = jdbc.update("UPDATE app_setting SET setting_value = :v WHERE setting_key = :k", p);
        if (updated == 0) {
            jdbc.update("INSERT INTO app_setting (setting_key, setting_value) VALUES (:k, :v)", p);
        }
        current = next;
        return next;
    }

    private AppSettings load() {
        List<String> rows = jdbc.queryForList("SELECT setting_value FROM app_setting WHERE setting_key = :k",
                new MapSqlParameterSource("k", KEY), String.class);
        if (rows.isEmpty()) {
            return defaults;
        }
        try {
            AppSettings s = objectMapper.readValue(rows.get(0), AppSettings.class);
            return s.strategyDefaults() == null
                    ? new AppSettings(s.buyingPaused(), s.feeRate(), s.paperSlippage(), s.reentryCooldownMinutes(),
                    AppSettings.StrategyDefaults.standard())
                    : s;
        } catch (JsonProcessingException e) {
            log.warn("저장된 설정을 읽지 못해 기본값을 써요: {}", e.getMessage());
            return defaults;
        }
    }

    private String toJson(AppSettings s) {
        try {
            return objectMapper.writeValueAsString(s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("설정을 저장하지 못했어요", e);
        }
    }
}
