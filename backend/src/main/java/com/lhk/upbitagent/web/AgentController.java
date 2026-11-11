package com.lhk.upbitagent.web;

import com.lhk.upbitagent.agent.AgentService;
import com.lhk.upbitagent.agent.Strategy;
import com.lhk.upbitagent.agent.StrategyRequest;
import com.lhk.upbitagent.agent.Trade;
import com.lhk.upbitagent.agent.TradeStatus;
import com.lhk.upbitagent.store.AgentLogRepository;
import com.lhk.upbitagent.store.StrategyRepository;
import com.lhk.upbitagent.store.TradeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AgentController {

    private final StrategyRepository strategies;
    private final TradeRepository trades;
    private final AgentLogRepository logs;
    private final AgentService agent;

    public AgentController(StrategyRepository strategies, TradeRepository trades,
                           AgentLogRepository logs, AgentService agent) {
        this.strategies = strategies;
        this.trades = trades;
        this.logs = logs;
        this.agent = agent;
    }

    // ---------- 전략 ----------

    @GetMapping("/strategies")
    public List<Strategy> listStrategies() {
        return strategies.findAll();
    }

    /** 전략 저장 (기본: 바로 시작). 저장 직후 한 주기를 돌려 첫 매수를 바로 확인할 수 있게 한다 */
    @PostMapping("/strategies")
    public Strategy createStrategy(@RequestBody StrategyRequest req) {
        Strategy saved = strategies.insert(req.toStrategy(null, System.currentTimeMillis()));
        logs.add(saved.id(), "INFO", "[" + saved.name() + "] 전략 저장" + (saved.enabled() ? " · 시작" : ""));
        if (saved.enabled()) {
            agent.runCycle();
        }
        return saved;
    }

    @PutMapping("/strategies/{id}")
    public Strategy updateStrategy(@PathVariable long id, @RequestBody StrategyRequest req) {
        Strategy old = findStrategy(id);
        strategies.update(req.toStrategy(id, old.createdAt()));
        logs.add(id, "INFO", "[" + req.name() + "] 전략 수정");
        return findStrategy(id);
    }

    @PostMapping("/strategies/{id}/start")
    public Strategy start(@PathVariable long id) {
        Strategy s = findStrategy(id);
        strategies.setEnabled(id, true);
        logs.add(id, "INFO", "[" + s.name() + "] 시작");
        agent.runCycle();
        return findStrategy(id);
    }

    /** 정지: 신규 매수만 멈추고, 보유 중인 종목은 매도 규칙대로 계속 관리 */
    @PostMapping("/strategies/{id}/stop")
    public Strategy stop(@PathVariable long id) {
        Strategy s = findStrategy(id);
        strategies.setEnabled(id, false);
        logs.add(id, "INFO", "[" + s.name() + "] 정지 (보유 종목은 매도 규칙대로 계속 관리)");
        return findStrategy(id);
    }

    @DeleteMapping("/strategies/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        Strategy s = findStrategy(id);
        if (!trades.findOpenByStrategy(id).isEmpty()) {
            throw new IllegalArgumentException("보유 중인 종목이 있어 삭제할 수 없어요. 먼저 매도하거나 정지 후 정리해주세요.");
        }
        strategies.softDelete(id);
        logs.add(id, "INFO", "[" + s.name() + "] 삭제");
        return ResponseEntity.noContent().build();
    }

    // ---------- 포지션·거래 ----------

    /** 진행 중 거래 + 현재가 기준 평가손익 */
    @GetMapping("/positions")
    public List<AgentService.Position> positions() {
        return agent.positions();
    }

    /** 수동 매도 */
    @PostMapping("/trades/{id}/close")
    public Trade close(@PathVariable long id) {
        return agent.closeManually(id);
    }

    /**
     * 투자 히스토리.
     * status: OPEN | CLOSED | (생략=전체), sort: buyAt | sellAt | pnlRate | pnl | market | buyPrice | sellPrice
     * from/to: 매수 시각 범위(epoch ms)
     */
    @GetMapping("/trades")
    public List<Trade> trades(@RequestParam(required = false) TradeStatus status,
                              @RequestParam(required = false) Long strategyId,
                              @RequestParam(required = false) Long from,
                              @RequestParam(required = false) Long to,
                              @RequestParam(defaultValue = "buyAt") String sort,
                              @RequestParam(defaultValue = "desc") String dir,
                              @RequestParam(defaultValue = "300") int limit) {
        return trades.search(new TradeRepository.TradeSearch(status, strategyId, from, to, sort, dir, limit));
    }

    // ---------- 에이전트 ----------

    @GetMapping("/agent/summary")
    public AgentService.Summary summary() {
        return agent.summary();
    }

    @GetMapping("/agent/logs")
    public List<AgentLogRepository.AgentLog> agentLogs(@RequestParam(defaultValue = "50") int limit) {
        return logs.recent(limit);
    }

    /** 다음 주기를 기다리지 않고 바로 한 번 실행 (테스트용) */
    @PostMapping("/agent/run")
    public Map<String, String> runNow() {
        agent.runCycle();
        return Map.of("message", "한 주기를 실행했어요");
    }

    // ---------- 에러 ----------

    private Strategy findStrategy(long id) {
        return strategies.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("전략을 찾을 수 없어요: " + id));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
    }
}
