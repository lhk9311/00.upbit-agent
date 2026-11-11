package com.lhk.upbitagent.domain;

/**
 * 조건 검색에 쓰는 기준봉. path 는 업비트 캔들 API 경로.
 */
public enum CandleUnit {

    MIN15("minutes/15", "15분봉"),
    MIN60("minutes/60", "1시간봉"),
    MIN240("minutes/240", "4시간봉"),
    DAY("days", "일봉");

    private final String path;
    private final String label;

    CandleUnit(String path, String label) {
        this.path = path;
        this.label = label;
    }

    public String path() {
        return path;
    }

    public String label() {
        return label;
    }
}
