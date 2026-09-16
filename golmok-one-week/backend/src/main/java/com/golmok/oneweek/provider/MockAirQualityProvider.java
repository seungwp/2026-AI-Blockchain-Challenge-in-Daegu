package com.golmok.oneweek.provider;

import com.golmok.oneweek.provider.Providers.AirQualityProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 예시 미세먼지 등급. 날씨 Provider 를 실제 API 로 바꿔도 대기질은 따로 교체할 수 있도록 분리했다.
 * 미세먼지는 매출 근거로 쓰지 않는다(docs/coefficients.md 5-2, 성은영 2017 유의하지 않음).
 */
@Component
@ConditionalOnProperty(name = "golmok.providers.air-quality", havingValue = "mock", matchIfMissing = true)
public class MockAirQualityProvider implements AirQualityProvider {

    private static final List<String> WEEK =
            List.of("보통", "나쁨", "좋음", "좋음", "보통", "보통", "나쁨");

    @Override
    public List<String> pm10Grades(double latitude, double longitude, LocalDate start, int days) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < days; i++) out.add(WEEK.get(i % WEEK.size()));
        return out;
    }
}
