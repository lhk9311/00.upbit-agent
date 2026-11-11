package com.lhk.upbitagent.agent;

/**
 * 거래 한 건 (매수 1번 + 매도 1번).
 * OPEN(진행 중)이면 매도 관련 필드는 모두 null.
 * 시각은 epoch ms, 금액은 원, pnlRate 는 % 단위.
 */
public record Trade(
        Long id,
        Long strategyId,
        String strategyName,
        String market,
        String koreanName,
        TradeStatus status,
        TradeMode mode,

        double buyPrice,        // 체결가(슬리피지 반영)
        double quantity,
        double buyAmount,       // buyPrice × quantity
        double buyFee,
        long buyAt,
        double highestPrice,    // 보유 중 최고가 (트레일링 스탑용)

        Double sellPrice,
        Double sellAmount,
        Double sellFee,
        Long sellAt,
        String sellReason,
        Double pnl,             // 실현손익(원, 수수료 차감)
        Double pnlRate          // 실현수익률(%)
) {

    /** 수수료 포함 총 매수 비용 */
    public double cost() {
        return buyAmount + buyFee;
    }
}
