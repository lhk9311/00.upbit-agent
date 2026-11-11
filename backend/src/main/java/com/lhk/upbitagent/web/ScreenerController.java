package com.lhk.upbitagent.web;

import com.lhk.upbitagent.domain.CandleUnit;
import com.lhk.upbitagent.market.MarketDataStore;
import com.lhk.upbitagent.nl.ClaudeConditionParser;
import com.lhk.upbitagent.nl.ClaudeConditionParser.NlParseException;
import com.lhk.upbitagent.nl.NlConditionMapper;
import com.lhk.upbitagent.screener.ScreenerCondition;
import com.lhk.upbitagent.screener.ScreenerEngine;
import com.lhk.upbitagent.screener.ScreenerResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ScreenerController {

    private final MarketDataStore store;
    private final ClaudeConditionParser parser;
    private final ScreenerEngine engine = new ScreenerEngine();

    public ScreenerController(MarketDataStore store, ClaudeConditionParser parser) {
        this.store = store;
        this.parser = parser;
    }

    /** 조건 검색. 켜진 조건 모두 통과한 종목 중 정렬 기준 상위 최대 10개 */
    @PostMapping("/screener")
    public ResponseEntity<?> screen(@RequestBody ScreenerCondition condition) {
        CandleUnit unit = condition.unitOrDefault();

        if (!store.isReady(unit)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "message", unit.label() + " 데이터를 수집하는 중이에요. 서버 시작 후 1~2분 뒤 다시 시도해주세요."));
        }

        ScreenerResult result = engine.evaluate(store.snapshots(unit), condition)
                .withCacheUpdatedAt(store.candleUpdatedAt(unit));
        return ResponseEntity.ok(result);
    }

    /**
     * 자연어 → 조건 변환. 검색은 하지 않고 조건만 돌려준다.
     * 화면이 이 조건을 패널에 채워 보여준 뒤 /screener 로 검색한다 (사람이 해석을 확인할 수 있게).
     */
    @PostMapping("/screener/parse")
    public NlConditionMapper.Result parse(@RequestBody ParseRequest request) {
        return parser.parse(request.text());
    }

    /** 자연어 검색 사용 가능 여부 (API 키 설정 여부) */
    @GetMapping("/screener/parse/status")
    public Map<String, Object> parseStatus() {
        return Map.of("enabled", parser.isConfigured());
    }

    /** 수집 현황 */
    @GetMapping("/collector/status")
    public Map<String, Object> status() {
        return store.status();
    }

    @ExceptionHandler(NlParseException.class)
    public ResponseEntity<Map<String, String>> handleParse(NlParseException e) {
        return ResponseEntity.status(e.status()).body(Map.of("message", e.getMessage()));
    }

    public record ParseRequest(String text) {
    }
}
