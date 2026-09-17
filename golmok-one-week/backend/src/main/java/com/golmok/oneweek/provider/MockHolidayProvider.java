package com.golmok.oneweek.provider;

import com.golmok.oneweek.provider.Providers.HolidayProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/**
 * 명절 정보 없음. 빈 지도를 돌려주면 HOLIDAY 규칙이 그냥 발동하지 않아 안전한 기본값이 된다.
 * {@code golmok.providers.holiday=mock} 일 때만 쓰인다. KasiHolidayProvider 는 실패 시 이 클래스를 거치지 않고
 * 직접 빈 지도를 돌려준다.
 */
@Component
@ConditionalOnProperty(name = "golmok.providers.holiday", havingValue = "mock", matchIfMissing = true)
public class MockHolidayProvider implements HolidayProvider {

    @Override
    public Map<LocalDate, String> classify(LocalDate start, LocalDate end) {
        return Map.of();
    }
}
