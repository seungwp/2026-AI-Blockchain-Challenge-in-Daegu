package com.golmok.oneweek.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 도메인 enum 모음 (파일 수를 줄이기 위해 한 파일에 둠). */
public final class Enums {
    private Enums() {}

    public enum SourceType { PAPER, PUBLIC_DATA, API, FESTIVAL, INDUSTRY, DEMO }
    public enum ConditionType { RAIN, HOT, COLD, DUST, WEEKEND, HOLIDAY, FESTIVAL, COMPETITION }
    public enum RecommendationType { INVENTORY, STAFFING, MENU, DELIVERY, MARKETING, NOTICE }
    public enum Confidence { HIGH, MEDIUM, LOW }
    public enum Priority { HIGH, MEDIUM, LOW }

    /** API에서는 한글 라벨("국물요리")로 주고받는다. */
    public enum MenuCategory {
        SOUP("국물요리"), GRILL("구이"), COLD_NOODLE("냉면류"), SNACK("분식"), CHICKEN("치킨"),
        JAPANESE("일식"), CHINESE("중식"), WESTERN("양식"), HEALTH("보양식"), COMMON("공통"), ETC("기타");

        private final String label;
        MenuCategory(String label) { this.label = label; }

        @JsonValue public String label() { return label; }

        @JsonCreator
        public static MenuCategory fromLabel(String value) {
            for (MenuCategory c : values()) if (c.label.equals(value) || c.name().equalsIgnoreCase(value)) return c;
            throw new IllegalArgumentException("지원하지 않는 메뉴 카테고리: " + value);
        }
    }
}
