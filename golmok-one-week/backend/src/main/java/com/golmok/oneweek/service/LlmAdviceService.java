package com.golmok.oneweek.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.golmok.oneweek.dto.ReportDtos.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 리포트 요약을 자연스러운 문장으로 다듬는 선택적 단계. {@link WeeklyGuideRuleEngine}이 만든
 * 결정적 요약({@code summary})은 그대로 두고, 같은 사실을 재료로 삼아 별도의 {@code aiSummary}를
 * 만든다 — 실패해도 화면에는 기존 summary 가 그대로 보이므로 이 단계가 없어도 리포트는 완전하다.
 *
 * <p>규칙(원칙 6, CLAUDE.md): LLM은 숫자를 계산하지 않는다. 입력 JSON에 없는 숫자가 출력에
 * 나오면 그 응답은 버리고 {@code null}을 돌려준다. 재시도는 하지 않는다 — NVIDIA Build 무료
 * 등급은 첫 응답까지 최대 2~3분 걸릴 수 있어(pipeline/make_advice.py 주석), 사용자 요청 경로에서는
 * 짧은 타임아웃 안에 한 번만 시도하고 실패하면 조용히 건너뛴다.
 */
@Service
@Slf4j
public class LlmAdviceService {

    private static final String URL = "https://integrate.api.nvidia.com/v1/chat/completions";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String SYSTEM_PROMPT = """
            당신은 대구 골목상권 음식점 사장님에게 이번 주 운영 참고 요약을 3문장 이내로 써주는 도우미입니다.
            반드시 아래 JSON에 있는 사실과 숫자만 사용하세요. 새로운 숫자·퍼센트·날짜·매출액을 만들지 마세요.
            매출을 예측하거나 보장하는 표현("매출이 오릅니다", "얼마 벌 것으로 예상") 대신
            "점검을 권장합니다", "준비해두면 좋습니다"처럼 참고용 표현만 쓰세요.
            JSON 키 이름이나 id를 문장에 그대로 쓰지 말고, 자연스러운 한국어 문장으로만 답하세요.
            설명 없이 요약 문장만 출력하세요.
            """;

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${golmok.keys.nvidia-api-key:}")
    private String apiKey;

    @Value("${golmok.keys.nvidia-model:nvidia/nemotron-3-super-120b-a12b}")
    private String model;

    /** 실패·타임아웃·검증 실패 시 항상 null. 이 값이 null이어도 리포트는 완전하다. */
    public String summarize(String mainMenu, String menuCategoryLabel, String weeklySummary,
                            List<Recommendation> topActions, CommercialArea area) {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("NVIDIA_API_KEY 가 없어 AI 요약을 건너뜁니다.");
            return null;
        }
        try {
            String factsJson = buildFacts(mainMenu, menuCategoryLabel, weeklySummary, topActions, area);
            String text = call(factsJson);
            if (text == null || text.isBlank()) return null;
            text = text.strip();
            return hasInventedNumber(factsJson, text) ? null : text;
        } catch (Exception e) {
            log.warn("AI 요약 생성 실패, 건너뜁니다: {}", e.toString());
            return null;
        }
    }

    private String buildFacts(String mainMenu, String menuCategoryLabel, String weeklySummary,
                              List<Recommendation> topActions, CommercialArea area) throws Exception {
        var actions = topActions.stream()
                .map(a -> Map.of("제목", a.title(), "내용", a.text(), "근거", a.basis() == null ? "" : a.basis()))
                .toList();
        Map<String, Object> facts = new java.util.LinkedHashMap<>();
        facts.put("대표메뉴", mainMenu);
        facts.put("메뉴카테고리", menuCategoryLabel);
        facts.put("이번주요약", weeklySummary);
        facts.put("핵심점검항목", actions);
        if (area != null) facts.put("상권메모", area.note());
        return mapper.writeValueAsString(facts);
    }

    private String call(String factsJson) throws Exception {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.2,
                "max_tokens", 300,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", factsJson)));

        HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) throw new IllegalStateException("NVIDIA API HTTP " + res.statusCode());

        JsonNode root = mapper.readTree(res.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        return content.isMissingNode() ? null : content.asText();
    }

    /**
     * 응답에 등장하는 숫자가 입력 JSON에 없던 숫자면 지어낸 값으로 간주한다.
     * 반올림 오차 허용 0.51 (pipeline/make_advice.py 의 검증 기준과 동일).
     * ponytail: 파이프라인처럼 재생성을 시도하지 않고 바로 폐기한다 — 요청 경로 지연을 늘리지 않기 위함.
     */
    static boolean hasInventedNumber(String factsJson, String text) {
        var allowed = extractNumbers(factsJson);
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            double v = Double.parseDouble(m.group());
            boolean ok = allowed.stream().anyMatch(a -> Math.abs(a - v) < 0.51);
            if (!ok) return true;
        }
        return false;
    }

    /**
     * "2026-09-18" 같은 날짜의 하이픈이 마이너스 부호로 잘못 붙어 -9, -18 로 추출될 수 있어
     * 절댓값도 함께 허용 목록에 넣는다(pipeline/make_advice.py allowed_numbers() 와 동일한 처리).
     */
    private static List<Double> extractNumbers(String json) {
        List<Double> out = new java.util.ArrayList<>();
        Matcher m = NUMBER.matcher(json);
        while (m.find()) {
            double v = Double.parseDouble(m.group());
            out.add(v);
            out.add(Math.abs(v));
        }
        return out;
    }
}
