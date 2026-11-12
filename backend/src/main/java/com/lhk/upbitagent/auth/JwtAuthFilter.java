package com.lhk.upbitagent.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 모든 요청마다 한 번 실행되는 필터.
 * "Authorization: Bearer 토큰" 헤더가 있고 토큰이 유효하면 "로그인한 사용자" 로 표시한다.
 *
 * 여기서 요청을 막지는 않는다. 표시만 하고 다음으로 넘긴다.
 * 실제로 막는 건 SecurityConfig 의 규칙(authenticated())이다 → 표시가 없으면 401.
 *
 * @Component 를 붙이지 않은 이유: 붙이면 스프링이 일반 서블릿 필터로도 한 번 더 등록해서
 * 같은 요청에 두 번 끼어들 수 있다. SecurityConfig 에서 직접 만들어 보안 필터 체인에만 넣는다.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtProvider jwt;
    private final AppUserRepository users;

    public JwtAuthFilter(JwtProvider jwt, AppUserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(PREFIX)) {
            jwt.parse(header.substring(PREFIX.length()))
                    // 토큰은 맞아도 계정이 지워졌거나 비밀번호를 바꾼 뒤라면 거부
                    .flatMap(p -> users.findByUsername(p.username())
                            .filter(u -> u.acceptsTokenIssuedAt(p.issuedAt())))
                    .ifPresent(u -> {
                        var auth = new UsernamePasswordAuthenticationToken(
                                u.username(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }

        chain.doFilter(request, response);
    }
}
