package com.lhk.upbitagent.store;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.lhk.upbitagent.store.StrategyRepository.nullableLong;

@Repository
public class AgentLogRepository {

    public record AgentLog(long id, long createdAt, Long strategyId, String level, String message) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public AgentLogRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void add(Long strategyId, String level, String message) {
        String msg = message.length() > 500 ? message.substring(0, 500) : message;
        jdbc.update("""
                INSERT INTO agent_log (created_at, strategy_id, level, message)
                VALUES (:createdAt, :strategyId, :level, :message)
                """, new MapSqlParameterSource()
                .addValue("createdAt", System.currentTimeMillis())
                .addValue("strategyId", strategyId)
                .addValue("level", level)
                .addValue("message", msg));
    }

    public List<AgentLog> recent(int limit) {
        return jdbc.query("SELECT * FROM agent_log ORDER BY id DESC LIMIT " + Math.max(1, Math.min(limit, 500)),
                (rs, i) -> new AgentLog(
                        rs.getLong("id"),
                        rs.getLong("created_at"),
                        nullableLong(rs, "strategy_id"),
                        rs.getString("level"),
                        rs.getString("message")));
    }
}
