package com.golmok.oneweek.provider;

import com.golmok.oneweek.entity.Store;
import org.springframework.data.domain.Limit;
import com.golmok.oneweek.provider.Providers.StoreSearchProvider;
import com.golmok.oneweek.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 가게 검색: H2에 seed 된 행정안전부 인허가 실데이터(24,079곳)에서 상호명·주소 부분일치로 찾는다.
 * 설정값은 호환을 위해 {@code golmok.providers.store-search=mock}을 그대로 쓴다(대체 구현 없음).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "golmok.providers.store-search", havingValue = "mock", matchIfMissing = true)
public class DbStoreSearchProvider implements StoreSearchProvider {

    /** 대구 음식점이 2만 건대라 검색 결과를 화면에서 다룰 수 있는 개수로 제한한다. */
    private static final int MAX_RESULTS = 20;

    private final StoreRepository storeRepository;

    @Override
    public List<Store> search(String keyword, String city) {
        String k = keyword == null ? "" : keyword.trim();
        if (k.isEmpty()) return List.of();
        return storeRepository.findByCityAndNameContainingIgnoreCaseOrCityAndAddressContainingIgnoreCase(
                city, k, city, k, Limit.of(MAX_RESULTS));
    }

    @Override
    public Store fromAddress(String address, String city) {
        return storeRepository.save(Store.builder()
                .name(address + " (직접 입력)")
                .category("음식점")
                .address(address)
                .roadAddress(address)
                .city(city)
                .district(guessDistrict(address))
                .latitude(35.8714).longitude(128.6014)   // 대구 시청 기준 좌표(임시)
                .demoData(true)   // 검색으로 찾지 못해 사용자가 직접 입력한 가게 (공공데이터 원본 아님)
                .build());
    }

    private String guessDistrict(String address) {
        for (String gu : List.of("중구", "동구", "서구", "남구", "북구", "수성구", "달서구", "달성군", "군위군")) {
            if (address != null && address.contains(gu)) return gu;
        }
        return null;
    }
}
