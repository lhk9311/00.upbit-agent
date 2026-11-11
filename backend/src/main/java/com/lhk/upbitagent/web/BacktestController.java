package com.lhk.upbitagent.web;

import com.lhk.upbitagent.agent.AgentEngine;
import com.lhk.upbitagent.agent.Strategy;
import com.lhk.upbitagent.agent.StrategyRequest;
import com.lhk.upbitagent.backtest.BacktestEngine;
import com.lhk.upbitagent.domain.CandleUnit;
import com.lhk.upbitagent.market.MarketDataStore;
import com.lhk.upbitagent.store.StrategyRepository;
import com.lhk.upbitagent.upbit.UpbitDtos.UpbitMarket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 백테스트. 서버가 이미 모아둔 캔들(메모리 캐시)로 계산하므로 업비트를 추가로 호출하지 않는다.
 */
@RestController
@RequestMapping("/api")
public class BacktestController {

    private final MarketDataStore market;
    private final StrategyRepository strategies;
    private final BacktestEngine engine;

    public BacktestController(MarketDataStore market, StrategyRepository strategies,
                              @Value("${agent.fee-rate:0.0005}") double feeRate,
                              @Value("${agent.paper-slippage:0.001}") double slippage,
                              @Value("${agent.reentry-cooldown-minutes:60}") long cooldownMinutes) {
        this.market = market;
        this.strategies = strategies;
        this.engine = new BacktestEngine(new AgentEngine.Config(feeRate, slippage), cooldownMinutes * 60_000);
    }

    /** 저장하기 전 전략으로 백테스트 (전략 설정 창에서 사용) */
    @PostMapping("/backtest")
    public BacktestEngine.Result backtest(@RequestBody StrategyRequest req) {
        return run(req.toStrategy(null, System.currentTimeMillis()));
    }

    /** 저장된 전략으로 백테스트 */
    @PostMapping("/strategies/{id}/backtest")
    public BacktestEngine.Result backtestSaved(@PathVariable long id) {
        Strategy s = strategies.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("전략을 찾을 수 없어요: " + id));
        return run(s);
    }

    private BacktestEngine.Result run(Strategy s) {
        CandleUnit unit = s.condition().unitOrDefault();
        if (!market.isReady(unit)) {
            throw new IllegalStateException(unit.label() + " 데이터를 아직 모으는 중이에요. 잠시 후 다시 시도해주세요.");
        }

        Map<String, String> names = new HashMap<>();
        Set<String> warnings = new HashSet<>();
        for (UpbitMarket m : market.markets()) {
            names.put(m.market(), m.koreanName());
            if (m.warning()) {
                warnings.add(m.market());
            }
        }
        return engine.run(s, unit, market.candles(unit), names, warnings);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> notReady(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
    }
}
