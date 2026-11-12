package com.lhk.upbitagent.agent;

import com.lhk.upbitagent.screener.ScreenerCondition;

/**
 * 전략 = 매수 조건(조건 검색) + 매도 규칙 + 리스크 한도.
 * 퍼센트 값은 % 단위(3 = 3%), null 이면 그 규칙은 사용하지 않는다.
 */
public record Strategy(
        Long id,
        String name,
        ScreenerCondition condition,
        String naturalText,         // 자연어로 입력한 원문 (패널로 직접 만들었으면 null)

        double orderKrw,            // 1회 매수 금액(원, 수수료 포함)
        int maxPositions,           // 동시에 보유할 최대 종목 수

        Double takeProfitPct,       // 익절: 매수가 대비 +X% 이상
        Double stopLossPct,         // 손절: 매수가 대비 -X% 이하 (양수로 입력)
        Double trailingStopPct,     // 트레일링: 보유 중 최고가 대비 -X% (수익 구간에서만)
        Integer maxHoldHours,       // 최대 보유 시간

        Double dailyLossLimitKrw,   // 오늘 실현손실이 이 금액 이상이면 신규 매수 중단 (양수로 입력)

        TradeMode mode,
        boolean enabled,
        Long createdAt
) {

    /** enabled 만 바꾼 복사본 (record 는 값을 못 바꾸므로 새로 만든다) */
    public Strategy withEnabled(boolean value) {
        return new Strategy(id, name, condition, naturalText, orderKrw, maxPositions,
                takeProfitPct, stopLossPct, trailingStopPct, maxHoldHours, dailyLossLimitKrw,
                mode, value, createdAt);
    }
}
