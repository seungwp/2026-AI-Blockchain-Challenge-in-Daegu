package com.golmok.oneweek.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 실제 KAMIS API를 호출해 오늘 기준 가격이 그럴듯한 범위로 오는지 확인.
 * KAMIS_CERT_KEY/ID 환경변수가 없으면 건너뛴다.
 */
@EnabledIfEnvironmentVariable(named = "KAMIS_CERT_KEY", matches = ".+")
class KamisPriceProviderLiveTest {

    private KamisPriceProvider provider() throws Exception {
        KamisPriceProvider p = new KamisPriceProvider();
        set(p, "certKey", System.getenv("KAMIS_CERT_KEY"));
        set(p, "certId", System.getenv("KAMIS_CERT_ID"));
        return p;
    }

    private void set(Object target, String field, String value) throws Exception {
        Field f = KamisPriceProvider.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void 배추와_닭_가격이_최근_며칠_이내_날짜로_온다() throws Exception {
        Map<String, KamisPriceProvider.LivePrice> result = provider().fetchLatest(List.of("배추", "닭"));

        assertTrue(result.containsKey("배추"), "결과 키: " + result.keySet());
        assertTrue(result.containsKey("닭"), "결과 키: " + result.keySet());

        LocalDate today = LocalDate.now();
        for (var e : result.entrySet()) {
            var p = e.getValue();
            assertTrue(p.price() > 0, e.getKey() + " 가격이 0 이하");
            assertFalse(p.date().isAfter(today), e.getKey() + " 날짜가 미래");
            assertFalse(p.date().isBefore(today.minusDays(7)), e.getKey() + " 날짜가 너무 오래됨: " + p.date());
        }
    }

    @Test
    void 매핑에_없는_품목은_결과에서_빠진다() throws Exception {
        assertTrue(provider().fetchLatest(List.of("존재하지않는품목")).isEmpty());
    }
}
