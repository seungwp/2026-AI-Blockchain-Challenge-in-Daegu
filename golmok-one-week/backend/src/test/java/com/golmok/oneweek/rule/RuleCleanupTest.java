package com.golmok.oneweek.rule;

import com.golmok.oneweek.dto.ReportDtos.*;
import com.golmok.oneweek.entity.Enums.*;
import com.golmok.oneweek.entity.MenuRule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 뻔한 권고 정리: 메뉴 규칙 우선, 중식 강수 제외, 주말 핵심 제외, 변수 없는 주 요약. */
class RuleCleanupTest {

    private static MenuRule rule(MenuCategory cat, ConditionType cond, RecommendationType type) {
        return MenuRule.builder().menuCategory(cat).conditionType(cond).recommendationType(type)
                .recommendationText(cat + "-" + cond).sourceId(11L).confidence(Confidence.MEDIUM).build();
    }

    private static Recommendation rec(ConditionType cond, Priority priority) {
        return new Recommendation("t", "x", RecommendationType.NOTICE, priority, Confidence.MEDIUM, cond, "b",
                LocalDate.of(2026, 9, 19), List.of());
    }

    @Test
    void 같은_조건_유형이면_메뉴_규칙이_공통_규칙을_대체한다() {
        var common = rule(MenuCategory.COMMON, ConditionType.WEEKEND, RecommendationType.STAFFING);
        var chicken = rule(MenuCategory.CHICKEN, ConditionType.WEEKEND, RecommendationType.STAFFING);
        var commonRain = rule(MenuCategory.COMMON, ConditionType.RAIN, RecommendationType.DELIVERY);

        var result = WeeklyGuideRuleEngine.applicableRules(List.of(common, chicken, commonRain), MenuCategory.CHICKEN);

        assertEquals(List.of(chicken, commonRain), result);
    }

    @Test
    void 중식에는_강수_규칙을_적용하지_않는다() {
        var commonRain = rule(MenuCategory.COMMON, ConditionType.RAIN, RecommendationType.DELIVERY);
        var hot = rule(MenuCategory.COMMON, ConditionType.HOT, RecommendationType.DELIVERY);

        assertEquals(List.of(hot), WeeklyGuideRuleEngine.applicableRules(List.of(commonRain, hot), MenuCategory.CHINESE));
    }

    @Test
    void 이번_주_변수가_있으면_주말은_핵심에서_빠지고_없으면_남는다() {
        var weekend = rec(ConditionType.WEEKEND, Priority.MEDIUM);
        var rain = rec(ConditionType.RAIN, Priority.LOW);

        assertEquals(List.of(rain), WeeklyGuideRuleEngine.topThree(List.of(weekend, rain)));
        assertEquals(List.of(weekend), WeeklyGuideRuleEngine.topThree(List.of(weekend)));
    }

    @Test
    void 변수가_없는_주는_평소대로_운영_문구를_쓴다() {
        var calm = new WeatherDay(LocalDate.of(2026, 9, 21), "월", "맑음", 25.0, 15.0, 10, 0.0, 50, false, 6L);
        var in = new WeeklyGuideRuleEngine.Input(MenuCategory.CHICKEN, List.of(calm), List.of(), null, Map.of(), List.of());

        String summary = WeeklyGuideRuleEngine.summarize(in);

        assertTrue(summary.contains("평소대로 운영"), summary);
    }
}
