package com.golmok.oneweek.provider;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 특일정보 API가 돌려준 "추석" 날짜 목록을 전날/당일/연휴기간/마지막날로 분류.
 * 실제 2026년 값(9/24~9/26)을 정답으로 검증한다.
 */
class ChuseokClassifierTest {

    @SuppressWarnings("unchecked")
    private Map<LocalDate, String> classify(List<LocalDate> dates) throws Exception {
        Method m = ChuseokClassifier.class.getDeclaredMethod("classify", List.class);
        m.setAccessible(true);
        return (Map<LocalDate, String>) m.invoke(null, dates);
    }

    @Test
    void 표준_3일_연휴는_전날_당일_마지막날로_분류된다() throws Exception {
        // 2026년 실제 추석: getHoliDeInfo 응답 9/24, 9/25, 9/26
        var d = classify(List.of(
                LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26)));
        assertEquals("CHUSEOK_EVE", d.get(LocalDate.of(2026, 9, 24)));
        assertEquals("CHUSEOK_DAY", d.get(LocalDate.of(2026, 9, 25)));
        assertEquals("CHUSEOK_LAST", d.get(LocalDate.of(2026, 9, 26)));
    }

    @Test
    void 대체공휴일로_늘어난_연휴는_중간날을_기간으로_묶는다() throws Exception {
        var d = classify(List.of(
                LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25),
                LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 27)));
        assertEquals("CHUSEOK_EVE", d.get(LocalDate.of(2026, 9, 24)));
        assertEquals("CHUSEOK_PERIOD", d.get(LocalDate.of(2026, 9, 25)));
        assertEquals("CHUSEOK_PERIOD", d.get(LocalDate.of(2026, 9, 26)));
        assertEquals("CHUSEOK_LAST", d.get(LocalDate.of(2026, 9, 27)));
    }

    @Test
    void 순서가_뒤섞여_들어와도_정렬해서_분류한다() throws Exception {
        var d = classify(List.of(
                LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25)));
        assertEquals("CHUSEOK_EVE", d.get(LocalDate.of(2026, 9, 24)));
        assertEquals("CHUSEOK_LAST", d.get(LocalDate.of(2026, 9, 26)));
    }

    @Test
    void 빈_목록은_빈_지도를_돌려준다() throws Exception {
        assertTrue(classify(List.of()).isEmpty());
    }

    @Test
    void 하루뿐이면_당일로_처리한다() throws Exception {
        var d = classify(List.of(LocalDate.of(2026, 9, 25)));
        assertEquals("CHUSEOK_DAY", d.get(LocalDate.of(2026, 9, 25)));
    }
}
