package com.golmok.oneweek.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.golmok.oneweek.dto.ReportDtos.*;
import com.golmok.oneweek.dto.SourceResponse;
import com.golmok.oneweek.dto.StoreResponse;
import com.golmok.oneweek.entity.AnalysisReport;
import com.golmok.oneweek.entity.Enums.MenuCategory;
import com.golmok.oneweek.entity.Store;
import com.golmok.oneweek.exception.NotFoundException;
import com.golmok.oneweek.provider.Providers.AirQualityProvider;
import com.golmok.oneweek.provider.Providers.FestivalProvider;
import com.golmok.oneweek.provider.Providers.HolidayProvider;
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
            "가게·상권·축제·날씨는 공공데이터 실데이터입니다. 미세먼지만 아직 예시 값이며, 에어코리아 API 연동 시 같은 형식으로 교체됩니다.";
    private static final int DAYS = 7;

    private final StoreService storeService;
    private final MenuClassificationService menuClassificationService;
    private final CommercialAreaService commercialAreaService;
    private final WeeklyGuideRuleEngine ruleEngine;
    private final WeatherProvider weatherProvider;
    private final AirQualityProvider airQualityProvider;
    private final FestivalProvider festivalProvider;
    private final HolidayProvider holidayProvider;
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

        var output = ruleEngine.evaluate(
                new WeeklyGuideRuleEngine.Input(category, weather, festivals, area, holidays));

        AnalysisReport saved = reportRepository.save(AnalysisReport.builder()
                .storeId(store.getId())
                .mainMenu(request.mainMenu())
                .menuCategory(category)
                .analysisStartDate(start)
                .analysisEndDate(end)
                .summary(output.summary())
                .weeklyWeatherJson(write(weather))
                .commercialAreaJson(write(area))
                .festivalJson(write(festivals))
                .recommendationsJson(write(new StoredRecommendations(output.topActions(), output.dailyGuides())))
                .demoData(true)   // 미세먼지가 아직 예시 값이라 리포트에 안내 문구를 남긴다
                .build());

        return toResponse(saved, StoreResponse.from(store), weather, area, festivals, output.topActions(), output.dailyGuides());
    }

    public ReportResponse get(Long reportId) {
        AnalysisReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("해당 리포트를 찾을 수 없습니다. id=" + reportId));
        StoreResponse store = storeService.get(report.getStoreId());
        List<WeatherDay> weather = read(report.getWeeklyWeatherJson(), new TypeReference<>() {});
        CommercialArea area = read(report.getCommercialAreaJson(), new TypeReference<>() {});
        List<FestivalInfo> festivals = read(report.getFestivalJson(), new TypeReference<>() {});
        StoredRecommendations rec = read(report.getRecommendationsJson(), new TypeReference<>() {});
        return toResponse(report, store, weather, area, festivals, rec.topActions(), rec.dailyGuides());
    }

    /** 리포트에 인용된 모든 출처를 모아서 응답에 함께 실어준다. */
    private ReportResponse toResponse(AnalysisReport report, StoreResponse store, List<WeatherDay> weather,
                                      CommercialArea area, List<FestivalInfo> festivals,
                                      List<Recommendation> topActions, List<DailyGuide> dailyGuides) {
        Set<Long> ids = new LinkedHashSet<>();
        topActions.forEach(r -> ids.addAll(r.sourceIds()));
        dailyGuides.forEach(g -> g.guides().forEach(r -> ids.addAll(r.sourceIds())));
        weather.stream().map(WeatherDay::sourceId).filter(Objects::nonNull).forEach(ids::add);
        festivals.stream().map(FestivalInfo::sourceId).filter(Objects::nonNull).forEach(ids::add);
        if (area != null && area.sourceIds() != null) ids.addAll(area.sourceIds());

        List<SourceResponse> sources = sourceRepository.findAllById(ids).stream().map(SourceResponse::from).toList();

        return new ReportResponse(report.getId(), store, report.getMainMenu(), report.getMenuCategory(),
                report.getAnalysisStartDate(), report.getAnalysisEndDate(), report.getSummary(),
                topActions, weather, area, festivals, dailyGuides, sources,
                report.isDemoData(), report.isDemoData() ? DEMO_NOTICE : null, DISCLAIMER);
    }

    private List<WeatherDay> weather(Store store, LocalDate start) {
        double lat = store.getLatitude() == null ? 35.8714 : store.getLatitude();
        double lon = store.getLongitude() == null ? 128.6014 : store.getLongitude();
        List<WeatherDay> days = weatherProvider.weekly(lat, lon, start, DAYS);
        List<String> dust = airQualityProvider.pm10Grades(lat, lon, start, DAYS);
        List<WeatherDay> merged = new ArrayList<>();
        for (int i = 0; i < days.size(); i++) {
            WeatherDay d = days.get(i);
            String grade = i < dust.size() ? dust.get(i) : d.pm10Grade();
            merged.add(new WeatherDay(d.date(), d.dayOfWeek(), d.condition(), d.tempMax(), d.tempMin(),
                    d.precipitationProbability(), d.precipitationMm(), d.humidity(), grade,
                    d.isDemoData(), d.sourceId()));
        }
        return merged;
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
