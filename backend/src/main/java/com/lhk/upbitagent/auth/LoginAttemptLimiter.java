package com.lhk.upbitagent.auth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * 로그인 시도 제한 (비밀번호 무차별 대입 방지).
 *
 * 규칙: 같은 아이디로 window 시간 안에 maxFailures 번 틀리면 lockMs 동안 잠근다.
 * 로그인에 성공하면 그 아이디의 실패 기록은 지운다.
 *
 * 서버 메모리에만 저장하므로 서버를 재시작하면 초기화된다 (혼자 쓰는 서비스라 충분).
 * Spring 에 의존하지 않는 순수 Java (시각을 파라미터로 받아서 테스트하기 쉽다).
 */
public class LoginAttemptLimiter {

    private final int maxFailures;
    private final long windowMs;
    private final long lockMs;

    /** 아이디 → 최근 실패 시각들 */
    private final Map<String, Deque<Long>> failures = new HashMap<>();
    /** 아이디 → 잠금이 풀리는 시각 */
    private final Map<String, Long> lockedUntil = new HashMap<>();

    public LoginAttemptLimiter(int maxFailures, long windowMs, long lockMs) {
        this.maxFailures = maxFailures;
        this.windowMs = windowMs;
        this.lockMs = lockMs;
    }

    /** 기본값: 10분 안에 5번 틀리면 10분 잠금 */
    public static LoginAttemptLimiter defaults() {
        return new LoginAttemptLimiter(5, 10 * 60_000L, 10 * 60_000L);
    }

    /** 잠겨 있으면 풀리는 시각, 아니면 null */
    public synchronized Long lockedUntil(String username, long now) {
        String key = key(username);
        Long until = lockedUntil.get(key);
        if (until == null) {
            return null;
        }
        if (now >= until) {
            lockedUntil.remove(key);
            failures.remove(key);
            return null;
        }
        return until;
    }

    /**
     * 실패 기록. 이번 실패로 잠기면 true.
     */
    public synchronized boolean recordFailure(String username, long now) {
        String key = key(username);
        Deque<Long> q = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        q.addLast(now);
        while (!q.isEmpty() && now - q.peekFirst() > windowMs) {
            q.removeFirst(); // window 밖의 오래된 실패는 버림
        }
        if (q.size() >= maxFailures) {
            lockedUntil.put(key, now + lockMs);
            q.clear();
            return true;
        }
        return false;
    }

    /** 잠기기 전까지 남은 시도 횟수 */
    public synchronized int remainingAttempts(String username, long now) {
        Deque<Long> q = failures.get(key(username));
        if (q == null) {
            return maxFailures;
        }
        long recent = q.stream().filter(t -> now - t <= windowMs).count();
        return (int) Math.max(0, maxFailures - recent);
    }

    public synchronized void recordSuccess(String username) {
        String key = key(username);
        failures.remove(key);
        lockedUntil.remove(key);
    }

    /** 대소문자·공백 차이로 제한을 피하지 못하게 */
    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
