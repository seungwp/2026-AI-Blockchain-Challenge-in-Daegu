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

    @Test void 주소검색은_대구만_반환하고_인증실패를_빈결과로_위장하지않음() throws Exception {
        var root = mapper.readTree("""
                {"results":{"common":{"errorCode":"0"},"juso":[
                  {"siNm":"서울특별시","roadAddrPart1":"서울 주소"},
                  {"siNm":"대구광역시","roadAddrPart1":"대구광역시 중구 공평로 88","jibunAddr":"동인동","sggNm":"중구"}
                ]}}
                """);
        assertEquals(1, JusoAddressProvider.parse(root).size());
        var error = mapper.readTree("{\"results\":{\"common\":{\"errorCode\":\"E0001\"}}}");
        assertThrows(IllegalArgumentException.class, () -> JusoAddressProvider.parse(error));
    }

    @Test void 좌표없는_직접입력은_시청좌표를_만들지않음() {
        var repo = mock(StoreRepository.class);
        var juso = mock(JusoAddressProvider.class);
        when(juso.search("새 주소")).thenReturn(List.of(new JusoAddressProvider.Address("대구광역시 중구 새길 1", "새동", "중구")));
        when(repo.findByCityAndRoadAddressStartingWith(anyString(), anyString())).thenReturn(List.of());
        when(repo.save(any(Store.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var result = new DbStoreSearchProvider(repo, juso).fromAddress("새 주소", "대구광역시");
        assertNull(result.getLatitude());
        assertNull(result.getLongitude());
        assertEquals("대구광역시 중구 새길 1", result.getRoadAddress());
    }

    @Test void 주소가_여러개면_임의선택하지않음() {
        var repo = mock(StoreRepository.class);
        var juso = mock(JusoAddressProvider.class);
        when(juso.search("넓은 주소")).thenReturn(List.of(
                new JusoAddressProvider.Address("주소1", "", ""), new JusoAddressProvider.Address("주소2", "", "")));
        assertThrows(IllegalArgumentException.class, () -> new DbStoreSearchProvider(repo, juso).fromAddress("넓은 주소", "대구광역시"));
        verify(repo, never()).save(any());
    }
}
