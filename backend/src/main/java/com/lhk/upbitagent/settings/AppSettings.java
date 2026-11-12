package com.lhk.upbitagent.settings;

import com.lhk.upbitagent.agent.AgentEngine;

import java.util.ArrayList;
import java.util.List;

/**
 * 설정 탭에서 바꾸는 값들. DB(app_setting)에 JSON 한 덩어리로 저장한다.
 *
 * @param buyingPaused           true 면 모든 전략의 신규 매수를 멈춘다 (보유 종목 매도 관리는 계속)
 * @param feeRate                거래 수수료율 (0.0005 = 0.05%)
 * @param paperSlippage          모의 체결가를 불리하게 조정하는 비율 (0.001 = 0.1%)
 * @param reentryCooldownMinutes 매도한 종목을 다시 사지 않는 시간(분)
 * @param strategyDefaults       새 전략 설정 창에 처음 채워지는 값
 */
public record AppSettings(
        boolean buyingPaused,
        double feeRate,
        double paperSlippage,
        int reentryCooldownMinutes,
        StrategyDefaults strategyDefaults
) {

    /** null 이면 그 규칙을 기본으로 쓰지 않음 (예: 트레일링 null = 처음엔 꺼진 상태) */
    public record StrategyDefaults(
            Double orderKrw,
            Integer maxPositions,
            Double takeProfitPct,
            Double stopLossPct,
            Double trailingStopPct,
            Integer maxHoldHours,
            Double dailyLossLimitKrw
    ) {
        public static StrategyDefaults standard() {
            return new StrategyDefaults(10_000.0, 3, 5.0, 3.0, null, 24, 30_000.0);
        }
    }

    public AgentEngine.Config agentConfig() {
        return new AgentEngine.Config(feeRate, paperSlippage);
    }

    public long reentryCooldownMs() {
        return reentryCooldownMinutes * 60_000L;
    }

    /** 문제가 있으면 메시지 목록, 없으면 빈 목록 */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (!(feeRate >= 0 && feeRate <= 0.01)) {
            errors.add("수수료율은 0~1% 사이여야 해요");
        }
        if (!(paperSlippage >= 0 && paperSlippage <= 0.02)) {
            errors.add("체결가 불리 비율은 0~2% 사이여야 해요");
        }
        if (reentryCooldownMinutes < 0 || reentryCooldownMinutes > 7 * 24 * 60) {
            errors.add("재진입 대기는 0분~7일 사이여야 해요");
        }

        StrategyDefaults d = strategyDefaults;
        if (d == null) {
            errors.add("새 전략 기본값이 없어요");
            return errors;
        }
        if (d.orderKrw() == null || d.orderKrw() < AgentEngine.MIN_ORDER_KRW) {
            errors.add("기본 1회 매수 금액은 5,000원 이상이어야 해요");
        }
        if (d.maxPositions() == null || d.maxPositions() < 1 || d.maxPositions() > 10) {
            errors.add("기본 최대 보유 종목 수는 1~10개여야 해요");
        }
        pct(d.takeProfitPct(), "기본 익절 %", errors);
        pct(d.stopLossPct(), "기본 손절 %", errors);
        pct(d.trailingStopPct(), "기본 트레일링 %", errors);
        if (d.maxHoldHours() != null && d.maxHoldHours() < 1) {
            errors.add("기본 최대 보유 시간은 1시간 이상이어야 해요");
        }
        if (d.dailyLossLimitKrw() != null && d.dailyLossLimitKrw() <= 0) {
            errors.add("기본 일일 손실 한도는 0보다 커야 해요");
        }
        return errors;
    }

    private static void pct(Double v, String label, List<String> errors) {
        if (v != null && !(v > 0 && v < 100)) {
            errors.add(label + "는 0보다 크고 100보다 작아야 해요");
        }
    }
}
