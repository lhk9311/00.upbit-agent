package com.lhk.upbitagent.nl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 자연어 검색 조건 → ScreenerCondition 변환 (Claude API, 구조화된 출력 사용).
 *
 * 구조화된 출력(output_config.format = json_schema)을 쓰면 응답이 스키마 모양을 따르도록 보장된다.
 * 프롬프트·스키마는 resources/nl/ 아래 파일로 분리해 코드 수정 없이 다듬을 수 있게 했다.
 * 참고: https://platform.claude.com/docs/en/build-with-claude/structured-outputs
 */
@Service
public class ClaudeConditionParser {

    private static final Logger log = LoggerFactory.getLogger(ClaudeConditionParser.class);

    public static final int MAX_INPUT_LENGTH = 500;

    private final ObjectMapper objectMapper;
    private final RestClient rest;
    private final String apiKey;
    private final String model;
    private final String systemPrompt;
    private final JsonNode schema;

    public ClaudeConditionParser(ObjectMapper objectMapper,
                                 @Value("${anthropic.api-key:}") String apiKey,
                                 @Value("${anthropic.model:claude-haiku-5-5}") String model,
                                 @Value("${anthropic.base-url:https://api.anthropic.com}") String baseUrl) throws IOException {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.rest = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
        this.systemPrompt = new ClassPathResource("nl/condition-prompt.txt").getContentAsString(StandardCharsets.UTF_8);
        this.schema = objectMapper.readTree(new ClassPathResource("nl/condition-schema.json").getInputStream());
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public NlConditionMapper.Result parse(String text) {
        if (!isConfigured()) {
            throw new NlParseException(503,
                    "자연어 검색을 쓰려면 Claude API 키가 필요해요. 환경변수 ANTHROPIC_API_KEY 를 설정하고 서버를 다시 켜주세요.");
        }
        if (text == null || text.isBlank()) {
            throw new NlParseException(400, "검색 조건을 문장으로 입력해주세요.");
        }
        if (text.length() > MAX_INPUT_LENGTH) {
            throw new NlParseException(400, "조건 문장은 " + MAX_INPUT_LENGTH + "자 이내로 입력해주세요.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 1024);
        body.put("system", systemPrompt);
        body.put("messages", List.of(Map.of("role", "user", "content", text.trim())));
        body.put("output_config", Map.of("format", Map.of("type", "json_schema", "schema", schema)));

        JsonNode response;
        try {
            response = rest.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            log.warn("Claude API 오류 {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            int code = e.getStatusCode().value();
            if (code == 401 || code == 403) {
                throw new NlParseException(503, "Claude API 키가 올바르지 않아요. ANTHROPIC_API_KEY 를 확인해주세요.");
            }
            if (code == 429) {
                throw new NlParseException(429, "Claude API 요청이 많아요. 잠시 후 다시 시도해주세요.");
            }
            throw new NlParseException(502, "조건 변환 요청이 거절됐어요 (HTTP " + code + ").");
        } catch (HttpServerErrorException e) {
            log.warn("Claude API 서버 오류 {}", e.getStatusCode());
            throw new NlParseException(502, "Claude API 가 일시적으로 응답하지 않아요. 잠시 후 다시 시도해주세요.");
        } catch (ResourceAccessException e) {
            log.warn("Claude API 연결 실패: {}", e.getMessage());
            throw new NlParseException(502, "Claude API 에 연결하지 못했어요. 네트워크를 확인해주세요.");
        }

        if (response == null) {
            throw new NlParseException(502, "Claude API 응답이 비어 있어요.");
        }

        String stopReason = response.path("stop_reason").asText("");
        if ("refusal".equals(stopReason)) {
            throw new NlParseException(422, "이 문장은 조건으로 바꿀 수 없어요. 다른 표현으로 입력해주세요.");
        }
        if ("max_tokens".equals(stopReason)) {
            throw new NlParseException(502, "응답이 중간에 잘렸어요. 문장을 조금 짧게 입력해주세요.");
        }

        String json = null;
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                json = block.path("text").asText();
                break;
            }
        }
        if (json == null) {
            throw new NlParseException(502, "Claude API 응답에서 조건을 찾지 못했어요.");
        }

        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {
            });
            return NlConditionMapper.map(raw);
        } catch (IOException e) {
            log.warn("조건 JSON 파싱 실패: {}", json);
            throw new NlParseException(502, "변환 결과를 읽지 못했어요. 다시 시도해주세요.");
        }
    }

    /** 화면에 그대로 보여줄 메시지와 HTTP 상태 */
    public static class NlParseException extends RuntimeException {
        private final int status;

        public NlParseException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
