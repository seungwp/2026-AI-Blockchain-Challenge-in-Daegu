package com.golmok.oneweek.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.golmok.oneweek.dto.ReportDtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 리포트 요약을 자연스러운 문장으로 다듬는 선택적 단계. {@link WeeklyGuideRuleEngine}이 만든
 * 결정적 요약({@code summary})은 그대로 두고, 같은 사실을 재료로 삼아 별도의 {@code aiSummary}를
 * 만든다 — 실패해도 화면에는 기존 summary 가 그대로 보이므로 이 단계가 없어도 리포트는 완전하다.
 *
 * <p>규칙(원칙 6, CLAUDE.md): LLM은 숫자를 계산하지 않는다. 입력 JSON에 없는 숫자가 출력에
 * 나오면 그 응답은 버리고 {@code null}을 돌려준다. 재시도는 하지 않는다 — 실패해도 리포트
 * 로딩을 막지 않기 위해 짧은 타임아웃 안에 한 번만 시도하고 실패하면 조용히 건너뛴다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmAdviceService {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String SYSTEM_PROMPT = """
            당신은 대구 골목상권 음식점 사장님에게 이번 주 운영 참고 요약을 3문장 이내로 써주는 도우미입니다.
            반드시 아래 JSON에 있는 사실과 숫자만 사용하세요. 새로운 숫자·퍼센트·날짜·매출액을 만들지 마세요.
            매출을 예측하거나 보장하는 표현("매출이 오릅니다", "얼마 벌 것으로 예상") 대신
            "점검을 권장합니다", "준비해두면 좋습니다"처럼 참고용 표현만 쓰세요.
            연구·분석 결과를 "이 가게 매출이 늘 수 있다"처럼 이 가게의 매출 전망으로 바꿔 말하지 마세요.
            JSON 키 이름이나 id를 문장에 그대로 쓰지 말고, 자연스러운 한국어 문장으로만 답하세요.
            설명 없이 요약 문장만 출력하세요.
            """;

    private final GroqChatClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 실패·타임아웃·검증 실패 시 항상 null. 이 값이 null이어도 리포트는 완전하다. */
    public String summarize(String mainMenu, String menuCategoryLabel, String weeklySummary,
                            List<Recommendation> topActions, CommercialArea area) {
        if (!client.hasKey()) {
            log.info("LLM API 키가 없어 AI 요약을 건너뜁니다.");
            return null;
        }
        try {
            String factsJson = buildFacts(mainMenu, menuCategoryLabel, weeklySummary, topActions, area);
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", factsJson));
            String text = client.complete(messages, 0.2, 600, TIMEOUT);
            if (text == null || text.isBlank()) return null;
            text = text.strip();
            return LlmNumberGuard.hasInventedNumber(factsJson, text) ? null : text;
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
}
