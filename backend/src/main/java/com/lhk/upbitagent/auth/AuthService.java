package com.lhk.upbitagent.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * 로그인·비밀번호 변경.
 */
@Service
public class AuthService {

    public static final int MIN_PASSWORD_LENGTH = 8;

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwt;
    private final LoginAttemptLimiter limiter = LoginAttemptLimiter.defaults();

    public AuthService(AuthenticationManager authenticationManager, AppUserRepository users,
                       PasswordEncoder passwordEncoder, JwtProvider jwt) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
    }

    /**
     * 로그인 흐름
     * 1) 잠겨 있으면 거부 (5번 틀리면 10분)
     * 2) AuthenticationManager 에 아이디·비밀번호를 넘김
     *    → UserDetailsService 로 DB 에서 계정을 찾고 → PasswordEncoder(BCrypt)로 비밀번호 비교
     * 3) 맞으면 JWT 발급, 틀리면 실패 횟수 기록
     */
    public JwtProvider.Issued login(String username, String password) {
        long now = System.currentTimeMillis();
        String name = username == null ? "" : username.trim();

        Long lockedUntil = limiter.lockedUntil(name, now);
        if (lockedUntil != null) {
            long minutes = Math.max(1, (lockedUntil - now + 59_999) / 60_000);
            throw new AuthError(429, "로그인 시도가 너무 많아요. " + minutes + "분 뒤에 다시 시도해주세요.");
        }

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(name, password));
        } catch (AuthenticationException e) {
            // 아이디가 없는 경우와 비밀번호가 틀린 경우를 구분해서 알려주지 않는다 (아이디 존재 여부 노출 방지)
            boolean locked = limiter.recordFailure(name, now);
            if (locked) {
                throw new AuthError(429, "5번 틀려서 10분 동안 로그인이 잠겼어요.");
            }
            throw new AuthError(401, "아이디 또는 비밀번호가 맞지 않아요. (남은 시도 "
                    + limiter.remainingAttempts(name, now) + "회)");
        }

        limiter.recordSuccess(name);
        return jwt.issue(name);
    }

    /**
     * 비밀번호 변경. 바꾸면 이전에 발급된 토큰은 모두 무효가 되므로 새 토큰을 돌려준다.
     */
    public JwtProvider.Issued changePassword(String username, String currentPassword, String newPassword) {
        AppUserRepository.AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new AuthError(401, "로그인이 필요해요"));

        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.passwordHash())) {
            throw new AuthError(400, "현재 비밀번호가 맞지 않아요.");
        }
        validateNewPassword(newPassword);
        if (passwordEncoder.matches(newPassword, user.passwordHash())) {
            throw new AuthError(400, "지금 비밀번호와 다른 비밀번호를 입력해주세요.");
        }

        users.updatePassword(username, passwordEncoder.encode(newPassword), System.currentTimeMillis());
        return jwt.issue(username);
    }

    static void validateNewPassword(String pw) {
        if (pw == null || pw.length() < MIN_PASSWORD_LENGTH) {
            throw new AuthError(400, "새 비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 해요.");
        }
        // BCrypt 는 72바이트까지만 비교해서, 그보다 길면 뒷부분이 무시된다
        if (pw.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AuthError(400, "새 비밀번호가 너무 길어요. (최대 72바이트)");
        }
    }

    /** 화면에 그대로 보여줄 메시지와 HTTP 상태 */
    public static class AuthError extends RuntimeException {
        private final int status;

        public AuthError(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
