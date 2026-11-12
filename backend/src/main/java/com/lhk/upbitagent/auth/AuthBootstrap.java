package com.lhk.upbitagent.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 서버가 다 켜진 직후 한 번 실행 (ApplicationRunner).
 * - 계정이 하나도 없으면 관리자 계정을 만든다.
 * - app.auth.reset-on-start=true 이면 관리자 비밀번호를 다시 만든다 (비밀번호를 잊었을 때).
 *
 * 비밀번호를 설정하지 않았으면 무작위로 만들어 콘솔에 "한 번만" 보여준다.
 * DB 에는 BCrypt 해시만 저장되므로, 이 로그를 놓치면 원래 비밀번호는 다시 볼 수 없다 → 재설정으로 해결.
 */
@Component
public class AuthBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthBootstrap.class);
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"; // 헷갈리는 0/O, 1/l 제외

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String initialPassword;
    private final boolean resetOnStart;

    public AuthBootstrap(AppUserRepository users, PasswordEncoder passwordEncoder,
                         @Value("${app.auth.initial-username:admin}") String username,
                         @Value("${app.auth.initial-password:}") String initialPassword,
                         @Value("${app.auth.reset-on-start:false}") boolean resetOnStart) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.initialPassword = initialPassword;
        this.resetOnStart = resetOnStart;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean exists = users.findByUsername(username).isPresent();
        if (users.count() > 0 && !(resetOnStart && exists)) {
            return; // 이미 계정이 있고 재설정 요청도 없으면 아무것도 안 함
        }

        boolean generated = initialPassword == null || initialPassword.isBlank();
        String password = generated ? randomPassword(12) : initialPassword;
        String hash = passwordEncoder.encode(password);
        long now = System.currentTimeMillis();

        if (exists) {
            users.updatePassword(username, hash, now);
        } else {
            users.insert(username, hash, now);
        }

        String action = exists ? "관리자 비밀번호를 다시 만들었어요" : "관리자 계정을 만들었어요";
        if (generated) {
            log.warn("""

                    ==================================================
                      {}
                      아이디   : {}
                      비밀번호 : {}
                      → 로그인 후 설정 탭에서 비밀번호를 바꿔주세요.
                      (이 비밀번호는 지금 한 번만 보여요)
                    ==================================================""", action, username, password);
        } else {
            log.warn("{}: {} (비밀번호는 ADMIN_PASSWORD 값)", action, username);
        }
        if (resetOnStart) {
            log.warn("ADMIN_RESET 을 다시 false 로 바꿔주세요. 켜두면 재시작할 때마다 비밀번호가 바뀌어요.");
        }
    }

    private static String randomPassword(int length) {
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(r.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
