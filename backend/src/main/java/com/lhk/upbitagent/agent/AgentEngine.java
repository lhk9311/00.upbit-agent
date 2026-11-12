package com.lhk.upbitagent.agent;

import com.lhk.upbitagent.screener.ScreenerResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 에이전트의 판단 로직. "지금 무엇을 팔고 무엇을 살지"만 결정하고,
 * 실제 저장·주문은 AgentService 가 한다. Spring 에 의존하지 않아 단위 테스트가 쉽다.
 *
 * 한 주기(cycle)에서
 * 1) 보유 포지션마다 매도 규칙 확인: 손절 → 익절 → 트레일링 → 보유시간 순서
 * 2) 오늘 실현손실이 한도를 넘었으면 신규 매수 중단
 * 3) 빈 자리(최대 보유 수 - 보유 수)만큼 조건 검색 상위 종목을 매수
 *    (이미 보유 중이거나, 최근에 판 종목(재진입 대기)은 건너뜀)
 */
public class AgentEngine {

    /** 업비트 KRW 마켓 최소 주문 금액 */
    public static final double MIN_ORDER_KRW = 5_000;

    private static final long HOUR = 60 * 60 * 1000L;

    /**
     * @param feeRate  거래 수수료율 (업비트 KRW 마켓 0.05% = 0.0005)
     * @param slippage 모의 체결 가격 불리하게 조정하는 비율 (0.001 = 0.1%)
     */
    public record Config(double feeRate, double slippage) {
        public static Config defaults() {
            return new Config(0.0005, 0.001);
        }
    }

    public record SellOrder(Trade trade, double marketPrice, String reason) {
    }

    public record BuyOrder(String market, String koreanName, double marketPrice) {
    }

    public record HighUpdate(long tradeId, double highestPrice) {
    }

    public record Decision(List<SellOrder> sells, List<BuyOrder> buys, List<HighUpdate> highs, List<String> notes) {
    }

    private final Config config;

    public AgentEngine(Config config) {
        this.config = config;
    }

    /**
     * @param strategy         전략
     * @param openTrades       이 전략의 진행 중 거래
     * @param prices           종목별 현재가
     * @param candidates       조건 검색 결과 (정렬된 순서). null 이면 매수 판단을 하지 않는다
     * @param todayRealizedPnl 이 전략의 오늘 실현손익(원)
     * @param cooldownMarkets  재진입 대기 중인 종목 (최근에 매도한 종목)
     * @param now              현재 시각(ms)
     */
    public Decision decide(Strategy strategy,
                           List<Trade> openTrades,
                           Map<String, Double> prices,
                           List<ScreenerResult.Item> candidates,
                           double todayRealizedPnl,
                           Set<String> cooldownMarkets,
                           long now) {

        List<SellOrder> sells = new ArrayList<>();
        List<HighUpdate> highs = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        // ---------- 1. 매도 ----------
        for (Trade t : openTrades) {
            Double price = prices.get(t.market());
            if (price == null) {
                continue; // 현재가를 모르면 판단 보류
            }

            double high = Math.max(t.highestPrice(), price);
            if (high > t.highestPrice()) {
                highs.add(new HighUpdate(t.id(), high));
            }

            String reason = exitReason(strategy, t, price, high, now);
            if (reason != null) {
                sells.add(new SellOrder(t, price, reason));
            }
        }

        // ---------- 2~3. 매수 ----------
        List<BuyOrder> buys = new ArrayList<>();

        if (!strategy.enabled()) {
            return new Decision(sells, buys, highs, notes); // 정지된 전략은 매도만
        }
        if (strategy.buyWindow() != null && !strategy.buyWindow().contains(now)) {
            return new Decision(sells, buys, highs, notes); // 매수 시간대 밖 → 매도만 (매분 기록이 쌓이지 않게 메모 없음)
        }
        if (candidates == null) {
            notes.add("시세 데이터 수집 중이라 매수 판단을 건너뜀");
            return new Decision(sells, buys, highs, notes);
        }
        if (strategy.dailyLossLimitKrw() != null && todayRealizedPnl <= -strategy.dailyLossLimitKrw()) {
            notes.add(String.format(Locale.ROOT, "오늘 실현손실 %,.0f원 → 일일 손실 한도 도달, 신규 매수 중단", todayRealizedPnl));
            return new Decision(sells, buys, highs, notes);
        }
        if (strategy.orderKrw() < MIN_ORDER_KRW) {
            notes.add("1회 주문 금액이 최소 주문 금액(5,000원)보다 작아 매수하지 않음");
            return new Decision(sells, buys, highs, notes);
        }

        int slots = strategy.maxPositions() - (openTrades.size() - sells.size());

        Set<String> held = new HashSet<>();
        for (Trade t : openTrades) {
            held.add(t.market()); // 이번 주기에 파는 종목도 같은 주기에 다시 사지 않음
        }

        for (ScreenerResult.Item item : candidates) {
            if (slots <= 0) {
                break;
            }
            if (held.contains(item.market()) || cooldownMarkets.contains(item.market())) {
                continue;
            }
            buys.add(new BuyOrder(item.market(), item.koreanName(), item.tradePrice()));
            held.add(item.market());
            slots--;
        }

        return new Decision(sells, buys, highs, notes);
    }

    /** 매도 사유. 매도하지 않으면 null */
    String exitReason(Strategy s, Trade t, double price, double high, long now) {
        double rate = (price - t.buyPrice()) / t.buyPrice() * 100;

        if (s.stopLossPct() != null && rate <= -s.stopLossPct()) {
            return String.format(Locale.ROOT, "손절 (%.2f%%)", rate);
        }
        if (s.takeProfitPct() != null && rate >= s.takeProfitPct()) {
            return String.format(Locale.ROOT, "익절 (+%.2f%%)", rate);
        }
        if (s.trailingStopPct() != null && high > t.buyPrice()
                && price <= high * (1 - s.trailingStopPct() / 100)) {
            return String.format(Locale.ROOT, "트레일링 (최고가 대비 -%.2f%%)", (high - price) / high * 100);
        }
        if (s.maxHoldHours() != null && now - t.buyAt() >= s.maxHoldHours() * HOUR) {
            return "보유시간 " + s.maxHoldHours() + "시간 초과";
        }
        return null;
    }

    // ---------- 모의 체결 계산 ----------

    /** 매수 체결: 주문 금액(수수료 포함) 안에서 살 수 있는 만큼 */
    public Trade openTrade(Strategy s, BuyOrder order, long now) {
        double fill = order.marketPrice() * (1 + config.slippage());
        double amount = s.orderKrw() / (1 + config.feeRate());
        double qty = amount / fill;
        double fee = amount * config.feeRate();

        return new Trade(null, s.id(), s.name(), order.market(), order.koreanName(),
                TradeStatus.OPEN, s.mode(),
                fill, qty, amount, fee, now, fill,
                null, null, null, null, null, null, null);
    }

    /** 매도 체결: 수수료를 빼고 실현손익 계산 */
    public Trade closeTrade(Trade t, double marketPrice, String reason, long now) {
        double fill = marketPrice * (1 - config.slippage());
        double amount = fill * t.quantity();
        double fee = amount * config.feeRate();
        double pnl = amount - fee - t.cost();
        double pnlRate = pnl / t.cost() * 100;

        return new Trade(t.id(), t.strategyId(), t.strategyName(), t.market(), t.koreanName(),
                TradeStatus.CLOSED, t.mode(),
                t.buyPrice(), t.quantity(), t.buyAmount(), t.buyFee(), t.buyAt(), t.highestPrice(),
                fill, amount, fee, now, reason, pnl, pnlRate);
    }
}
