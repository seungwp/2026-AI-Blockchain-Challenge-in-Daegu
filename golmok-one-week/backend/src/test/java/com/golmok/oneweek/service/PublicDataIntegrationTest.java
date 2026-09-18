package com.golmok.oneweek.service;

import com.golmok.oneweek.dto.ReportDtos.PricePoint;
import com.golmok.oneweek.entity.Store;
import com.golmok.oneweek.entity.Enums.MenuCategory;
import com.golmok.oneweek.repository.StoreRepository;
import com.golmok.oneweek.repository.FestivalEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"golmok.keys.kamis-cert-key=", "golmok.keys.kamis-cert-id=", "golmok.keys.groq-api-key="})
class PublicDataIntegrationTest {
    @Autowired StoreRepository stores;
    @Autowired FestivalEventRepository festivals;
    @Autowired IngredientPriceService prices;
    @Autowired CommercialAreaService area;
    @Autowired FestivalService festivalService;

    @Test void 실제수집본이_DB와_서비스에_연결됨() {
        var enriched = stores.findAll().stream().filter(s -> s.getDetailCategoryCode() != null).toList();
        assertTrue(enriched.size() > 10000);
        assertTrue(festivals.findAll().stream().anyMatch(f -> f.getPlayTime() != null && !f.getPlayTime().isBlank()));
        var reportPrices = prices.forCategory(MenuCategory.CHICKEN);
        assertFalse(reportPrices.isEmpty());
        var chicken = reportPrices.getFirst();
        assertTrue(chicken.history().size() >= 20);
        assertNotNull(chicken.comparisonDate());
        assertNotNull(chicken.vsPreviousWeekRatio());
        assertTrue(chicken.predictionStale());
        assertNull(chicken.probSpike());
        assertFalse(chicken.alert());
        assertTrue(area.summarize(enriched.getFirst()).sourceIds().contains(15L));
    }

    @Test void 좌표없는_가게에는_상권과_행사거리분석을_하지않음() {
        var store = Store.builder().id(99999L).city("대구광역시").build();
        assertNull(area.summarize(store));
        assertTrue(festivalService.nearby(store, LocalDate.now(), LocalDate.now().plusDays(6)).isEmpty());
    }

    @Test void 휴장일은_일주일전_직전관측을_쓰되_오래된값은_비교하지않음() {
        LocalDate current = LocalDate.of(2026, 9, 18);
        var recent = new PricePoint(LocalDate.of(2026, 9, 10), 1000.0);
        var ancient = new PricePoint(LocalDate.of(2026, 9, 1), 800.0);
        assertEquals(recent, IngredientPriceService.comparison(List.of(recent, ancient), current));
        assertNull(IngredientPriceService.comparison(List.of(ancient), current));
    }
}
