package com.golmok.oneweek.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLM 응답에 입력 JSON에 없는 숫자가 있으면 지어낸 값으로 간주해 버려야 한다(원칙 6).
 * 네트워크 호출 없이 순수 검증 로직({@link LlmNumberGuard})만 테스트한다.
 */
class LlmAdviceServiceTest {

    private static final String FACTS =
            "{\"이번주요약\":\"이번 주는 비 예보 2일 조건입니다\",\"핵심점검항목\":[" +
            "{\"제목\":\"비 예보\",\"근거\":\"2026-09-18(토) 강수확률 80%\"}]}";

    @Test
    void 입력에_있는_숫자만_쓰면_통과한다() {
        String text = "토요일 강수확률이 80%로 높으니 포장 준비를 점검해보세요.";
        assertFalse(LlmNumberGuard.hasInventedNumber(FACTS, text));
    }

    @Test
    void 입력에_없는_숫자를_지어내면_걸러진다() {
        String text = "이번 주 매출이 15% 오를 것으로 예상됩니다.";
        assertTrue(LlmNumberGuard.hasInventedNumber(FACTS, text));
    }

    @Test
    void 반올림_오차_0_51_이내는_허용한다() {
        String text = "강수확률이 80.3%로 높습니다.";
        assertFalse(LlmNumberGuard.hasInventedNumber(FACTS, text));
    }

    @Test
    void 날짜에_포함된_숫자도_입력에_있으면_통과한다() {
        String text = "9월 18일 토요일에는 비 예보가 있습니다.";
        assertFalse(LlmNumberGuard.hasInventedNumber(FACTS, text));
    }

    @Test
    void 숫자가_아예_없으면_항상_통과한다() {
        assertFalse(LlmNumberGuard.hasInventedNumber(FACTS, "특별한 조건 없이 평소처럼 운영하시면 됩니다."));
    }
}
