package com.golmok.oneweek.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.golmok.oneweek.entity.Store;
import com.golmok.oneweek.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicDataProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void 가격결측과_미래평년행이_최신실측가격을_덮지않음() throws Exception {
        var root = mapper.readTree("""
                {"data":{"item":[
                  {"countyname":"평균","yyyy":"2026","regday":"09/16","price":"4,269"},
                  {"countyname":"평균","yyyy":"2026","regday":"09/17","price":"-"},
                  {"countyname":"평년","yyyy":"2026","regday":"09/17","price":"5,600"},
                  {"countyname":"평년","yyyy":"2026","regday":"09/16","price":"5,626"},
                  {"countyname":"평균","yyyy":"2026","regday":"09/15","price":"0"},
                  {"countyname":"시장A","yyyy":"2026","regday":"09/18","price":"9,999"}
                ]}}
                """);
        var result = KamisPriceProvider.parse(root);
        assertEquals(LocalDate.of(2026, 9, 16), result.date());
        assertEquals(4269, result.price());
        assertEquals(5626, result.normalPrice());
        assertEquals(1, result.history().size());
    }

    @Test void 지오코딩은_대구좌표만_반환하고_실패를_빈결과로_위장하지않음() throws Exception {
        var root = mapper.readTree("""
                {"status":"OK","addresses":[
                  {"roadAddress":"서울 주소","x":"126.9","y":"37.5"},
                  {"roadAddress":"대구광역시 중구 공평로 88","jibunAddress":"대구광역시 중구 동인동","x":"128.6","y":"35.8",
                   "addressElements":[{"types":["SIGUGUN"],"longName":"중구"}]}
                ]}
                """);
        var addresses = NaverGeocodingProvider.parse(root);
        assertEquals(1, addresses.size());
        assertEquals(35.8, addresses.getFirst().latitude());
        assertEquals(128.6, addresses.getFirst().longitude());
        assertEquals("중구", addresses.getFirst().district());
        var error = mapper.readTree("{\"status\":\"INVALID_REQUEST\"}");
        assertThrows(IllegalArgumentException.class, () -> NaverGeocodingProvider.parse(error));
    }

    @Test void 직접입력은_지오코딩_좌표를_저장함() {
        var repo = mock(StoreRepository.class);
        var geocoding = mock(NaverGeocodingProvider.class);
        when(geocoding.search("새 주소")).thenReturn(List.of(new NaverGeocodingProvider.Address("대구광역시 중구 새길 1", "새동", "중구", 35.87, 128.6)));
        when(repo.save(any(Store.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var result = new DbStoreSearchProvider(repo, geocoding).fromAddress("새 주소", "대구광역시");
        assertEquals(35.87, result.getLatitude());
        assertEquals(128.6, result.getLongitude());
        assertEquals("대구광역시 중구 새길 1", result.getRoadAddress());
    }

    @Test void 주소가_여러개면_임의선택하지않음() {
        var repo = mock(StoreRepository.class);
        var geocoding = mock(NaverGeocodingProvider.class);
        when(geocoding.search("넓은 주소")).thenReturn(List.of(
                new NaverGeocodingProvider.Address("주소1", "", "", 35.8, 128.5), new NaverGeocodingProvider.Address("주소2", "", "", 35.9, 128.6)));
        assertThrows(IllegalArgumentException.class, () -> new DbStoreSearchProvider(repo, geocoding).fromAddress("넓은 주소", "대구광역시"));
        verify(repo, never()).save(any());
    }
}
