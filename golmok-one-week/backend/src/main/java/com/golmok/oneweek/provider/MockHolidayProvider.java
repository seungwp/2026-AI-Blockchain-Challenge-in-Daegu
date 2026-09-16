package com.golmok.oneweek.provider;

import com.golmok.oneweek.provider.Providers.HolidayProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/**
 * 명절 정보 없음. 빈 지도를 돌려주면 HOLIDAY 규칙이 그냥 발동하지 않아 안전한 기본값이 된다.
 * KasiHolidayProvider 가 API 호출에 실패했을 때의 폴백으로도 쓰인다.
 */
@Component
@ConditionalOnProperty(name = "golmok.providers.holiday", havingValue = "mock", matchIfMissing = true)
public class MockHolidayProvider implements HolidayProvider {

    @Override
    public Map<LocalDate, String> classify(LocalDate start, LocalDate end) {
        return Map.of();
    }
}
