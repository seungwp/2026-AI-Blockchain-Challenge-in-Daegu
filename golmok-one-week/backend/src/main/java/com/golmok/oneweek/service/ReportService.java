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
import com.golmok.oneweek.provider.Providers.FestivalProvider;
import com.golmok.oneweek.provider.Providers.HolidayProvider;
import com.golmok.oneweek.repository.IngredientPriceRepository;
import com.golmok.oneweek.provider.KamisPriceProvider;
import com.golmok.oneweek.provider.KamisPriceProvider.LivePrice;
import com.golmok.oneweek.provider.Providers.WeatherProvider;
import com.golmok.oneweek.repository.AnalysisReportRepository;
import com.golmok.oneweek.repository.SourceRepository;
import com.golmok.oneweek.rule.WeeklyGuideRuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    public static final String DISCLAIMER =
            "본 결과는 공공데이터 및 연구자료를 기반으로 한 운영 참고용 제안이며, 실제 매출을 보장하지 않습니다.";
    public static final String DEMO_NOTICE =
            "일부 데이터는 아직 예시 값입니다. 아래 각 항목의 '데모 데이터' 표시로 어느 부분인지 확인할 수 있습니다.";
    private static final int DAYS = 7;

    private final StoreService storeService;
    private final MenuClassificationService menuClassificationService;
    private final CommercialAreaService commercialAreaService;
    private final WeeklyGuideRuleEngine ruleEngine;
    private final WeatherProvider weatherProvider;
    private final FestivalProvider festivalProvider;
    private final HolidayProvider holidayProvider;
    private final IngredientPriceRepository ingredientPriceRepository;
    private final KamisPriceProvider kamisPriceProvider;
    private final LlmAdviceService llmAdviceService;
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
        List<FestivalInfo> festivals = festivals(store, start, end);
        CommercialArea area = commercialAreaService.summarize(store);
        Map<LocalDate, String> holidays = holidayProvider.classify(start, end);
        List<IngredientPriceInfo> prices = ingredientPrices(category);

        var output = ruleEngine.evaluate(
                new WeeklyGuideRuleEngine.Input(category, weather, festivals, area, holidays, prices));
        String aiSummary = llmAdviceService.summarize(
                request.mainMenu(), category.label(), output.summary(), output.topActions(), area);

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

    /**
     * 메뉴 카테고리에 대응하는 KAMIS 품목의 가격·급등확률을 조회한다.
     * 가격·기준일은 요청 시점에 KAMIS 를 실시간으로 조회해 채우고(실패 시 최근 시드 스냅샷으로 대체),
     * 급등확률은 학습된 모델의 결과라 재계산하지 않고 매일 갱신되는 시드 스냅샷 값을 그대로 쓴다.
     */
    private List<IngredientPriceInfo> ingredientPrices(MenuCategory category) {
        List<String> items = MenuIngredientMap.itemsFor(category);
        if (items.isEmpty()) return List.of();

        Map<String, LivePrice> live = kamisPriceProvider.fetchLatest(items);
        return ingredientPriceRepository.findByItemIn(items).stream()
                .map(p -> {
                    LivePrice l = live.get(p.getItem());
                    double price = l != null ? l.price() : p.getPrice();
                    LocalDate date = l != null ? l.date() : p.getPriceDate();
                    Double ratio = l != null && l.normalPrice() != null
                            ? (l.price() / l.normalPrice() - 1) : p.getVsNormalRatio();
                    return new IngredientPriceInfo(p.getItem(), p.getUnit(), price, date, p.getProbSpike(),
                            p.isAlert(), ratio, l == null && p.isDemoData(), p.getSourceId());
                })
                .toList();
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
                report.getAnalysisStartDate(), report.getAnalysisEndDate(), report.getSummary(), report.getAiSummary(),
                topActions, weather, area, festivals, prices, dailyGuides, sources,
                report.isDemoData(), report.isDemoData() ? DEMO_NOTICE : null, DISCLAIMER);
    }

    private List<WeatherDay> weather(Store store, LocalDate start) {
        double lat = store.getLatitude() == null ? 35.8714 : store.getLatitude();
        double lon = store.getLongitude() == null ? 128.6014 : store.getLongitude();
        return weatherProvider.weekly(lat, lon, start, DAYS);
    }

    /** 행사별 거리와 영향 구분(1km/3km)을 채운다. */
    private List<FestivalInfo> festivals(Store store, LocalDate start, LocalDate end) {
        List<FestivalInfo> out = new ArrayList<>();
        for (FestivalInfo f : festivalProvider.findFestivals(start, end)) {
            Integer distance = null;
            String impact = "거리 정보 없음";
            if (store.getLatitude() != null && f.latitude() != null && f.longitude() != null) {
                distance = (int) Math.round(CommercialAreaService.distanceMeters(
                        store.getLatitude(), store.getLongitude(), f.latitude(), f.longitude()));
                impact = distance <= 1000 ? "직접 영향 가능" : distance <= 3000 ? "간접 영향 가능" : "영향 제한적";
            }
            String note = switch (impact) {
                case "직접 영향 가능" -> "행사장과 가까워 방문객 유입 가능성이 있으며, 교통·주차 혼잡도 함께 고려가 필요합니다.";
                case "간접 영향 가능" -> "행사장과 다소 떨어져 있어 간접적인 유동 변화 가능성이 있습니다. (중간 신뢰도)";
                case "영향 제한적" -> "행사장과 거리가 멀어 직접적인 영향은 제한적일 수 있습니다.";
                default -> f.impactNote();
            };
            out.add(new FestivalInfo(f.id(), f.name(), f.startDate(), f.endDate(), f.locationName(), f.address(),
                    f.latitude(), f.longitude(), distance, impact, note, f.isDemoData(), f.sourceId()));
        }
        out.sort(Comparator.comparing(f -> f.distanceMeters() == null ? Integer.MAX_VALUE : f.distanceMeters()));
        return out;
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
