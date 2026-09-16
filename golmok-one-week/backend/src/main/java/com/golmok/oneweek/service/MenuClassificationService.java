package com.golmok.oneweek.service;

import com.golmok.oneweek.dto.MenuDtos.ClassifyResponse;
import com.golmok.oneweek.entity.Enums.Confidence;
import com.golmok.oneweek.entity.Enums.MenuCategory;
import org.springframework.stereotype.Service;

import java.util.*;

/** 메뉴명 키워드 기반 카테고리 분류. 매칭 실패 시 기타(LOW)로 두고 사용자가 직접 고르게 한다. */
@Service
public class MenuClassificationService {

    private static final Map<MenuCategory, List<String>> KEYWORDS = new LinkedHashMap<>();
    static {
        KEYWORDS.put(MenuCategory.SOUP, List.of("김치찌개", "된장찌개", "순두부", "국밥", "설렁탕", "갈비탕", "해장국", "전골", "찌개", "탕"));
        KEYWORDS.put(MenuCategory.GRILL, List.of("닭똥집", "막창", "곱창", "삼겹살", "갈비", "불고기", "오리구이", "구이", "바베큐"));
        KEYWORDS.put(MenuCategory.COLD_NOODLE, List.of("냉면", "물냉면", "비빔냉면", "밀면", "물회", "냉국수"));
        KEYWORDS.put(MenuCategory.SNACK, List.of("떡볶이", "순대", "튀김", "김밥", "라면", "쫄면", "분식", "납작만두", "만두"));
        KEYWORDS.put(MenuCategory.CHICKEN, List.of("치킨", "후라이드", "양념치킨", "닭강정"));
        KEYWORDS.put(MenuCategory.JAPANESE, List.of("초밥", "스시", "라멘", "돈까스", "우동", "사시미", "회"));
        KEYWORDS.put(MenuCategory.CHINESE, List.of("짜장", "짬뽕", "탕수육", "마라탕", "마라샹궈", "중식"));
        KEYWORDS.put(MenuCategory.WESTERN, List.of("파스타", "피자", "스테이크", "리조또", "햄버거", "수제버거", "샐러드", "브런치"));
        KEYWORDS.put(MenuCategory.HEALTH, List.of("삼계탕", "추어탕", "장어", "보양", "한방"));
    }

    public ClassifyResponse classify(String menuName, String storeCategory) {
        String menu = menuName == null ? "" : menuName.replace(" ", "");
        String category = storeCategory == null ? "" : storeCategory.replace(" ", "");

        Match best = match(menu);
        if (best != null) {
            // 메뉴명에서 직접 찾았으면 신뢰도 높음 (키워드가 길수록 구체적)
            Confidence c = best.keyword().length() >= 3 ? Confidence.HIGH : Confidence.MEDIUM;
            return new ClassifyResponse(best.category(), c, List.of(best.keyword()), true);
        }
        Match byStore = match(category);
        if (byStore != null) {
            return new ClassifyResponse(byStore.category(), Confidence.MEDIUM, List.of(byStore.keyword()), true);
        }
        return new ClassifyResponse(MenuCategory.ETC, Confidence.LOW, List.of(), true);
    }

    private record Match(MenuCategory category, String keyword) {}

    /** 가장 긴 키워드가 우선 (예: "닭강정"이 "탕"보다 우선). */
    private Match match(String text) {
        if (text.isBlank()) return null;
        Match best = null;
        for (var e : KEYWORDS.entrySet()) {
            for (String k : e.getValue()) {
                if (text.contains(k) && (best == null || k.length() > best.keyword().length())) {
                    best = new Match(e.getKey(), k);
                }
            }
        }
        return best;
    }
}
