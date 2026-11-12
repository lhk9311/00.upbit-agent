package com.lhk.upbitagent.agent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

/**
 * 매수 시간대 (한국 시간). 이 시간 안에서만 새로 산다. 매도는 시간과 상관없이 규칙대로 한다.
 *
 * 예) 09:00 ~ 09:10 → 9시 0분 0초부터 9시 9분 59초까지 매수 (끝 시각은 포함하지 않음)
 * 자정을 넘는 시간대(23:00 ~ 01:00)도 된다.
 *
 * 참고: 업비트의 "전일 대비 등락률"은 매일 09:00(한국 시간)에 0 으로 다시 시작한다.
 * 그래서 "09:00~09:10 에 +5% 이상" = "오늘 장 시작 10분 안에 5% 넘게 오른 종목" 이 된다.
 *
 * @param from 시작 "HH:mm"
 * @param to   끝 "HH:mm" (포함하지 않음)
 */
public record BuyWindow(String from, String to) {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public BuyWindow {
        LocalTime f = parseTime(from, "매수 시작 시각");
        LocalTime t = parseTime(to, "매수 끝 시각");
        if (f.equals(t)) {
            throw new IllegalArgumentException("매수 시작과 끝 시각이 같아요");
        }
        from = f.toString(); // "9:00" → "09:00" 으로 맞춤
        to = t.toString();
    }

    /**
     * 화면 입력 → 시간대. 둘 다 비었으면 null (시간 제한 없음), 하나만 있으면 오류.
     */
    public static BuyWindow of(String from, String to) {
        boolean noFrom = from == null || from.isBlank();
        boolean noTo = to == null || to.isBlank();
        if (noFrom && noTo) {
            return null;
        }
        if (noFrom || noTo) {
            throw new IllegalArgumentException("매수 시간대는 시작과 끝을 둘 다 입력해주세요");
        }
        return new BuyWindow(from.trim(), to.trim());
    }

    /** 이 시각(epoch ms)이 매수 시간대 안인지 */
    public boolean contains(long epochMs) {
        return openedAt(epochMs) != null;
    }

    /**
     * epochMs 가 시간대 안이면, 이번 시간대가 시작된 시각(epoch ms). 밖이면 null.
     * (09:00 직후엔 아직 어제 등락률이 남아 있을 수 있어서, 시작 후 얼마나 지났는지 확인할 때 쓴다)
     */
    public Long openedAt(long epochMs) {
        ZonedDateTime now = Instant.ofEpochMilli(epochMs).atZone(KST);
        LocalTime f = LocalTime.parse(from);
        LocalTime t = LocalTime.parse(to);
        LocalTime time = now.toLocalTime();
        LocalDate day = now.toLocalDate();

        if (f.isBefore(t)) {
            // 같은 날 안: from <= time < to
            return !time.isBefore(f) && time.isBefore(t) ? start(day, f) : null;
        }
        // 자정을 넘는 시간대: time >= from (오늘 시작) 또는 time < to (어제 시작)
        if (!time.isBefore(f)) {
            return start(day, f);
        }
        return time.isBefore(t) ? start(day.minusDays(1), f) : null;
    }

    /** [fromMs, toMs) 구간에 시간대가 조금이라도 걸치는지 (백테스트에서 봉 단위로 확인할 때) */
    public boolean overlaps(long fromMs, long toMs) {
        if (toMs - fromMs >= 24 * 60 * 60_000L) {
            return true;
        }
        // 구간 시작이 시간대 안이거나, 구간 안에 시간대 시작 시각이 있으면 걸친다
        if (contains(fromMs)) {
            return true;
        }
        Long next = nextStart(fromMs);
        return next < toMs;
    }

    /** t 이후(포함) 처음 오는 시간대 시작 시각 */
    private long nextStart(long t) {
        LocalDate day = Instant.ofEpochMilli(t).atZone(KST).toLocalDate();
        long today = start(day, LocalTime.parse(from));
        return today >= t ? today : start(day.plusDays(1), LocalTime.parse(from));
    }

    /** 시간대 길이(분) */
    public long minutes() {
        long m = java.time.Duration.between(LocalTime.parse(from), LocalTime.parse(to)).toMinutes();
        return m > 0 ? m : m + 24 * 60;
    }

    public String label() {
        return from + "~" + to;
    }

    private static long start(LocalDate day, LocalTime f) {
        return day.atTime(f).atZone(KST).toInstant().toEpochMilli();
    }

    private static LocalTime parseTime(String v, String label) {
        if (v == null) {
            throw new IllegalArgumentException(label + "이 없어요");
        }
        try {
            String s = v.trim();
            if (s.length() == 4 && s.charAt(1) == ':') {
                s = "0" + s; // 9:00 → 09:00
            }
            return LocalTime.parse(s);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(label + " 형식이 올바르지 않아요 (예: 09:00): " + v);
        }
    }
}
