package com.lhk.upbitagent.agent;

import com.lhk.upbitagent.screener.ScreenerCondition;

import java.util.ArrayList;
import java.util.List;

/**
 * 화면에서 받는 전략 생성·수정 요청. toStrategy() 에서 검증한다.
 */
public record StrategyRequest(
        String name,
        ScreenerCondition condition,
        String naturalText,
        Double orderKrw,
        Integer maxPositions,
        Double takeProfitPct,
        Double stopLossPct,
        Double trailingStopPct,
        Integer maxHoldHours,
        Double dailyLossLimitKrw,
        Boolean enabled
) {

    public static final int MAX_POSITIONS_LIMIT = 10;

    /**
     * 검증 후 Strategy 로 변환. 문제가 있으면 IllegalArgumentException (메시지는 화면에 그대로 표시).
     * 지금은 모의매매만 지원하므로 mode 는 항상 PAPER.
     */
    public Strategy toStrategy(Long id, long createdAt) {
        List<String> errors = new ArrayList<>();

        if (condition == null) {
            errors.add("검색 조건이 없어요");
        }
        if (orderKrw == null || orderKrw < AgentEngine.MIN_ORDER_KRW) {
            errors.add("1회 매수 금액은 5,000원 이상이어야 해요 (업비트 최소 주문 금액)");
        }
        if (maxPositions == null || maxPositions < 1 || maxPositions > MAX_POSITIONS_LIMIT) {
            errors.add("최대 보유 종목 수는 1~" + MAX_POSITIONS_LIMIT + "개 사이여야 해요");
        }
        positive(takeProfitPct, "익절 %", errors);
        positive(stopLossPct, "손절 %", errors);
        positive(trailingStopPct, "트레일링 %", errors);
        if (stopLossPct != null && stopLossPct >= 100) {
            errors.add("손절 %는 100보다 작아야 해요");
        }
        if (trailingStopPct != null && trailingStopPct >= 100) {
            errors.add("트레일링 %는 100보다 작아야 해요");
        }
        if (maxHoldHours != null && maxHoldHours < 1) {
            errors.add("최대 보유 시간은 1시간 이상이어야 해요");
        }
        positive(dailyLossLimitKrw, "일일 손실 한도", errors);

        // 매도 규칙이 하나도 없으면 포지션이 영원히 안 닫힌다
        if (takeProfitPct == null && stopLossPct == null && trailingStopPct == null && maxHoldHours == null) {
            errors.add("매도 규칙(익절·손절·트레일링·최대 보유 시간)을 하나 이상 정해주세요");
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" / ", errors));
        }

        String safeName = (name == null || name.isBlank()) ? "전략 " + (id == null ? "" : id) : name.trim();
        if (safeName.length() > 100) {
            safeName = safeName.substring(0, 100);
        }
        String text = (naturalText == null || naturalText.isBlank()) ? null : naturalText.trim();
        if (text != null && text.length() > 500) {
            text = text.substring(0, 500);
        }

        return new Strategy(id, safeName.trim(), condition, text,
                orderKrw, maxPositions,
                takeProfitPct, stopLossPct, trailingStopPct, maxHoldHours, dailyLossLimitKrw,
                TradeMode.PAPER,
                enabled == null || enabled,
                createdAt);
    }

    private static void positive(Double v, String label, List<String> errors) {
        if (v != null && v <= 0) {
            errors.add(label + "는 0보다 커야 해요");
        }
    }
}
