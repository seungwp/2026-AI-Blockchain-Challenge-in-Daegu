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
    private final JusoAddressProvider addressProvider;

    @Override
    public List<Store> search(String keyword, String city) {
        String k = keyword == null ? "" : keyword.trim();
        if (k.isEmpty()) return List.of();
        return storeRepository.findByCityAndNameContainingIgnoreCaseOrCityAndAddressContainingIgnoreCase(
                city, k, city, k, Limit.of(MAX_RESULTS));
    }

    @Override
    public Store fromAddress(String address, String city) {
        if (!"대구광역시".equals(city)) throw new IllegalArgumentException("대구광역시 주소만 지원합니다.");
        List<JusoAddressProvider.Address> candidates = addressProvider.search(address);
        if (candidates.isEmpty()) throw new IllegalArgumentException("대구 주소를 찾지 못했습니다. 도로명과 건물번호를 입력해주세요.");
        if (candidates.size() != 1) throw new IllegalArgumentException("주소가 여러 곳 검색되었습니다. 도로명과 건물번호를 더 정확히 입력해주세요.");
        var found = candidates.getFirst();
        // 같은 건물이라도 다른 음식점을 사용자 가게로 취급하지 않는다. 기존 좌표의
        // 직접 재사용은 위치가 유일하게 일치하는 경우에만 허용한다.
        List<Store> sameAddress = storeRepository.findByCityAndRoadAddressStartingWith(city, found.roadAddress()).stream()
                .filter(s -> baseAddress(s.getRoadAddress()).equals(found.roadAddress()))
                .filter(s -> !s.isDemoData() && s.getLatitude() != null && s.getLongitude() != null).toList();
        List<String> locations = sameAddress.stream().map(s -> s.getLatitude() + ":" + s.getLongitude()).distinct().toList();
        Store location = locations.size() == 1 ? sameAddress.getFirst() : null;
        return storeRepository.save(Store.builder()
                .name(address + " (직접 입력)")
                .category("음식점")
                .address(found.address())
                .roadAddress(found.roadAddress())
                .city(city)
                .district(found.district())
                .latitude(location == null ? null : location.getLatitude())
                .longitude(location == null ? null : location.getLongitude())
                .demoData(true)   // 검색으로 찾지 못해 사용자가 직접 입력한 가게 (공공데이터 원본 아님)
                .build());
    }

    private static String baseAddress(String address) {
        return address == null ? "" : address.split("[,（(]", 2)[0].trim();
    }

}
