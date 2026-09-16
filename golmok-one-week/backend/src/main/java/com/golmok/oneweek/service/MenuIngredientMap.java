package com.golmok.oneweek.service;

import com.golmok.oneweek.entity.Enums.MenuCategory;

import java.util.List;
import java.util.Map;

/**
 * 메뉴 카테고리별로 참고할 KAMIS 식자재 품목. 8품목(계란·깐마늘·닭·대파·무·배추·삼겹살·양파)만
 * 확보돼 있어(pipeline/fetch_kamis.py), 실제 원가 구성과 명확히 겹치는 카테고리에만 연결한다.
 * 냉면류·일식·양식·기타는 대응 품목이 없어 비워 둔다 — 억지로 끼워 맞추지 않는다.
 */
final class MenuIngredientMap {
    private MenuIngredientMap() {}

    private static final Map<MenuCategory, List<String>> MAP = Map.of(
            MenuCategory.CHICKEN, List.of("닭"),
            MenuCategory.GRILL, List.of("삼겹살"),
            MenuCategory.SOUP, List.of("대파", "무", "배추"),
            MenuCategory.SNACK, List.of("대파", "양파", "계란"),
            MenuCategory.CHINESE, List.of("양파", "대파", "깐마늘"),
            MenuCategory.HEALTH, List.of("닭", "깐마늘"));

    static List<String> itemsFor(MenuCategory category) {
        return MAP.getOrDefault(category, List.of());
    }
}
