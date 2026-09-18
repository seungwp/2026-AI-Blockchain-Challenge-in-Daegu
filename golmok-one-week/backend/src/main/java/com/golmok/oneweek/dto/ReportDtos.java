package com.golmok.oneweek.dto;

import com.golmok.oneweek.entity.Enums.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 리포트 관련 요청·응답 DTO 모음. */
public final class ReportDtos {
    private ReportDtos() {}

    public record CreateRequest(
            @NotNull(message = "가게를 선택해주세요.") Long storeId,
            @NotBlank(message = "대표 메뉴를 입력해주세요.") @Size(max = 100) String mainMenu,
            MenuCategory menuCategory) {}

    /** 하루치 날씨 (예보). */
    public record WeatherDay(LocalDate date, String dayOfWeek, String condition, Double tempMax, Double tempMin,
                             Integer precipitationProbability, Double precipitationMm, Integer humidity,
                             boolean isDemoData, Long sourceId) {}

    /** 주변 상권 요약. */
    public record CommercialArea(String district, String dong, Integer totalStores, Integer sameCategoryStores,
                                 String competitionLevel, String note, boolean isDemoData, List<Long> sourceIds) {}

    /** 행사 정보 + 가게와의 거리·영향 구분. */
    public record FestivalInfo(Long id, String name, LocalDate startDate, LocalDate endDate, String locationName,
                               String address, Double latitude, Double longitude, Integer distanceMeters,
                               String impactLevel, String impactNote, boolean isDemoData, Long sourceId,
                               String playTime, String fee, String contact, LocalDate fetchedAt) {
        public FestivalInfo(Long id, String name, LocalDate startDate, LocalDate endDate, String locationName,
                            String address, Double latitude, Double longitude, Integer distanceMeters,
                            String impactLevel, String impactNote, boolean isDemoData, Long sourceId) {
            this(id, name, startDate, endDate, locationName, address, latitude, longitude, distanceMeters,
                    impactLevel, impactNote, isDemoData, sourceId, null, null, null, null);
        }
    }

    /** 운영 권고 1건. 매출 수치가 아니라 '점검/준비/고려' 수준의 행동. */
    public record Recommendation(String title, String text, RecommendationType type, Priority priority,
                                 Confidence confidence, ConditionType conditionType, String basis,
                                 LocalDate date, List<Long> sourceIds) {}

    /** 요일별 운영 가이드. */
    public record DailyGuide(LocalDate date, String dayOfWeek, String weatherSummary, List<Recommendation> guides) {}

    /** 식자재 참고 가격 1건. price·vsNormalRatio 는 실측치, probSpike 는 모델 추정값(정밀도 낮음, 확인용). */
    public record PricePoint(LocalDate date, Double price) {}

    public record IngredientPriceInfo(String item, String unit, Double price, LocalDate priceDate,
                                      Double probSpike, boolean alert, Double vsNormalRatio,
                                      boolean isDemoData, Long sourceId, List<PricePoint> history,
                                      LocalDate comparisonDate, Double vsPreviousWeekRatio,
                                      LocalDate predictionDate, boolean predictionStale, String priceBasis) {
        public IngredientPriceInfo(String item, String unit, Double price, LocalDate priceDate,
                                   Double probSpike, boolean alert, Double vsNormalRatio,
                                   boolean isDemoData, Long sourceId) {
            this(item, unit, price, priceDate, probSpike, alert, vsNormalRatio, isDemoData, sourceId,
                    List.of(), null, null, null, true, null);
        }
    }

    public record ReportResponse(Long reportId, StoreResponse store, String mainMenu, MenuCategory menuCategory,
                                 LocalDate analysisStartDate, LocalDate analysisEndDate, String summary,
                                 String aiSummary, List<Recommendation> topActions, List<WeatherDay> weather,
                                 CommercialArea commercialArea, List<FestivalInfo> festivals,
                                 List<IngredientPriceInfo> ingredientPrices, List<DailyGuide> dailyGuides,
                                 List<SourceResponse> sources,
                                 boolean isDemoData, String demoNotice, String disclaimer) {}
}
