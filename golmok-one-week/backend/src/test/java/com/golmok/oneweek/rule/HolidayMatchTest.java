package com.golmok.oneweek.rule;

import com.golmok.oneweek.dto.ReportDtos.FestivalInfo;
import com.golmok.oneweek.dto.ReportDtos.WeatherDay;
import com.golmok.oneweek.entity.Enums.ConditionType;
import com.golmok.oneweek.entity.Enums.Confidence;
import com.golmok.oneweek.entity.Enums.MenuCategory;
import com.golmok.oneweek.entity.Enums.RecommendationType;
import com.golmok.oneweek.entity.MenuRule;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** HOLIDAY 조건이 규칙의 conditionValue 와 그 날짜의 분류 코드가 같을 때만 매칭되는지 확인. */
class HolidayMatchTest {

    private final WeeklyGuideRuleEngine engine = new WeeklyGuideRuleEngine(null);

    private MenuRule rule(String value) {
        return MenuRule.builder().menuCategory(MenuCategory.COMMON).conditionType(ConditionType.HOLIDAY)
                .conditionValue(value).recommendationType(RecommendationType.NOTICE)
                .recommendationText("t").sourceId(4L).confidence(Confidence.HIGH).build();
    }

    private WeatherDay day(LocalDate d) {
        return new WeatherDay(d, "금", "맑음", 25.0, 15.0, 10, 0.0, 50, false, 6L);
    }

    @SuppressWarnings("unchecked")
    private Optional<String> matchDay(MenuRule r, WeatherDay d, String holidayCode) throws Exception {
        Method m = WeeklyGuideRuleEngine.class.getDeclaredMethod(
                "matchDay", MenuRule.class, WeatherDay.class, List.class, String.class);
        m.setAccessible(true);
        return (Optional<String>) m.invoke(engine, r, d, List.<FestivalInfo>of(), holidayCode);
    }

    @Test
    void 코드가_같으면_매칭되고_근거에_한글_라벨이_들어간다() throws Exception {
        var basis = matchDay(rule("CHUSEOK_DAY"), day(LocalDate.of(2026, 9, 25)), "CHUSEOK_DAY");
        assertTrue(basis.isPresent());
        assertTrue(basis.get().contains("추석 당일"), basis.get());
    }

    @Test
    void 코드가_다르면_매칭되지_않는다() throws Exception {
        assertTrue(matchDay(rule("CHUSEOK_DAY"), day(LocalDate.of(2026, 9, 24)), "CHUSEOK_EVE").isEmpty());
    }

    @Test
    void 해당일이_명절이_아니면_매칭되지_않는다() throws Exception {
        assertTrue(matchDay(rule("CHUSEOK_DAY"), day(LocalDate.of(2026, 9, 18)), null).isEmpty());
    }
}
