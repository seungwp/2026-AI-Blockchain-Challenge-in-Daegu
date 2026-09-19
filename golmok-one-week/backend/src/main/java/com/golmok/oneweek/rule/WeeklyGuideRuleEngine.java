package com.golmok.oneweek.rule;

import com.golmok.oneweek.dto.ReportDtos.*;
import com.golmok.oneweek.entity.Enums.*;
import com.golmok.oneweek.entity.MenuRule;
import com.golmok.oneweek.repository.MenuRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * 날씨·행사·상권·메뉴 규칙을 결합해 '점검/준비/고려' 수준의 운영 권고를 만든다.
 * 매출액이나 증감률은 계산하지 않는다 (실제 매출 데이터가 없기 때문).
 */
@Component
@RequiredArgsConstructor
public class WeeklyGuideRuleEngine {

    private final MenuRuleRepository menuRuleRepository;

    public record Input(MenuCategory menuCategory, List<WeatherDay> weather, List<FestivalInfo> festivals,
                        CommercialArea commercialArea, Map<LocalDate, String> holidays,
                        List<IngredientPriceInfo> ingredientPrices) {}

    public record Output(List<Recommendation> topActions, List<DailyGuide> dailyGuides, String summary) {}

    public Output evaluate(Input in) {
        MenuCategory category = in.menuCategory() == null ? MenuCategory.ETC : in.menuCategory();
        List<MenuRule> rules = applicableRules(menuRuleRepository.findByMenuCategoryIn(List.of(MenuCategory.COMMON, category)), category);

        List<DailyGuide> daily = new ArrayList<>();
        List<Recommendation> all = new ArrayList<>();

        for (WeatherDay day : in.weather()) {
            List<FestivalInfo> nearby = in.festivals().stream()
                    .filter(f -> !day.date().isBefore(f.startDate()) && !day.date().isAfter(f.endDate()))
                    .toList();
            List<Recommendation> guides = new ArrayList<>();
            String holidayCode = in.holidays().get(day.date());
            for (MenuRule rule : rules) {
                if (rule.getConditionType() == ConditionType.COMPETITION) continue;  // 기간 전체에 적용되는 규칙
                Optional<String> basis = matchDay(rule, day, nearby, holidayCode);
                basis.ifPresent(b -> guides.add(toRecommendation(rule, day.date(), b, severity(rule, day, nearby))));
            }
            guides.sort(Comparator.<Recommendation>comparingInt(r -> r.priority().ordinal())
                    .thenComparingInt(r -> conditionRank(r.conditionType())));
            daily.add(new DailyGuide(day.date(), day.dayOfWeek(), weatherSummary(day), guides));
            all.addAll(guides);
        }

        // 상권 경쟁 규칙은 날짜와 무관하게 주간 전체 권고로 1건 추가
        for (MenuRule rule : rules) {
            if (rule.getConditionType() != ConditionType.COMPETITION) continue;
            int threshold = intValue(rule.getConditionValue(), 3);
            Integer same = in.commercialArea() == null ? null : in.commercialArea().sameCategoryStores();
            if (same != null && same >= threshold
                    && "COMPLETE".equals(in.commercialArea().competitionLevel())) {
                String basis = "반경 500m 내 유사 업종 %d곳".formatted(same);
                Recommendation r = toRecommendation(rule, null, basis, false);
                List<Long> sources = new ArrayList<>(r.sourceIds());
                in.commercialArea().sourceIds().forEach(id -> { if (!sources.contains(id)) sources.add(id); });
                all.add(new Recommendation(r.title(), r.text(), r.type(), r.priority(), r.confidence(),
                        r.conditionType(), r.basis(), r.date(), sources));
            }
        }

        // 식자재 급등확률 규칙도 날짜와 무관하게 카테고리별 품목을 한 번만 확인한다
        for (MenuRule rule : rules) {
            if (rule.getConditionType() != ConditionType.PRICE_SPIKE) continue;
            in.ingredientPrices().stream().filter(IngredientPriceInfo::alert).findFirst().ifPresent(p -> {
                String basis = "%s 급등확률 %.0f%%".formatted(p.item(), p.probSpike() * 100);
                if (p.vsNormalRatio() != null) basis += " (평년 대비 %+.0f%%)".formatted(p.vsNormalRatio() * 100);
                all.add(toRecommendation(rule, null, basis, false));
            });
        }

        List<Recommendation> top = topThree(all);
        return new Output(top, daily, summarize(in));
    }

    /**
     * 같은 (조건, 권고유형)에 메뉴 전용 규칙이 있으면 공통 규칙은 뺀다 — 같은 날 비슷한 권고가 두 번 뜨지 않게.
     * 중식은 강수 규칙을 적용하지 않는다: 권태용 외(2018) "강수여부는 중식을 제외한 배달음식에서 유의".
     */
    static List<MenuRule> applicableRules(List<MenuRule> rules, MenuCategory category) {
        Set<String> specific = new HashSet<>();
        rules.stream().filter(r -> r.getMenuCategory() != MenuCategory.COMMON).forEach(r -> specific.add(ruleKey(r)));
        return rules.stream()
                .filter(r -> r.getMenuCategory() != MenuCategory.COMMON || !specific.contains(ruleKey(r)))
                .filter(r -> !(category == MenuCategory.CHINESE && r.getConditionType() == ConditionType.RAIN))
                .toList();
    }

    private static String ruleKey(MenuRule r) {
        return r.getConditionType() + "|" + r.getRecommendationType();
    }

    /** 날짜별 조건 충족 여부. 충족하면 근거 문구를 담아 돌려준다. */
    private Optional<String> matchDay(MenuRule rule, WeatherDay day, List<FestivalInfo> nearby, String holidayCode) {
        String label = "%s(%s)".formatted(day.date(), day.dayOfWeek());
        return switch (rule.getConditionType()) {
            case RAIN -> day.precipitationProbability() != null && day.precipitationProbability() >= intValue(rule.getConditionValue(), 60)
                    ? Optional.of("%s 강수확률 %d%%".formatted(label, day.precipitationProbability())) : Optional.empty();
            case HOT -> day.tempMax() != null && day.tempMax() >= doubleValue(rule.getConditionValue(), 30)
                    ? Optional.of("%s 최고기온 %.1f℃".formatted(label, day.tempMax())) : Optional.empty();
            case COLD -> day.tempMin() != null && day.tempMin() <= doubleValue(rule.getConditionValue(), 5)
                    ? Optional.of("%s 최저기온 %.1f℃".formatted(label, day.tempMin())) : Optional.empty();
            case WEEKEND -> isWeekend(day.date())
                    ? Optional.of("%s 주말".formatted(label)) : Optional.empty();
            case FESTIVAL -> {
                int[] band = distanceBand(rule.getConditionValue());
                yield nearby.stream()
                        .filter(f -> f.distanceMeters() != null
                                && f.distanceMeters() > band[0] && f.distanceMeters() <= band[1])
                        .findFirst()
                        .map(WeeklyGuideRuleEngine::festivalBasis);
            }
            case HOLIDAY -> holidayCode != null && holidayCode.equals(rule.getConditionValue())
                    ? Optional.of("%s %s".formatted(label, holidayLabel(holidayCode))) : Optional.empty();
            case COMPETITION -> Optional.empty();
            case PRICE_SPIKE -> Optional.empty();   // 날짜와 무관, 위에서 1회만 처리한다
        };
    }

    private static String holidayLabel(String code) {
        return switch (code) {
            case "CHUSEOK_EVE" -> "추석 전날";
            case "CHUSEOK_DAY" -> "추석 당일";
            case "CHUSEOK_PERIOD" -> "추석 연휴 기간";
            case "CHUSEOK_LAST" -> "추석 연휴 마지막날";
            default -> code;
        };
    }

    /**
     * 행사 거리 조건을 (초과, 이하] 구간으로 읽는다.
     * "1000" = 1km 이내, "1000-3000" = 1km 초과 3km 이내.
     * 구간을 나눠야 1km 규칙과 3km 규칙이 같은 행사에 중복으로 걸리지 않는다.
     */
    private static int[] distanceBand(String value) {
        if (value != null && value.contains("-")) {
            String[] p = value.split("-", 2);
            return new int[]{intValue(p[0], 0), intValue(p[1], 3000)};
        }
        return new int[]{-1, intValue(value, 1000)};
    }

    /** 행사 근거 문구: "대구메이커페스타 · 9/19(토)~9/20(일) 개최 · 가게에서 1.2km". */
    private static String festivalBasis(FestivalInfo f) {
        String period = f.startDate().equals(f.endDate())
                ? dayLabel(f.startDate())
                : dayLabel(f.startDate()) + "~" + dayLabel(f.endDate());
        return "%s · %s 개최 · 가게에서 %s".formatted(f.name(), period, distanceLabel(f.distanceMeters()));
    }

    private static String dayLabel(LocalDate d) {
        String[] dow = {"월", "화", "수", "목", "금", "토", "일"};
        return "%d/%d(%s)".formatted(d.getMonthValue(), d.getDayOfMonth(), dow[d.getDayOfWeek().getValue() - 1]);
    }

    private static String distanceLabel(Integer meters) {
        if (meters == null) return "거리 정보 없음";
        return meters >= 1000 ? "%.1fkm".formatted(meters / 1000.0) : meters + "m";
    }

    /** 조건이 강하게 걸린 날이면 우선순위를 올린다. */
    private boolean severity(MenuRule rule, WeatherDay day, List<FestivalInfo> nearby) {
        return switch (rule.getConditionType()) {
            case RAIN -> day.precipitationProbability() != null && day.precipitationProbability() >= 60;
            case HOT -> day.tempMax() != null && day.tempMax() >= 33;
            case COLD -> day.tempMin() != null && day.tempMin() <= 0;
            case FESTIVAL -> nearby.stream().anyMatch(f -> f.distanceMeters() != null && f.distanceMeters() <= 1000);
            case HOLIDAY -> true;
            default -> false;
        };
    }

    private Recommendation toRecommendation(MenuRule rule, LocalDate date, String basis, boolean severe) {
        Priority priority = switch (rule.getConfidence()) {
            case HIGH -> severe ? Priority.HIGH : Priority.MEDIUM;
            case MEDIUM -> severe ? Priority.MEDIUM : Priority.LOW;
            case LOW -> Priority.LOW;
        };
        String title = titleOf(rule.getRecommendationType(), rule.getConditionType());
        List<Long> sources = rule.getSourceId() == null ? List.of() : List.of(rule.getSourceId());
        return new Recommendation(title, rule.getRecommendationText(), rule.getRecommendationType(), priority,
                rule.getConfidence(), rule.getConditionType(), basis, date, sources);
    }

    private String titleOf(RecommendationType type, ConditionType condition) {
        String what = switch (type) {
            case INVENTORY -> "재료·재고 점검";
            case STAFFING -> "인력·운영 점검";
            case MENU -> "메뉴 구성 고려";
            case DELIVERY -> "포장·배달 점검";
            case MARKETING -> "노출·안내 점검";
            case NOTICE -> "고객 안내";
        };
        String when = switch (condition) {
            case RAIN -> "비 예보";
            case HOT -> "고온";
            case COLD -> "저온";
            case WEEKEND -> "주말";
            case HOLIDAY -> "명절";
            case FESTIVAL -> "행사";
            case COMPETITION -> "주변 상권";
            case PRICE_SPIKE -> "식자재 가격";
        };
        return "%s · %s".formatted(when, what);
    }

    /**
     * 조건별 정렬 순위. 권태용 외(2018) 랜덤포레스트 변수중요도가 '시간 > 요일 > 월 > 평균온도 > 강수'
     * 순으로 나타나, 요일·시기 조건을 날씨 조건보다 앞에 둔다. docs/coefficients.md 6절 #7.
     */
    private static int conditionRank(ConditionType c) {
        return switch (c) {
            case HOLIDAY -> 0;
            case WEEKEND -> 1;
            case FESTIVAL -> 2;
            case HOT -> 3;
            case COLD -> 4;
            case RAIN -> 5;
            case COMPETITION -> 6;
            case PRICE_SPIKE -> 7;
        };
    }

    /**
     * priority 높은 순 → 가까운 날짜 순으로 고르되, 같은 (조건, 권고유형) 조합은 한 번만 담는다.
     * 주말은 매주 돌아와 매번 핵심에 들면 뻔하므로, 이번 주만의 조건(비·명절·행사·가격 등)이 있으면 핵심에서 뺀다.
     */
    static List<Recommendation> topThree(List<Recommendation> all) {
        boolean hasWeekVariable = all.stream().anyMatch(r -> r.conditionType() != ConditionType.WEEKEND);
        Map<String, Recommendation> unique = new LinkedHashMap<>();
        all.stream()
                .filter(r -> !hasWeekVariable || r.conditionType() != ConditionType.WEEKEND)
                .sorted(Comparator.<Recommendation>comparingInt(r -> r.priority().ordinal())
                        .thenComparingInt(r -> conditionRank(r.conditionType()))
                        .thenComparing(r -> r.date() == null ? LocalDate.MAX : r.date()))
                .forEach(r -> unique.putIfAbsent(r.conditionType() + "|" + r.type(), r));
        return unique.values().stream().limit(3).toList();
    }

    static String summarize(Input in) {
        long rainDays = in.weather().stream().filter(d -> d.precipitationProbability() != null && d.precipitationProbability() >= 60).count();
        long hotDays = in.weather().stream().filter(d -> d.tempMax() != null && d.tempMax() >= 30).count();
        long coldDays = in.weather().stream().filter(d -> d.tempMin() != null && d.tempMin() <= 5).count();
        boolean holiday = in.weather().stream().anyMatch(d -> in.holidays().containsKey(d.date()));
        String fest = in.festivals().stream()
                .filter(f -> f.distanceMeters() != null && f.distanceMeters() <= 3000)
                .map(FestivalInfo::name).findFirst().orElse(null);
        List<String> spikes = in.ingredientPrices().stream().filter(IngredientPriceInfo::alert).map(IngredientPriceInfo::item).toList();

        List<String> parts = new ArrayList<>();
        if (holiday) parts.add("추석 연휴");
        if (rainDays > 0) parts.add("비 예보 %d일".formatted(rainDays));
        if (hotDays > 0) parts.add("30℃ 이상 %d일".formatted(hotDays));
        if (coldDays > 0) parts.add("최저 5℃ 이하 %d일".formatted(coldDays));
        if (fest != null) parts.add("인근 행사 '%s'".formatted(fest));
        if (!spikes.isEmpty()) parts.add("%s 가격 확인 신호".formatted(String.join("·", spikes)));

        String sb = parts.isEmpty()
                ? "이번 주는 날씨·명절·행사·식자재 가격에 큰 변수가 없어 평소대로 운영하셔도 됩니다."
                : "이번 주에는 %s에 맞춰 운영 준비를 해보세요. 날짜별 처방에서 자세한 내용을 확인할 수 있어요."
                .formatted(String.join(", ", parts));
        return sb;
    }

    private String weatherSummary(WeatherDay d) {
        return "%s · 최고 %.1f℃ / 최저 %.1f℃ · 강수확률 %d%%"
                .formatted(d.condition(), d.tempMax(), d.tempMin(), d.precipitationProbability());
    }

    private boolean isWeekend(LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private static int intValue(String v, int fallback) {
        try { return Integer.parseInt(v.trim()); } catch (Exception e) { return fallback; }
    }

    private double doubleValue(String v, double fallback) {
        try { return Double.parseDouble(v.trim()); } catch (Exception e) { return fallback; }
    }
}
