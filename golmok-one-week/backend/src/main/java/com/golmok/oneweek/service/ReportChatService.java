package com.golmok.oneweek.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.golmok.oneweek.dto.ChatDtos.ChatMessage;
import com.golmok.oneweek.dto.ReportDtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 리포트 화면의 챗봇. "이 리포트에 있는 사실만" 근거로 사장님 질문에 답한다 — 대구 상권 전반이나
 * 일반 지식을 다루는 챗봇이 아니라 지금 보고 있는 리포트에 한정된 Q&A다.
 *
 * <p>절대 규칙(사용자 지시): 매출 증감률처럼 확정적인 숫자로 예측하는 답변은 절대 하지 않는다.
 * 예측을 요청받으면 "예측할 수 없다"고 답하도록 시스템 프롬프트에 명시하고, 숫자를 지어내면
 * {@link LlmNumberGuard}가 걸러 재시도한다. 챗봇은 사용자가 전송 버튼을 누르고 기다리는
 * 상호작용이라 {@link LlmAdviceService}(리포트 로딩을 막지 않기 위해 재시도 없음)와 달리
 * 검증 실패 시 피드백을 포함해 한 번 재시도한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportChatService {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_HISTORY = 6;
    private static final String CANNOT_ANSWER =
            "죄송합니다. 지금은 답변을 가져오지 못했습니다. 잠시 후 다시 시도해주세요.";
    private static final String NO_KEY_MESSAGE =
            "죄송합니다. 지금은 챗봇을 사용할 수 없습니다.";
    /** 성과 예측 질문. HCX-005는 금지해도 "주문이 늘어날 것" 같은 전망을 덧붙여서 LLM에 보내지 않고 고정 답을 준다. */
    private static final Pattern PERFORMANCE_QUESTION = Pattern.compile(
            "매출|손님\\s*수|손님.{0,6}(많|올|늘)|판매량|이익|수익|얼마나\\s*(벌|팔|오르|늘)|장사\\s*(잘|되)");
    /** 이 가게의 성과가 늘거나 줄 것이라는 전망 문장. 다른 질문의 답에 섞여 나오면 그 문장만 뺀다. */
    private static final Pattern FORECAST = Pattern.compile(
            "(늘어날|증가할|오를|많아질|줄어들|감소할)\\s*(것|가능성)|긍정적인\\s*(영향|결과)|매출.{0,10}(오를|늘|기대)");
    private static final String SYSTEM_PROMPT = """
            당신은 대구 골목상권 음식점 사장님의 질문에 쉬운 말로 답하는 도우미입니다.
            아래 [리포트 데이터]는 이번 주 리포트에 실제로 있는 사실입니다. 이 안에 있는 사실과 숫자만 사용해 답하세요.

            절대 하지 말아야 할 것:
            - "매출이 15% 늘 것입니다"처럼 매출 증감을 퍼센트나 금액으로 확정해서 말하는 것은 절대 금지입니다.
              매출을 예측해달라는 요청을 받으면 "정확한 매출 예측은 어렵습니다"라고 솔직히 답하고,
              대신 [리포트 데이터]에 있는 참고 자료(날씨·행사·경쟁 상황 등)를 안내하세요.
            - [리포트 데이터]에 없는 숫자·퍼센트·날짜·매출액을 새로 만들어 말하는 것.
            - [리포트 데이터]에 없는 사실을 지어내는 것. 모르면 "이 정보는 리포트에 없습니다"라고 답하세요.
            - JSON 키 이름이나 id를 그대로 문장에 쓰는 것. (예: '급등확인필요' 항목은 false 처럼 쓰지 말고
              "현재 급등 우려는 없습니다"처럼 자연스러운 문장으로 바꿔서 답하세요.)
            - 매출, 이익, 손님 수, 주문 수, 판매량, 효과처럼 미래 또는 성과의 구체적인 수치·퍼센트를
              묻는 질문에는 수치를 제시하거나 사용자가 말한 수치를 되풀이하지 마세요. "정확한 수치 예측은
              어렵습니다"라고 답한 뒤, 리포트에서 확인할 수 있는 날씨·행사·식자재·상권 근거를 안내하세요.
              단, 리포트에 명시된 과거·현재 사실(날씨, 행사 날짜, 식자재 가격, 거리)은 질문 의도에 맞게 설명할 수 있습니다.
            - 수치가 없더라도 "주문이 늘어날 것입니다", "긍정적인 영향을 미칠 것입니다"처럼 이 가게의 매출·주문·손님 수가
              늘거나 줄 것이라고 전망하는 것. 연구 결과는 "~한다는 분석이 있습니다"처럼 리포트 문장 그대로만 전하세요.
            - **, #, - 같은 마크다운 기호를 쓰는 것. 일반 문장으로만 답하세요.

            쉽고 친절한 말투로 2~4문장 이내로 답하세요.
            """;

    private final GroqChatClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public String ask(ReportResponse report, String question, List<ChatMessage> history) {
        if (!client.hasKey()) {
            log.info("LLM API 키가 없어 챗봇 응답을 건너뜁니다.");
            return NO_KEY_MESSAGE;
        }
        if (question != null && PERFORMANCE_QUESTION.matcher(question).find()) return performanceAnswer(report);
        String factsJson;
        try {
            factsJson = buildFacts(report);
        } catch (Exception e) {
            log.warn("챗봇용 리포트 데이터 구성 실패: {}", e.toString());
            return CANNOT_ANSWER;
        }

        List<ChatMessage> safeHistory = sanitize(history);
        // 사용자 질문에 든 임의 수치를 허용 목록에 넣지 않는다. "매출 20%" 같은 수치를 AI가
        // 그대로 인용해 확정적인 답처럼 보이는 일을 막고, 리포트와 과거 AI 답변의 검증된 수치만 허용한다.
        String allowedText = factsJson + " " + safeHistory.stream()
                .filter(message -> "assistant".equals(message.role()))
                .map(ChatMessage::content).reduce("", (a, b) -> a + " " + b);

        String reply = tryAsk(factsJson, question, safeHistory, null, allowedText);
        if (reply == null) {
            reply = tryAsk(factsJson, question, safeHistory,
                    "이전 답변에 리포트에 없는 숫자가 있었습니다. 숫자를 빼고, 리포트에 있는 사실만 사용해 다시 답해주세요.",
                    allowedText);
        }
        return reply != null ? reply : CANNOT_ANSWER;
    }

    private String tryAsk(String factsJson, String question, List<ChatMessage> history,
                          String correction, String allowedText) {
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT + "\n\n[리포트 데이터]\n" + factsJson));
            for (ChatMessage h : history) messages.add(Map.of("role", h.role(), "content", h.content()));
            messages.add(Map.of("role", "user", "content", question));
            if (correction != null) messages.add(Map.of("role", "system", "content", correction));

            String text = client.complete(messages, 0.3, 800, TIMEOUT);
            if (text == null || text.isBlank()) return null;
            text = dropForecasts(text.replace("**", "").strip());
            if (text == null) return null;
            return LlmNumberGuard.hasInventedNumber(allowedText, text) ? null : text;
        } catch (Exception e) {
            log.warn("챗봇 응답 생성 실패: {}", e.toString());
            return null;
        }
    }

    /** 고정 답: 예측은 하지 않고, 이번 주 처방 제목과 근거만 그대로 안내한다. */
    static String performanceAnswer(ReportResponse report) {
        List<Recommendation> actions = report.topActions() == null ? List.of() : report.topActions();
        if (actions.isEmpty()) {
            return "정확한 매출이나 손님 수는 예측하기 어렵습니다. 이번 주 리포트에는 특별히 점검할 항목이 없습니다.";
        }
        String items = actions.stream()
                .map(a -> a.basis() == null ? a.title() : a.title() + "(" + a.basis() + ")")
                .collect(Collectors.joining(", "));
        return "정확한 매출이나 손님 수는 예측하기 어렵습니다. 대신 이번 주에는 " + items
                + " 항목을 미리 확인해 보세요. 처방 카드의 근거보기에서 자세한 내용을 볼 수 있어요.";
    }

    /** 전망 문장만 빼고 나머지를 돌려준다. 남는 문장이 없으면 null(재시도 대상). */
    static String dropForecasts(String text) {
        String kept = Arrays.stream(text.split("(?<=[.!?])\\s+"))
                .filter(s -> !FORECAST.matcher(s).find())
                .collect(Collectors.joining(" "));
        return kept.isBlank() ? null : kept;
    }

    /** 클라이언트가 보낸 role을 신뢰하지 않는다 — user/assistant 외 값(예: system)은 전부 user로 강등한다. */
    private List<ChatMessage> sanitize(List<ChatMessage> history) {
        if (history == null) return List.of();
        return history.stream()
                .skip(Math.max(0, history.size() - MAX_HISTORY))
                .map(h -> new ChatMessage("assistant".equals(h.role()) ? "assistant" : "user", h.content()))
                .toList();
    }

    private String buildFacts(ReportResponse r) throws Exception {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("가게", r.store().name());
        facts.put("대표메뉴", r.mainMenu());
        facts.put("메뉴카테고리", r.menuCategory().label());
        facts.put("분석기간", r.analysisStartDate() + " ~ " + r.analysisEndDate());
        facts.put("이번주요약", r.summary());
        facts.put("핵심점검항목", r.topActions().stream()
                .map(a -> Map.of("제목", a.title(), "내용", a.text(), "근거", a.basis() == null ? "" : a.basis()))
                .toList());
        facts.put("날씨", r.weather().stream()
                .map(w -> Map.of("날짜", w.date() + "(" + w.dayOfWeek() + ")", "상태", String.valueOf(w.condition()),
                        "최고기온", String.valueOf(w.tempMax()), "최저기온", String.valueOf(w.tempMin()),
                        "강수확률", String.valueOf(w.precipitationProbability())))
                .toList());
        if (r.commercialArea() != null) facts.put("상권", r.commercialArea().note());
        facts.put("행사", r.festivals().stream()
                .map(f -> Map.of("이름", f.name(), "기간", f.startDate() + "~" + f.endDate(),
                        "거리", f.distanceMeters() == null ? "정보없음" : f.distanceMeters() + "m",
                        "영향구분", String.valueOf(f.impactLevel()), "행사시간", String.valueOf(f.playTime()),
                        "이용요금", String.valueOf(f.fee()), "확인일", String.valueOf(f.fetchedAt())))
                .toList());
        facts.put("식자재가격", r.ingredientPrices().stream()
                .map(p -> Map.of("품목", p.item(), "단위", String.valueOf(p.unit()), "가격", String.valueOf(p.price()),
                        "가격기준일", String.valueOf(p.priceDate()),
                        "일주일전비교일", String.valueOf(p.comparisonDate()),
                        "일주일전대비퍼센트", p.vsPreviousWeekRatio() == null ? "정보없음" : String.format(java.util.Locale.ROOT, "%.1f", p.vsPreviousWeekRatio() * 100),
                        "예측기준일", String.valueOf(p.predictionDate()),
                        "상태", p.predictionStale() ? "예측 갱신 대기: 현재 급등 여부 판단 불가"
                                : p.alert() ? "향후 급등 가능성이 있어 확인 필요" : "급등 알림 없음"))
                .toList());
        return mapper.writeValueAsString(facts);
    }
}
