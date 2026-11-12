package com.lhk.upbitagent.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhk.upbitagent.agent.BuyWindow;
import com.lhk.upbitagent.agent.Strategy;
import com.lhk.upbitagent.agent.TradeMode;
import com.lhk.upbitagent.screener.ScreenerCondition;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class StrategyRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public StrategyRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<Strategy> findAll() {
        return jdbc.query("SELECT * FROM strategy WHERE deleted = FALSE ORDER BY id DESC", mapper());
    }

    public Optional<Strategy> findById(long id) {
        return jdbc.query("SELECT * FROM strategy WHERE id = :id AND deleted = FALSE",
                new MapSqlParameterSource("id", id), mapper()).stream().findFirst();
    }

    public Strategy insert(Strategy s) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO strategy (name, condition_json, natural_text, order_krw, max_positions, take_profit_pct, stop_loss_pct,
                                      trailing_stop_pct, max_hold_hours, daily_loss_limit_krw, mode, enabled, created_at,
                                      buy_from, buy_to)
                VALUES (:name, :conditionJson, :naturalText, :orderKrw, :maxPositions, :takeProfitPct, :stopLossPct,
                        :trailingStopPct, :maxHoldHours, :dailyLossLimitKrw, :mode, :enabled, :createdAt,
                        :buyFrom, :buyTo)
                """, params(s), key, new String[]{"id"});
        return findById(key.getKey().longValue()).orElseThrow();
    }

    public void update(Strategy s) {
        jdbc.update("""
                UPDATE strategy
                   SET name = :name, condition_json = :conditionJson, natural_text = :naturalText, order_krw = :orderKrw,
                       max_positions = :maxPositions, take_profit_pct = :takeProfitPct, stop_loss_pct = :stopLossPct,
                       trailing_stop_pct = :trailingStopPct, max_hold_hours = :maxHoldHours,
                       daily_loss_limit_krw = :dailyLossLimitKrw, mode = :mode, enabled = :enabled,
                       buy_from = :buyFrom, buy_to = :buyTo
                 WHERE id = :id
                """, params(s).addValue("id", s.id()));
    }

    public void setEnabled(long id, boolean enabled) {
        jdbc.update("UPDATE strategy SET enabled = :enabled WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("enabled", enabled));
    }

    /** 거래 기록이 전략을 참조하므로 실제로 지우지 않고 숨긴다 */
    public void softDelete(long id) {
        jdbc.update("UPDATE strategy SET deleted = TRUE, enabled = FALSE WHERE id = :id",
                new MapSqlParameterSource("id", id));
    }

    private MapSqlParameterSource params(Strategy s) {
        return new MapSqlParameterSource()
                .addValue("name", s.name())
                .addValue("conditionJson", toJson(s.condition()))
                .addValue("naturalText", s.naturalText())
                .addValue("orderKrw", s.orderKrw())
                .addValue("maxPositions", s.maxPositions())
                .addValue("takeProfitPct", s.takeProfitPct())
                .addValue("stopLossPct", s.stopLossPct())
                .addValue("trailingStopPct", s.trailingStopPct())
                .addValue("maxHoldHours", s.maxHoldHours())
                .addValue("dailyLossLimitKrw", s.dailyLossLimitKrw())
                .addValue("mode", s.mode().name())
                .addValue("enabled", s.enabled())
                .addValue("createdAt", s.createdAt())
                .addValue("buyFrom", s.buyWindow() == null ? null : s.buyWindow().from())
                .addValue("buyTo", s.buyWindow() == null ? null : s.buyWindow().to());
    }

    private RowMapper<Strategy> mapper() {
        return (rs, i) -> new Strategy(
                rs.getLong("id"),
                rs.getString("name"),
                fromJson(rs.getString("condition_json")),
                rs.getString("natural_text"),
                rs.getDouble("order_krw"),
                rs.getInt("max_positions"),
                nullableDouble(rs, "take_profit_pct"),
                nullableDouble(rs, "stop_loss_pct"),
                nullableDouble(rs, "trailing_stop_pct"),
                nullableInt(rs, "max_hold_hours"),
                nullableDouble(rs, "daily_loss_limit_krw"),
                TradeMode.valueOf(rs.getString("mode")),
                rs.getBoolean("enabled"),
                rs.getLong("created_at"),
                BuyWindow.of(rs.getString("buy_from"), rs.getString("buy_to")));
    }

    private String toJson(ScreenerCondition c) {
        try {
            return objectMapper.writeValueAsString(c);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("조건을 JSON 으로 바꾸지 못했어요", e);
        }
    }

    private ScreenerCondition fromJson(String json) {
        try {
            return objectMapper.readValue(json, ScreenerCondition.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 조건 JSON 을 읽지 못했어요: " + json, e);
        }
    }

    static Double nullableDouble(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    static Integer nullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    static Long nullableLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }
}
