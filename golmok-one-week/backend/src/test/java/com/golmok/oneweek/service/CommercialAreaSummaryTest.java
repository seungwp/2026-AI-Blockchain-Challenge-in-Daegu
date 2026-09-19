package com.golmok.oneweek.service;

import com.golmok.oneweek.entity.Store;
import com.golmok.oneweek.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommercialAreaSummaryTest {
    @Test
    void categoryComparisonKeepsCountsAndExcludesDemoAndSelf() {
        var repository = mock(StoreRepository.class);
        Store own = store(1L, "음식점 > 치킨", false);
        when(repository.findByCityAndLatitudeBetweenAndLongitudeBetween(
                anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(own, store(2L, "치킨", false),
                        store(3L, null, false), store(4L, "치킨", true)));
        var result = new CommercialAreaService(repository).summarize(own);
        assertEquals(2, result.totalStores());
        assertEquals(1, result.sameCategoryStores());
    }

    private Store store(Long id, String category, boolean demo) {
        return Store.builder().id(id).category(category).demoData(demo)
                .latitude(35.87).longitude(128.60).city("대구광역시").build();
    }
}
