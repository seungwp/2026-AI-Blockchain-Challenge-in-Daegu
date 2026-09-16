package com.golmok.oneweek.service;

/**
 * Java 코드에서 직접 참조하는 출처 ID.
 * 전체 목록은 resources/data/sources.csv 이고, 번호는 docs/coefficients.md 의 출처 번호와 같다.
 * 규칙의 출처는 resources/data/menu_rules.csv 의 sourceId 컬럼에 숫자로 들어간다.
 */
public final class SourceCatalog {
    private SourceCatalog() {}

    public static final Long SBIZ_COMMERCIAL_ID = 1L;   // 소상공인시장진흥공단 상가(상권)정보
    public static final Long WEATHER_API_ID = 6L;       // 기상청 기상자료개방포털
    public static final Long FESTIVAL_TOURAPI_ID = 13L; // 한국관광공사 TourAPI 축제 정보
}
