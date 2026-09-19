package com.golmok.oneweek.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.golmok.oneweek.dto.ReportDtos.*;
import com.golmok.oneweek.dto.ChatDtos.*;
import com.golmok.oneweek.dto.SourceResponse;
import com.golmok.oneweek.dto.StoreResponse;
import com.golmok.oneweek.entity.AnalysisReport;
import com.golmok.oneweek.entity.Enums.MenuCategory;
import com.golmok.oneweek.entity.Store;
import com.golmok.oneweek.exception.NotFoundException;
import com.golmok.oneweek.provider.Providers.HolidayProvider;
import com.golmok.oneweek.provider.Providers.WeatherProvider;
import com.golmok.oneweek.repository.AnalysisReportRepository;
import com.golmok.oneweek.repository.SourceRepository;
import com.golmok.oneweek.rule.WeeklyGuideRuleEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportService {

    public static final String DISCLAIMER =
            "본 결과는 공공데이터 및 연구자료를 기반으로 한 운영 참고용 제안이며, 실제 매출을 보장하지 않습니다.";
    public static final String DEMO_NOTICE =
            "날씨·행사·상권·식자재 중 일부를 실데이터로 불러오지 못해 예시 값이 포함되어 있습니다.";
    private static final int DAYS = 7;

    private final StoreService storeService;
    private final MenuClassificationService menuClassificationService;
    private final CommercialAreaService commercialAreaService;
    private final FestivalService festivalService;
    private final IngredientPriceService ingredientPriceService;
    private final WeeklyGuideRuleEngine ruleEngine;
    private final WeatherProvider weatherProvider;
    private final HolidayProvider holidayProvider;
    private final ReportChatService reportChatService;
    private final AnalysisReportRepository reportRepository;
    private final SourceRepository sourceRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ReportResponse create(CreateRequest request) {
        Store store = storeService.getEntity(request.storeId());
        MenuCategory category = request.menuCategory() != null
                ? request.menuCategory()
                : menuClassificationService.classify(request.mainMenu(), store.getCategory()).menuCategory();

        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(DAYS - 1L);

        List<WeatherDay> weather = weather(store, start);
        List<FestivalInfo> festivals = festivalService.nearby(store, start, end);
        CommercialArea area = commercialAreaService.summarize(store);
        Map<LocalDate, String> holidays = holidayProvider.classify(start, end);
        List<IngredientPriceInfo> prices = ingredientPriceService.forCategory(category);

        var output = ruleEngine.evaluate(
                new WeeklyGuideRuleEngine.Input(category, weather, festivals, area, holidays, prices));
        // LLM은 숫자의 존재 여부만 검증할 수 있고, 상권 분류 같은 숫자의 의미까지 보장할 수 없다.
        // 주간 리포트에는 규칙 엔진이 만든 검증 가능한 요약만 제공한다.
        String aiSummary = null;

        boolean anyDemo = store.isDemoData()
                || weather.stream().anyMatch(WeatherDay::isDemoData)
                || festivals.stream().anyMatch(FestivalInfo::isDemoData)
                || (area != null && area.isDemoData())
                || prices.stream().anyMatch(IngredientPriceInfo::isDemoData);

        AnalysisReport saved = reportRepository.save(AnalysisReport.builder()
                .storeId(store.getId())
                .mainMenu(request.mainMenu())
                .menuCategory(category)
                .analysisStartDate(start)
                .analysisEndDate(end)
                .summary(output.summary())
                .aiSummary(aiSummary)
                .weeklyWeatherJson(write(weather))
                .commercialAreaJson(write(area))
                .festivalJson(write(festivals))
                .ingredientPricesJson(write(prices))
                .recommendationsJson(write(new StoredRecommendations(output.topActions(), output.dailyGuides())))
                .demoData(anyDemo)   // 어느 한 조각이라도 예시 값이면 리포트 전체에 안내를 남긴다
                .build());

        return toResponse(saved, StoreResponse.from(store), weather, area, festivals, prices,
                output.topActions(), output.dailyGuides());
    }

    public String chat(Long reportId, ChatRequest request) {
        ReportResponse report = get(reportId);
        return reportChatService.ask(report, request.question(), request.history());
    }

    public ReportResponse get(Long reportId) {
        AnalysisReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("해당 리포트를 찾을 수 없습니다. id=" + reportId));
        StoreResponse store = storeService.get(report.getStoreId());
        List<WeatherDay> weather = read(report.getWeeklyWeatherJson(), new TypeReference<>() {});
        CommercialArea area = read(report.getCommercialAreaJson(), new TypeReference<>() {});
        List<FestivalInfo> festivals = read(report.getFestivalJson(), new TypeReference<>() {});
        List<IngredientPriceInfo> prices = read(report.getIngredientPricesJson(), new TypeReference<>() {});
        StoredRecommendations rec = read(report.getRecommendationsJson(), new TypeReference<>() {});
        return toResponse(report, store, weather, area, festivals, prices, rec.topActions(), rec.dailyGuides());
    }

    /** 리포트에 인용된 모든 출처를 모아서 응답에 함께 실어준다. */
    private ReportResponse toResponse(AnalysisReport report, StoreResponse store, List<WeatherDay> weather,
                                      CommercialArea area, List<FestivalInfo> festivals,
                                      List<IngredientPriceInfo> prices,
                                      List<Recommendation> topActions, List<DailyGuide> dailyGuides) {
        Set<Long> ids = new LinkedHashSet<>();
        topActions.forEach(r -> ids.addAll(r.sourceIds()));
        dailyGuides.forEach(g -> g.guides().forEach(r -> ids.addAll(r.sourceIds())));
        weather.stream().map(WeatherDay::sourceId).filter(Objects::nonNull).forEach(ids::add);
        festivals.stream().map(FestivalInfo::sourceId).filter(Objects::nonNull).forEach(ids::add);
        prices.stream().map(IngredientPriceInfo::sourceId).filter(Objects::nonNull).forEach(ids::add);
        if (area != null && area.sourceIds() != null) ids.addAll(area.sourceIds());

        List<SourceResponse> sources = sourceRepository.findAllById(ids).stream().map(SourceResponse::from).toList();

        return new ReportResponse(report.getId(), store, report.getMainMenu(), report.getMenuCategory(),
                report.getAnalysisStartDate(), report.getAnalysisEndDate(), report.getSummary(), null,
                topActions, weather, area, festivals, prices, dailyGuides, sources,
                report.isDemoData(), report.isDemoData() ? DEMO_NOTICE : null, DISCLAIMER);
    }

    private List<WeatherDay> weather(Store store, LocalDate start) {
        double lat = store.getLatitude() == null ? 35.8714 : store.getLatitude();
        double lon = store.getLongitude() == null ? 128.6014 : store.getLongitude();
        var result = weatherProvider.weekly(lat, lon, start, DAYS);
        if (store.getLatitude() != null && store.getLongitude() != null) return result;
        // 가게 위치가 미확인인 경우 시청 기준 예보를 실제 가게 예보로 표시하지 않는다.
        return result.stream().map(w -> new WeatherDay(w.date(), w.dayOfWeek(), w.condition(),
                w.tempMax(), w.tempMin(), w.precipitationProbability(), w.precipitationMm(), w.humidity(),
                true, w.sourceId())).toList();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("리포트 저장 중 JSON 변환 실패", e);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("리포트 조회 중 JSON 파싱 실패", e);
        }
    }

    private record StoredRecommendations(List<Recommendation> topActions, List<DailyGuide> dailyGuides) {}
}
