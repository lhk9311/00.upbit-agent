package com.lhk.upbitagent.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.nio.charset.StandardCharsets;

/**
 * 보안 설정. "어떤 주소는 로그인 없이 되고, 나머지는 로그인해야 된다" 를 정하는 곳.
 *
 * 요청이 들어오면 컨트롤러에 닿기 전에 필터들을 차례로 지나간다 (보안 필터 체인).
 *   요청 → JwtAuthFilter(토큰 확인) → 권한 규칙 확인 → 컨트롤러
 */
@Configuration
public class SecurityConfig {

    /** 로그인 없이 허용하는 주소 */
    private static final RequestMatcher PUBLIC = request -> {
        String uri = request.getRequestURI();
        return uri.equals("/api/auth/login")
                || uri.startsWith("/h2-console")   // 개발용 DB 콘솔 (H2 자체 로그인이 따로 있음)
                || uri.equals("/error");           // 스프링 기본 에러 응답
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwt, AppUserRepository users)
            throws Exception {
        http
                // JWT 를 헤더로 보내는 방식이라 쿠키 기반 공격(CSRF)이 해당되지 않아 끈다
                .csrf(AbstractHttpConfigurer::disable)
                // 스프링 기본 로그인 화면·팝업은 안 쓴다 (Vue 화면에서 직접 로그인)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 세션을 만들지 않는다 = 서버가 로그인 상태를 기억하지 않는다 (JWT 방식의 핵심)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // H2 콘솔이 iframe 을 써서 같은 사이트 안에서는 허용
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().authenticated())
                // 로그인 안 한 요청 → 스프링 기본 화면 대신 JSON 401
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter().write("{\"message\":\"로그인이 필요해요\"}");
                }))
                // 아이디·비밀번호 필터보다 먼저 토큰을 확인
                .addFilterBefore(new JwtAuthFilter(jwt, users), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 비밀번호 해시. BCrypt 는 같은 비밀번호도 매번 다른 해시가 나오고, 일부러 느리게 만들어 대입 공격을 어렵게 한다 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 로그인할 때 스프링 시큐리티가 "이 아이디의 계정 정보" 를 DB 에서 가져오는 방법 */
    @Bean
    public UserDetailsService userDetailsService(AppUserRepository users) {
        return username -> users.findByUsername(username)
                .map(u -> User.withUsername(u.username()).password(u.passwordHash()).roles("ADMIN").build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    /** 아이디·비밀번호 확인을 맡는 객체. 위의 UserDetailsService + PasswordEncoder 로 자동 구성된다 */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
