package com.golmok.oneweek.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 응답에 입력 JSON(사실 근거)에 없는 숫자가 나오면 지어낸 값으로 간주한다(원칙 6: LLM은 숫자를
 * 계산하지 않는다). {@link LlmAdviceService}·{@link ReportChatService} 양쪽에서 공용으로 쓴다.
 */
final class LlmNumberGuard {
    private LlmNumberGuard() {}

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    /** 반올림 오차 허용 0.51 (pipeline/make_advice.py 의 검증 기준과 동일). */
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
        List<Double> out = new ArrayList<>();
        Matcher m = NUMBER.matcher(json);
        while (m.find()) {
            double v = Double.parseDouble(m.group());
            out.add(v);
            out.add(Math.abs(v));
        }
        return out;
    }
}
