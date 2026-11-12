package com.lhk.upbitagent.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Optional;

/**
 * JWT 발급·검증.
 *
 * JWT = "헤더.내용.서명" 세 덩어리를 점(.)으로 이은 문자열.
 *  - 내용(payload): 누구인지(sub), 언제 발급(iat), 언제 만료(exp)
 *  - 서명: 서버만 아는 비밀 키로 만든 값. 내용을 한 글자라도 바꾸면 서명이 안 맞아서 거부된다.
 * 내용은 암호화가 아니라 Base64 인코딩이라 누구나 읽을 수 있다 → 비밀번호 같은 건 넣지 않는다.
 *
 * 세션 방식과의 차이:
 *  - 세션: 서버가 "누가 로그인했는지" 를 메모리에 기억하고, 브라우저는 세션 ID 쿠키만 들고 다닌다.
 *  - JWT : 서버는 아무것도 기억하지 않는다. 매 요청마다 토큰의 서명만 확인하면 누군지 안다.
 */
@Component
public class JwtProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);

    public record Issued(String token, long expiresAt) {
    }

    public record Parsed(String username, long issuedAt) {
    }

    private final SecretKey key;
    private final long expiryMs;

    public JwtProvider(@Value("${app.jwt.secret:}") String secret,
                       @Value("${app.jwt.expiry-hours:12}") long expiryHours) {
        byte[] bytes;
        if (secret == null || secret.isBlank()) {
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            log.info("JWT_SECRET 이 설정되지 않아 임시 키를 만들었어요. 서버를 재시작하면 다시 로그인해야 해요.");
        } else {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                // HS256 은 최소 256비트(32바이트) 키가 필요하다
                throw new IllegalStateException("JWT_SECRET 은 32자 이상이어야 해요 (지금 " + bytes.length + "자)");
            }
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expiryMs = expiryHours * 3_600_000L;
    }

    public Issued issue(String username) {
        long now = System.currentTimeMillis();
        Date expiresAt = new Date(now + expiryMs);
        String token = Jwts.builder()
                .subject(username)
                .issuedAt(new Date(now))
                .expiration(expiresAt)
                .signWith(key)            // HS256 서명
                .compact();
        return new Issued(token, expiresAt.getTime());
    }

    /** 서명이 맞고 만료되지 않았으면 내용, 아니면 empty */
    public Optional<Parsed> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)       // 서명 확인 + 만료 확인
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new Parsed(claims.getSubject(), claims.getIssuedAt().getTime()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();       // 위조·만료·형식 오류
        }
    }
}
