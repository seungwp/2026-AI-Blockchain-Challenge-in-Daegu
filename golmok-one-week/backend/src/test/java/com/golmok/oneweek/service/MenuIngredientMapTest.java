package com.golmok.oneweek.service;

import com.golmok.oneweek.entity.Enums.MenuCategory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 메뉴 카테고리 → KAMIS 품목 매핑. 확보된 8품목(계란·깐마늘·닭·대파·무·배추·삼겹살·양파)과
 * 겹치지 않는 카테고리(냉면류·일식·양식·기타)는 억지로 연결하지 않고 비워 둬야 한다.
 */
class MenuIngredientMapTest {

    @SuppressWarnings("unchecked")
    private List<String> itemsFor(MenuCategory category) throws Exception {
        Method m = MenuIngredientMap.class.getDeclaredMethod("itemsFor", MenuCategory.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(null, category);
    }

    private static final List<String> AVAILABLE =
            List.of("계란", "깐마늘", "닭", "대파", "무", "배추", "삼겹살", "양파");

    @Test
    void 매핑된_품목은_전부_실제_보유한_8품목_안에_있다() throws Exception {
        for (MenuCategory c : MenuCategory.values()) {
            for (String item : itemsFor(c)) {
                assertTrue(AVAILABLE.contains(item), c + " 에 매핑된 '" + item + "' 은 보유 품목이 아님");
            }
        }
    }

    @Test
    void 대응_품목이_없는_카테고리는_비워둔다() throws Exception {
        assertTrue(itemsFor(MenuCategory.COLD_NOODLE).isEmpty());
        assertTrue(itemsFor(MenuCategory.JAPANESE).isEmpty());
        assertTrue(itemsFor(MenuCategory.WESTERN).isEmpty());
        assertTrue(itemsFor(MenuCategory.ETC).isEmpty());
        assertTrue(itemsFor(MenuCategory.COMMON).isEmpty());
    }

    @Test
    void 치킨은_닭_구이는_삼겹살에_연결된다() throws Exception {
        assertEquals(List.of("닭"), itemsFor(MenuCategory.CHICKEN));
        assertEquals(List.of("삼겹살"), itemsFor(MenuCategory.GRILL));
    }
}
