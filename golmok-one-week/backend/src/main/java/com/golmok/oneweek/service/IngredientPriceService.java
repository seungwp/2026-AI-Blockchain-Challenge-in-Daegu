package com.golmok.oneweek.service;

import com.golmok.oneweek.dto.ReportDtos.IngredientPriceInfo;
import com.golmok.oneweek.dto.ReportDtos.PricePoint;
import com.golmok.oneweek.entity.Enums.MenuCategory;
import com.golmok.oneweek.entity.Enums.DataStatus;
import com.golmok.oneweek.provider.KamisPriceProvider;
import com.golmok.oneweek.provider.KamisPriceProvider.LivePrice;
import com.golmok.oneweek.repository.IngredientPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 리포트의 식자재 참고 가격 조각. 품목 매핑은 {@link MenuIngredientMap}, 급등 권고 규칙은 menu_rules.csv 의 PRICE_SPIKE. */
@Service
@RequiredArgsConstructor
public class IngredientPriceService {

    private final IngredientPriceRepository ingredientPriceRepository;
    private final KamisPriceProvider kamisPriceProvider;
    private final PublicDataSnapshots snapshots;

    /**
     * 메뉴 카테고리에 대응하는 KAMIS 품목의 가격·급등확률을 조회한다.
     * 가격·기준일은 요청 시점에 KAMIS 를 실시간으로 조회해 채우고(실패 시 최근 시드 스냅샷으로 대체),
     * 급등확률은 학습된 모델의 결과라 재계산하지 않고 매일 갱신되는 시드 스냅샷 값을 그대로 쓴다.
     */
    public List<IngredientPriceInfo> forCategory(MenuCategory category) {
        List<String> items = MenuIngredientMap.itemsFor(category);
        if (items.isEmpty()) return List.of();

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        // 새 리포트는 항상 KAMIS API를 우선 조회한다. API 장애·인증 미설정 때만 스냅샷으로 폴백한다.
        Map<String, LivePrice> live = kamisPriceProvider.fetchLatest(items);
        return ingredientPriceRepository.findByItemIn(items).stream()
                .map(p -> {
                    LivePrice l = live.get(p.getItem());
                    if (l == null) l = snapshotPrice(p.getItem());
                    double price = l != null ? l.price() : p.getPrice();
                    LocalDate date = l != null ? l.date() : p.getPriceDate();
                    Double ratio = l != null
                            ? (l.normalPrice() != null && l.normalPrice() > 0 ? l.price() / l.normalPrice() - 1 : null)
                            : p.getVsNormalRatio();
                    List<PricePoint> history = l == null ? List.of() : l.history();
                    PricePoint previous = comparison(history, date);
                    boolean stale = p.getPriceDate() == null || p.getPriceDate().isBefore(today.minusDays(1));
                    DataStatus status = l != null ? (l.live() ? DataStatus.LIVE : DataStatus.SNAPSHOT)
                            : p.isDemoData() ? DataStatus.DEMO : DataStatus.SNAPSHOT;
                    return new IngredientPriceInfo(p.getItem(), p.getUnit(), price, date, stale ? null : p.getProbSpike(),
                            !stale && p.isAlert(), ratio, status == DataStatus.DEMO, p.getSourceId(), history,
                            previous == null ? null : previous.date(), previous == null ? null : price / previous.price() - 1,
                            p.getPriceDate(), stale, "KAMIS 대구 소매 참고가격 · 실제 매입가와 다를 수 있음",
                            status, date == null ? null : date.toString());
                })
                .toList();
    }

    private LivePrice snapshotPrice(String item) {
        for (var p : snapshots.prices().path("items")) {
            if (!item.equals(p.path("item").asText())) continue;
            List<PricePoint> history = new ArrayList<>();
            for (var point : p.path("history")) history.add(new PricePoint(
                    LocalDate.parse(point.path("date").asText()), point.path("price").asDouble()));
            return new LivePrice(p.path("price").asDouble(),
                    p.hasNonNull("normalPrice") ? p.get("normalPrice").asDouble() : null,
                    LocalDate.parse(p.path("priceDate").asText()), List.copyOf(history), false);
        }
        return null;
    }

    /** 휴장일이면 일주일 전보다 앞선 가장 가까운 관측일(최대 3일 차이)을 사용한다. */
    static PricePoint comparison(List<PricePoint> history, LocalDate date) {
        if (date == null) return null;
        LocalDate target = date.minusDays(7);
        return history.stream().filter(p -> p.price() != null && p.price() > 0
                        && !p.date().isAfter(target) && !p.date().isBefore(target.minusDays(3)))
                .max(Comparator.comparing(PricePoint::date)).orElse(null);
    }
}
