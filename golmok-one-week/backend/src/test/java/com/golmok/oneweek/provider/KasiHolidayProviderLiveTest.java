package com.golmok.oneweek.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 실제 특일정보 API를 호출해 2026년 추석(9/24~9/26)이 올바르게 분류되는지 확인.
 * DATA_GO_KR_KEY 환경변수가 없으면 건너뛴다(CI·키 없는 환경 보호).
 */
@EnabledIfEnvironmentVariable(named = "DATA_GO_KR_KEY", matches = ".+")
class KasiHolidayProviderLiveTest {

    @Test
    void 실제_2026_추석이_전날_당일_마지막날로_온다() throws Exception {
        KasiHolidayProvider provider = new KasiHolidayProvider();
        Field f = KasiHolidayProvider.class.getDeclaredField("serviceKey");
        f.setAccessible(true);
        f.set(provider, System.getenv("DATA_GO_KR_KEY"));

        Map<LocalDate, String> result = provider.classify(
                LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 30));

        assertEquals("CHUSEOK_EVE", result.get(LocalDate.of(2026, 9, 24)));
        assertEquals("CHUSEOK_DAY", result.get(LocalDate.of(2026, 9, 25)));
        assertEquals("CHUSEOK_LAST", result.get(LocalDate.of(2026, 9, 26)));
        assertNull(result.get(LocalDate.of(2026, 9, 23)));
        assertNull(result.get(LocalDate.of(2026, 9, 27)));
    }
}
