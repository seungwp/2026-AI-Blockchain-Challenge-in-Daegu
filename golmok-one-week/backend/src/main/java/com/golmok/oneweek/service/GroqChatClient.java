package com.golmok.oneweek.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 호환 chat completion 호출. 기본은 Groq, {@code golmok.llm.url}로 CLOVA Studio(HyperCLOVA X) 등으로 바꾼다.
 * {@link LlmAdviceService}·{@link ReportChatService}·{@link MenuClassificationService} 공용.
 */
@Component
class GroqChatClient {

    @Value("${golmok.llm.url:https://api.groq.com/openai/v1/chat/completions}")
    private String url;

    @Value("${golmok.llm.reasoning-effort:low}")
    private String reasoningEffort;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${golmok.keys.groq-api-key:}")
    private String apiKey;

    @Value("${golmok.keys.groq-model:openai/gpt-oss-120b}")
    private String model;

    boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    String complete(List<Map<String, String>> messages, double temperature, int maxTokens, Duration timeout)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(Map.of(
                "model", model,
                "temperature", temperature,
                "max_tokens", maxTokens,
                "messages", messages));
        // gpt-oss는 추론 모델이라 기본 추론량이면 max_tokens를 추론에 다 써서 답이 잘린다 → low로 제한
        if (reasoningEffort != null && !reasoningEffort.isBlank()) body.put("reasoning_effort", reasoningEffort);

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) throw new IllegalStateException("LLM API HTTP " + res.statusCode());

        JsonNode choice = mapper.readTree(res.body()).path("choices").path(0);
        // 토큰 한도로 잘린 문장("이번 주 인근 대구메이커페스타(")을 화면에 내보내지 않는다
        if (!"stop".equals(choice.path("finish_reason").asText())) return null;
        JsonNode content = choice.path("message").path("content");
        return content.isMissingNode() ? null : content.asText();
    }
}
