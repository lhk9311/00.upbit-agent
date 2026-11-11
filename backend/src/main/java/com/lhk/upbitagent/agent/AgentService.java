package com.lhk.upbitagent.agent;

import com.lhk.upbitagent.domain.CandleUnit;
import com.lhk.upbitagent.market.MarketDataStore;
import com.lhk.upbitagent.screener.ScreenerCondition;
import com.lhk.upbitagent.screener.ScreenerEngine;
import com.lhk.upbitagent.screener.ScreenerResult;
import com.lhk.upbitagent.store.AgentLogRepository;
import com.lhk.upbitagent.store.StrategyRepository;
import com.lhk.upbitagent.store.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 에이전트 실행기. 주기마다 전략별로 AgentEngine 에 판단을 맡기고, 결과를 DB 에 반영한다.
 * 지금은 모의매매(PAPER)만 지원한다. 실매매(LIVE)는 6단계에서 업비트 주문 API 와 연결.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 현재가가 이보다 오래됐으면 매매 판단을 하지 않는다 */
    private static final long MAX_PRICE_AGE_MS = 60_000;

    private final StrategyRepository strategies;
    private final TradeRepository trades;
    private final AgentLogRepository logs;
    private final MarketDataStore market;
    private final AgentEngine engine;
    private final ScreenerEngine screener = new ScreenerEngine();
    private final long cooldownMs;

    /** 같은 안내 문구를 매 주기 반복해서 기록하지 않기 위한 마지막 문구 */
    private final Map<Long, String> lastNote = new HashMap<>();
    private boolean staleWarned = false;

    public AgentService(StrategyRepository strategies, TradeRepository trades, AgentLogRepository logs,
                        MarketDataStore market,
                        @Value("${agent.fee-rate:0.0005}") double feeRate,
                        @Value("${agent.paper-slippage:0.001}") double slippage,
                        @Value("${agent.reentry-cooldown-minutes:60}") long cooldownMinutes) {
        this.strategies = strategies;
        this.trades = trades;
        this.logs = logs;
        this.market = market;
        this.engine = new AgentEngine(new AgentEngine.Config(feeRate, slippage));
        this.cooldownMs = cooldownMinutes * 60_000;
    }

    // ---------- 주기 실행 ----------

    @Scheduled(fixedDelayString = "${agent.cycle-ms:60000}", initialDelay = 30_000)
    public void scheduledCycle() {
        runCycle();
    }

    /** 한 주기 실행. 수동 매도와 겹치지 않도록 synchronized */
    public synchronized void runCycle() {
        if (!market.pricesFresherThan(MAX_PRICE_AGE_MS)) {
            if (!staleWarned) {
                logs.add(null, "WARN", "현재가 수신이 지연돼 매매 판단을 쉬어요 (업비트 연결 확인)");
                staleWarned = true;
            }
            return;
        }
        staleWarned = false;

        Map<String, Double> prices = market.prices();
        long now = System.currentTimeMillis();

        for (Strategy s : strategies.findAll()) {
            try {
                runStrategy(s, prices, now);
            } catch (Exception e) {
                log.error("전략 {} 실행 실패", s.id(), e);
                logs.add(s.id(), "WARN", "[" + s.name() + "] 실행 중 오류: " + e.getMessage());
            }
        }
    }

    private void runStrategy(Strategy s, Map<String, Double> prices, long now) {
        List<Trade> open = trades.findOpenByStrategy(s.id());
        if (!s.enabled() && open.isEmpty()) {
            return;
        }

        List<ScreenerResult.Item> candidates = null;
        if (s.enabled()) {
            ScreenerCondition cond = s.condition();
            CandleUnit unit = cond.unitOrDefault();
            if (market.isReady(unit)) {
                candidates = screener.evaluate(market.snapshots(unit), cond).items();
            }
        }

        double todayPnl = trades.realizedPnlSince(s.id(), startOfTodayKst());
        Set<String> cooldown = trades.soldMarketsSince(s.id(), now - cooldownMs);

        AgentEngine.Decision d = engine.decide(s, open, prices, candidates, todayPnl, cooldown, now);

        for (AgentEngine.HighUpdate h : d.highs()) {
            trades.updateHighest(h.tradeId(), h.highestPrice());
        }

        for (AgentEngine.SellOrder order : d.sells()) {
            sell(order.trade(), order.marketPrice(), order.reason(), now);
        }

        for (AgentEngine.BuyOrder order : d.buys()) {
            if (s.mode() == TradeMode.LIVE) {
                note(s, "실매매(LIVE)는 아직 지원하지 않아 매수를 건너뛰었어요");
                break;
            }
            Trade t = engine.openTrade(s, order, now);
            trades.insert(t);
            logs.add(s.id(), "BUY", String.format(Locale.ROOT, "[%s] %s 매수 %s원 × %s (%,.0f원)",
                    s.name(), t.koreanName(), price(t.buyPrice()), qty(t.quantity()), t.cost()));
        }

        if (!d.notes().isEmpty()) {
            note(s, String.join(" / ", d.notes()));
        } else {
            lastNote.remove(s.id());
        }
    }

    private void sell(Trade t, double marketPrice, String reason, long now) {
        Trade closed = engine.closeTrade(t, marketPrice, reason, now);
        if (trades.close(closed)) {
            logs.add(t.strategyId(), "SELL", String.format(Locale.ROOT, "[%s] %s 매도 %s원 · %s · %+.2f%% (%+,.0f원)",
                    t.strategyName(), t.koreanName(), price(closed.sellPrice()), reason, closed.pnlRate(), closed.pnl()));
        }
    }

    private void note(Strategy s, String message) {
        if (!message.equals(lastNote.get(s.id()))) {
            logs.add(s.id(), "INFO", "[" + s.name() + "] " + message);
            lastNote.put(s.id(), message);
        }
    }

    // ---------- 화면에서 호출 ----------

    /** 진행 중 거래를 현재가로 바로 매도 */
    public synchronized Trade closeManually(long tradeId) {
        Trade t = trades.findById(tradeId)
                .orElseThrow(() -> new IllegalArgumentException("거래를 찾을 수 없어요: " + tradeId));
        if (t.status() != TradeStatus.OPEN) {
            throw new IllegalArgumentException("이미 매도된 거래예요.");
        }
        Double p = market.price(t.market());
        if (p == null) {
            throw new IllegalStateException("현재가를 아직 받지 못했어요. 잠시 후 다시 시도해주세요.");
        }
        sell(t, p, "수동 매도", System.currentTimeMillis());
        return trades.findById(tradeId).orElseThrow();
    }

    /** 진행 중 거래 + 지금 팔면 얼마인지(수수료·슬리피지 반영) */
    public List<Position> positions() {
        long now = System.currentTimeMillis();
        return trades.findOpen().stream().map(t -> {
            Double p = market.price(t.market());
            if (p == null) {
                return new Position(t, null, null, null);
            }
            Trade ifSold = engine.closeTrade(t, p, "", now);
            return new Position(t, p, ifSold.pnl(), ifSold.pnlRate());
        }).toList();
    }

    public Summary summary() {
        Map<String, Object> st = trades.closedStats();
        long closedCount = num(st.get("closed_count")).longValue();
        long winCount = num(st.get("win_count")).longValue();
        double realized = num(st.get("realized_pnl")).doubleValue();
        double totalCost = num(st.get("total_cost")).doubleValue();

        List<Position> positions = positions();
        double evalPnl = positions.stream().mapToDouble(p -> p.evalPnl() == null ? 0 : p.evalPnl()).sum();
        double openCost = positions.stream().mapToDouble(p -> p.trade().cost()).sum();

        return new Summary(
                closedCount,
                closedCount == 0 ? null : winCount * 100.0 / closedCount,
                realized,
                totalCost == 0 ? null : realized / totalCost * 100,
                trades.realizedPnlSince(null, startOfTodayKst()),
                positions.size(),
                openCost,
                evalPnl,
                openCost == 0 ? null : evalPnl / openCost * 100);
    }

    public record Position(Trade trade, Double currentPrice, Double evalPnl, Double evalPnlRate) {
    }

    public record Summary(
            long closedCount,
            Double winRate,            // 승률(%)
            double realizedPnl,        // 누적 실현손익(원)
            Double realizedPnlRate,    // 누적 실현수익률(%) = 실현손익 / 완료 거래 매수비용 합
            double todayRealizedPnl,
            int openCount,
            double openCost,           // 보유 중 매수비용 합
            double evalPnl,            // 보유 중 평가손익(원)
            Double evalPnlRate
    ) {
    }

    // ---------- 도우미 ----------

    private static long startOfTodayKst() {
        return LocalDate.now(KST).atStartOfDay(KST).toInstant().toEpochMilli();
    }

    private static Number num(Object o) {
        return o instanceof Number n ? n : 0;
    }

    private static String price(double p) {
        return p >= 100 ? String.format(Locale.ROOT, "%,.0f", p) : String.format(Locale.ROOT, "%,.4f", p);
    }

    private static String qty(double q) {
        return String.format(Locale.ROOT, "%.6f", q);
    }
}
