package com.golmok.oneweek.service;

import com.golmok.oneweek.dto.ReportDtos.IngredientPriceInfo;
import com.golmok.oneweek.entity.Enums.MenuCategory;
import com.golmok.oneweek.provider.KamisPriceProvider;
import com.golmok.oneweek.provider.KamisPriceProvider.LivePrice;
import com.golmok.oneweek.repository.IngredientPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 리포트의 식자재 참고 가격 조각. 품목 매핑은 {@link MenuIngredientMap}, 급등 권고 규칙은 menu_rules.csv 의 PRICE_SPIKE. */
@Service
@RequiredArgsConstructor
public class IngredientPriceService {

    private final IngredientPriceRepository ingredientPriceRepository;
    private final KamisPriceProvider kamisPriceProvider;

    /**
     * 메뉴 카테고리에 대응하는 KAMIS 품목의 가격·급등확률을 조회한다.
     * 가격·기준일은 요청 시점에 KAMIS 를 실시간으로 조회해 채우고(실패 시 최근 시드 스냅샷으로 대체),
     * 급등확률은 학습된 모델의 결과라 재계산하지 않고 매일 갱신되는 시드 스냅샷 값을 그대로 쓴다.
     */
    public List<IngredientPriceInfo> forCategory(MenuCategory category) {
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
}
