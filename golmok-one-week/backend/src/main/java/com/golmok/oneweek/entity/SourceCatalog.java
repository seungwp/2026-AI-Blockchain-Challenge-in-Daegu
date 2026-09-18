package com.golmok.oneweek.entity;

/**
 * Java 코드에서 직접 참조하는 출처 ID.
 * 전체 목록은 resources/data/sources.csv 이고, 번호는 docs/coefficients.md 의 출처 번호와 같다.
 * 규칙의 출처는 resources/data/menu_rules.csv 의 sourceId 컬럼에 숫자로 들어간다.
 */
public final class SourceCatalog {
    private SourceCatalog() {}

    public static final Long STORE_LICENSE_ID = 1L;    // 행정안전부 식품_일반음식점 인허가 정보
    public static final Long WEATHER_API_ID = 6L;       // 기상청 기상자료개방포털
    public static final Long FESTIVAL_TOURAPI_ID = 13L; // 한국관광공사 TourAPI 축제 정보
    public static final Long KAMIS_PRICE_ID = 14L;      // KAMIS 대구 소매가격 및 급등확률 모델
    public static final Long SBIZ_STORE_ID = 15L;       // 상가정보 세부 업종 (보강에만 사용)
}
