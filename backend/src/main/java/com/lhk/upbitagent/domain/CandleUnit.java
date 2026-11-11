package com.lhk.upbitagent.domain;

/**
 * 조건 검색에 쓰는 기준봉. path 는 업비트 캔들 API 경로.
 */
public enum CandleUnit {

    MIN15("minutes/15", "15분봉", 15),
    MIN60("minutes/60", "1시간봉", 60),
    MIN240("minutes/240", "4시간봉", 240),
    DAY("days", "일봉", 1440);

    private final String path;
    private final String label;
    private final int minutes;

    CandleUnit(String path, String label, int minutes) {
        this.path = path;
        this.label = label;
        this.minutes = minutes;
    }

    /** 봉 하나의 길이(ms) */
    public long millis() {
        return minutes * 60_000L;
    }

    public String path() {
        return path;
    }

    public String label() {
        return label;
    }
}
