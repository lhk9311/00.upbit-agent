package com.lhk.upbitagent.store;

import com.lhk.upbitagent.agent.Trade;
import com.lhk.upbitagent.agent.TradeMode;
import com.lhk.upbitagent.agent.TradeStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.lhk.upbitagent.store.StrategyRepository.nullableDouble;
import static com.lhk.upbitagent.store.StrategyRepository.nullableLong;

@Repository
public class TradeRepository {

    private static final String SELECT = """
            SELECT t.*, s.name AS strategy_name
              FROM trade t
              LEFT JOIN strategy s ON s.id = t.strategy_id
            """;

    /** 화면 정렬 키 → 컬럼 (SQL 인젝션 방지용 화이트리스트) */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "buyAt", "t.buy_at",
            "sellAt", "t.sell_at",
            "pnlRate", "t.pnl_rate",
            "pnl", "t.pnl",
            "market", "t.korean_name",
            "buyPrice", "t.buy_price",
            "sellPrice", "t.sell_price");

    private final NamedParameterJdbcTemplate jdbc;

    public TradeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(Trade t) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO trade (strategy_id, market, korean_name, status, mode, buy_price, quantity,
                                   buy_amount, buy_fee, buy_at, highest_price)
                VALUES (:strategyId, :market, :koreanName, :status, :mode, :buyPrice, :quantity,
                        :buyAmount, :buyFee, :buyAt, :highestPrice)
                """, new MapSqlParameterSource()
                .addValue("strategyId", t.strategyId())
                .addValue("market", t.market())
                .addValue("koreanName", t.koreanName())
                .addValue("status", t.status().name())
                .addValue("mode", t.mode().name())
                .addValue("buyPrice", t.buyPrice())
                .addValue("quantity", t.quantity())
                .addValue("buyAmount", t.buyAmount())
                .addValue("buyFee", t.buyFee())
                .addValue("buyAt", t.buyAt())
                .addValue("highestPrice", t.highestPrice()), key, new String[]{"id"});
        return key.getKey().longValue();
    }

    /** 매도 체결 반영. 이미 닫힌 거래는 건드리지 않는다(중복 매도 방지) */
    public boolean close(Trade t) {
        int updated = jdbc.update("""
                UPDATE trade
                   SET status = 'CLOSED', sell_price = :sellPrice, sell_amount = :sellAmount, sell_fee = :sellFee,
                       sell_at = :sellAt, sell_reason = :sellReason, pnl = :pnl, pnl_rate = :pnlRate
                 WHERE id = :id AND status = 'OPEN'
                """, new MapSqlParameterSource()
                .addValue("id", t.id())
                .addValue("sellPrice", t.sellPrice())
                .addValue("sellAmount", t.sellAmount())
                .addValue("sellFee", t.sellFee())
                .addValue("sellAt", t.sellAt())
                .addValue("sellReason", t.sellReason())
                .addValue("pnl", t.pnl())
                .addValue("pnlRate", t.pnlRate()));
        return updated == 1;
    }

    public void updateHighest(long id, double highest) {
        jdbc.update("UPDATE trade SET highest_price = :h WHERE id = :id AND status = 'OPEN'",
                new MapSqlParameterSource("id", id).addValue("h", highest));
    }

    public Optional<Trade> findById(long id) {
        return jdbc.query(SELECT + " WHERE t.id = :id", new MapSqlParameterSource("id", id), mapper())
                .stream().findFirst();
    }

    public List<Trade> findOpen() {
        return jdbc.query(SELECT + " WHERE t.status = 'OPEN' ORDER BY t.buy_at DESC", mapper());
    }

    public List<Trade> findOpenByStrategy(long strategyId) {
        return jdbc.query(SELECT + " WHERE t.status = 'OPEN' AND t.strategy_id = :sid ORDER BY t.buy_at",
                new MapSqlParameterSource("sid", strategyId), mapper());
    }

    /** 투자 히스토리 조회 */
    public List<Trade> search(TradeSearch q) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        MapSqlParameterSource p = new MapSqlParameterSource();

        if (q.status() != null) {
            sql.append(" AND t.status = :status");
            p.addValue("status", q.status().name());
        }
        if (q.strategyId() != null) {
            sql.append(" AND t.strategy_id = :sid");
            p.addValue("sid", q.strategyId());
        }
        if (q.from() != null) {
            sql.append(" AND t.buy_at >= :from");
            p.addValue("from", q.from());
        }
        if (q.to() != null) {
            sql.append(" AND t.buy_at < :to");
            p.addValue("to", q.to());
        }

        String column = SORT_COLUMNS.getOrDefault(q.sort(), "t.buy_at");
        String dir = "asc".equalsIgnoreCase(q.dir()) ? "ASC" : "DESC";
        // 진행 중 거래는 매도 관련 값이 NULL 이라 정렬 시 항상 뒤로 보낸다
        sql.append(" ORDER BY CASE WHEN ").append(column).append(" IS NULL THEN 1 ELSE 0 END, ")
                .append(column).append(' ').append(dir).append(", t.id DESC");
        sql.append(" LIMIT ").append(Math.max(1, Math.min(q.limit(), 1000)));

        return jdbc.query(sql.toString(), p, mapper());
    }

    /** since 이후 매도된 거래의 실현손익 합계 */
    public double realizedPnlSince(Long strategyId, long since) {
        String sql = "SELECT COALESCE(SUM(pnl), 0) FROM trade WHERE status = 'CLOSED' AND sell_at >= :since"
                + (strategyId == null ? "" : " AND strategy_id = :sid");
        Double v = jdbc.queryForObject(sql,
                new MapSqlParameterSource("since", since).addValue("sid", strategyId), Double.class);
        return v == null ? 0 : v;
    }

    /** since 이후 매도한 종목 (재진입 대기용) */
    public Set<String> soldMarketsSince(long strategyId, long since) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT DISTINCT market FROM trade WHERE strategy_id = :sid AND status = 'CLOSED' AND sell_at >= :since",
                new MapSqlParameterSource("sid", strategyId).addValue("since", since), String.class));
    }

    /** 완료 거래 통계: 건수, 이긴 건수, 실현손익 합계, 총 매수비용 */
    public Map<String, Object> closedStats() {
        return jdbc.queryForMap("""
                SELECT COUNT(*)                                       AS closed_count,
                       COALESCE(SUM(CASE WHEN pnl > 0 THEN 1 ELSE 0 END), 0) AS win_count,
                       COALESCE(SUM(pnl), 0)                          AS realized_pnl,
                       COALESCE(SUM(buy_amount + buy_fee), 0)         AS total_cost
                  FROM trade
                 WHERE status = 'CLOSED'
                """, new MapSqlParameterSource());
    }

    public record TradeSearch(TradeStatus status, Long strategyId, Long from, Long to,
                              String sort, String dir, int limit) {
    }

    private RowMapper<Trade> mapper() {
        return (rs, i) -> new Trade(
                rs.getLong("id"),
                rs.getLong("strategy_id"),
                rs.getString("strategy_name"),
                rs.getString("market"),
                rs.getString("korean_name"),
                TradeStatus.valueOf(rs.getString("status")),
                TradeMode.valueOf(rs.getString("mode")),
                rs.getDouble("buy_price"),
                rs.getDouble("quantity"),
                rs.getDouble("buy_amount"),
                rs.getDouble("buy_fee"),
                rs.getLong("buy_at"),
                rs.getDouble("highest_price"),
                nullableDouble(rs, "sell_price"),
                nullableDouble(rs, "sell_amount"),
                nullableDouble(rs, "sell_fee"),
                nullableLong(rs, "sell_at"),
                rs.getString("sell_reason"),
                nullableDouble(rs, "pnl"),
                nullableDouble(rs, "pnl_rate"));
    }
}
