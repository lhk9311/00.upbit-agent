package com.lhk.upbitagent.auth;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 로그인 계정 테이블(app_user) 접근.
 */
@Repository
public class AppUserRepository {

    /**
     * @param passwordChangedAt 비밀번호를 마지막으로 바꾼 시각(ms). 처음 만든 뒤 안 바꿨으면 null
     */
    public record AppUser(long id, String username, String passwordHash, long createdAt, Long passwordChangedAt) {

        /**
         * 이 토큰이 아직 유효한지: 비밀번호를 바꾸기 "전"에 발급된 토큰은 거부한다.
         * JWT 의 발급 시각(iat)은 초 단위로 저장되므로 초 단위로 비교한다.
         */
        public boolean acceptsTokenIssuedAt(long issuedAtMs) {
            return passwordChangedAt == null || issuedAtMs / 1000 >= passwordChangedAt / 1000;
        }
    }

    private static final RowMapper<AppUser> MAPPER = (rs, i) -> {
        long changed = rs.getLong("password_changed_at");
        return new AppUser(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getLong("created_at"),
                rs.wasNull() ? null : changed);
    };

    private final NamedParameterJdbcTemplate jdbc;

    public AppUserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppUser> findByUsername(String username) {
        return jdbc.query("SELECT * FROM app_user WHERE username = :u",
                new MapSqlParameterSource("u", username), MAPPER).stream().findFirst();
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM app_user", new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    public void insert(String username, String passwordHash, long now) {
        jdbc.update("INSERT INTO app_user (username, password_hash, created_at) VALUES (:u, :h, :now)",
                new MapSqlParameterSource("u", username).addValue("h", passwordHash).addValue("now", now));
    }

    public void updatePassword(String username, String passwordHash, long now) {
        jdbc.update("UPDATE app_user SET password_hash = :h, password_changed_at = :now WHERE username = :u",
                new MapSqlParameterSource("u", username).addValue("h", passwordHash).addValue("now", now));
    }
}
