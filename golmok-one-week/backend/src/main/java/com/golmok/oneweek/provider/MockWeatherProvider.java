package com.golmok.oneweek.provider;

import com.golmok.oneweek.dto.ReportDtos.WeatherDay;
import com.golmok.oneweek.provider.Providers.WeatherProvider;
import com.golmok.oneweek.service.SourceCatalog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 예시 날씨. 외부 API 없이도 전체 흐름이 동작하도록 7일치를 고정 패턴으로 만든다.
 * 맑음·비·흐림·고온이 모두 포함된다. (기준일 기준 결정적이라 새로고침해도 동일)
 * KmaWeatherProvider 가 API 호출에 실패했을 때의 폴백으로도 쓰인다.
 */
@Component
@ConditionalOnProperty(name = "golmok.providers.weather", havingValue = "mock", matchIfMissing = true)
public class MockWeatherProvider implements WeatherProvider {

    private record Pattern(String condition, double tMax, double tMin, int pop, double mm, int reh) {}

    private static final List<Pattern> WEEK = List.of(
            new Pattern("맑음", 27.0, 18.0, 10, 0.0, 55),
            new Pattern("흐림", 25.0, 19.0, 30, 0.0, 70),
            new Pattern("비", 22.0, 19.0, 80, 12.5, 90),
            new Pattern("비", 23.0, 19.5, 60, 5.0, 85),
            new Pattern("맑음", 31.0, 21.0, 10, 0.0, 50),
            new Pattern("맑음", 33.5, 23.0, 0, 0.0, 45),
            new Pattern("흐림", 29.0, 20.0, 20, 0.0, 65));

    static final String[] DOW = {"월", "화", "수", "목", "금", "토", "일"};

    @Override
    public List<WeatherDay> weekly(double latitude, double longitude, LocalDate start, int days) {
        List<WeatherDay> out = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = start.plusDays(i);
            Pattern p = WEEK.get(i % WEEK.size());
            out.add(new WeatherDay(d, DOW[d.getDayOfWeek().getValue() - 1], p.condition(), p.tMax(), p.tMin(),
                    p.pop(), p.mm(), p.reh(), true, SourceCatalog.WEATHER_API_ID));
        }
        return out;
    }
}
