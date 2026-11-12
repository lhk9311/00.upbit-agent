package com.lhk.upbitagent.web;

import com.lhk.upbitagent.auth.AuthService;
import com.lhk.upbitagent.auth.JwtProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /** 로그인 (로그인 없이 호출 가능한 유일한 API) */
    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest req) {
        JwtProvider.Issued issued = auth.login(req.username(), req.password());
        return new TokenResponse(issued.token(), req.username().trim(), issued.expiresAt());
    }

    /**
     * 지금 로그인한 사용자. Authentication 파라미터는 JwtAuthFilter 가 넣어둔 값을 스프링이 꺼내준다.
     */
    @GetMapping("/me")
    public Map<String, String> me(Authentication authentication) {
        return Map.of("username", authentication.getName());
    }

    /** 비밀번호 변경 → 새 토큰 */
    @PostMapping("/password")
    public TokenResponse changePassword(Authentication authentication, @RequestBody PasswordRequest req) {
        String username = authentication.getName();
        JwtProvider.Issued issued = auth.changePassword(username, req.currentPassword(), req.newPassword());
        return new TokenResponse(issued.token(), username, issued.expiresAt());
    }

    @ExceptionHandler(AuthService.AuthError.class)
    public ResponseEntity<Map<String, String>> handle(AuthService.AuthError e) {
        return ResponseEntity.status(e.status()).body(Map.of("message", e.getMessage()));
    }

    public record LoginRequest(String username, String password) {
    }

    public record PasswordRequest(String currentPassword, String newPassword) {
    }

    public record TokenResponse(String token, String username, long expiresAt) {
    }
}
