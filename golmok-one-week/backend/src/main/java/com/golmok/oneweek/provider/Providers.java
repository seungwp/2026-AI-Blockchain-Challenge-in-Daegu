package com.golmok.oneweek.provider;

import com.golmok.oneweek.dto.ReportDtos.FestivalInfo;
import com.golmok.oneweek.dto.ReportDtos.WeatherDay;
import com.golmok.oneweek.entity.Store;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 외부 데이터 연동 지점. MVP는 Mock 구현을 쓰고, 실제 API는 Adapter 클래스를 추가해
 * application.yml 의 golmok.providers.* 값으로 교체한다 (키는 환경변수로만 주입).
 */
public final class Providers {
    private Providers() {}

    public interface StoreSearchProvider {
        /** 상호명 또는 주소 키워드로 가게 후보 검색. */
        List<Store> search(String keyword, String city);
        /** 검색 결과가 없을 때 사용자가 입력한 주소로 임시 가게를 만든다. */
        Store fromAddress(String address, String city);
    }

    public interface WeatherProvider {
        /** 기준일부터 days일치 일별 예보. */
        List<WeatherDay> weekly(double latitude, double longitude, LocalDate start, int days);
    }

    public interface FestivalProvider {
        /** 기간과 겹치는 행사 목록 (거리 계산 전). */
        List<FestivalInfo> findFestivals(LocalDate start, LocalDate end);
    }

    public interface HolidayProvider {
        /**
         * 기간 내 추석 관련 날짜를 분류해 돌려준다. 값은 "CHUSEOK_EVE"(전날), "CHUSEOK_DAY"(당일),
         * "CHUSEOK_PERIOD"(연휴 중 그 외), "CHUSEOK_LAST"(연휴 마지막날) 중 하나.
         * 노진원 외(2019) 계수가 추석에 한정되므로 설날 등 다른 명절은 분류하지 않는다
         * (docs/coefficients.md 원칙 5: 단일 사례를 다른 명절로 확장하지 않는다).
         */
        Map<LocalDate, String> classify(LocalDate start, LocalDate end);
    }
}
