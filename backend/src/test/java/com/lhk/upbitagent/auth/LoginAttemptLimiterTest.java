package com.lhk.upbitagent.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptLimiterTest {

    private static final long MIN = 60_000L;

    @Test
    void 다섯번_틀리면_잠기고_시간이_지나면_풀림() {
        LoginAttemptLimiter l = new LoginAttemptLimiter(5, 10 * MIN, 10 * MIN);
        for (int i = 0; i < 4; i++) {
            assertFalse(l.recordFailure("admin", i * MIN));
        }
        assertEquals(1, l.remainingAttempts("admin", 4 * MIN));
        assertTrue(l.recordFailure("admin", 4 * MIN));

        assertEquals(14 * MIN, l.lockedUntil("admin", 5 * MIN).longValue());
        assertNull(l.lockedUntil("admin", 14 * MIN));   // 잠금 해제
        assertEquals(5, l.remainingAttempts("admin", 14 * MIN));
    }

    @Test
    void 오래된_실패는_세지_않음() {
        LoginAttemptLimiter l = new LoginAttemptLimiter(3, 10 * MIN, 10 * MIN);
        l.recordFailure("admin", 0);
        l.recordFailure("admin", MIN);
        // 0분의 실패는 11분 시점엔 window(10분) 밖
        assertFalse(l.recordFailure("admin", 11 * MIN));
        assertNull(l.lockedUntil("admin", 11 * MIN));
    }

    @Test
    void 성공하면_실패기록_초기화() {
        LoginAttemptLimiter l = new LoginAttemptLimiter(3, 10 * MIN, 10 * MIN);
        l.recordFailure("admin", 0);
        l.recordFailure("admin", 1);
        l.recordSuccess("admin");
        assertEquals(3, l.remainingAttempts("admin", 2));
    }

    @Test
    void 대소문자와_공백이_달라도_같은_아이디로_취급() {
        LoginAttemptLimiter l = new LoginAttemptLimiter(2, 10 * MIN, 10 * MIN);
        l.recordFailure("Admin", 0);
        assertTrue(l.recordFailure(" admin ", 1));
        assertTrue(l.lockedUntil("ADMIN", 2) != null);
    }
}
