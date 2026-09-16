package com.golmok.oneweek.service;

import com.golmok.oneweek.dto.ReportDtos.CommercialArea;
import com.golmok.oneweek.entity.Store;
import com.golmok.oneweek.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 주변 상권 요약. 반경 500m 내 실데이터 음식점 목록(행정안전부 인허가 정보)을 집계한다. */
@Service
@RequiredArgsConstructor
public class CommercialAreaService {

    private static final int RADIUS_METERS = 500;

    private final StoreRepository storeRepository;

    public CommercialArea summarize(Store store) {
        String city = store.getCity() == null ? "대구광역시" : store.getCity();
        // 반경 500m 를 덮는 경계상자로 먼저 좁힌다 (distanceMeters 와 같은 도-미터 환산 사용)
        double dLat = RADIUS_METERS / 111_000.0;
        double dLon = RADIUS_METERS / 88_800.0;
        List<Store> all = storeRepository.findByCityAndLatitudeBetweenAndLongitudeBetween(
                city, store.getLatitude() - dLat, store.getLatitude() + dLat,
                store.getLongitude() - dLon, store.getLongitude() + dLon);
        int total = 0, same = 0;
        for (Store s : all) {
            if (s.getId().equals(store.getId())) continue;
            if (distanceMeters(store, s) > RADIUS_METERS) continue;
            total++;
            if (sameKind(store.getCategory(), s.getCategory())) same++;
        }
        String level = same >= 3 ? "높음" : same >= 1 ? "보통" : "낮음";
        String note = "반경 %dm 기준 음식점 %d곳, 유사 업종 %d곳으로 경쟁 강도는 '%s' 수준입니다. 운영 참고용 집계입니다."
                .formatted(RADIUS_METERS, total, same, level);
        return new CommercialArea(store.getDistrict(), dongOf(store), total, same, level, note, false,
                List.of(SourceCatalog.STORE_LICENSE_ID));
    }

    /** 업종 문자열의 마지막 분류가 같으면 유사 업종으로 본다. */
    private boolean sameKind(String a, String b) {
        if (a == null || b == null) return false;
        return leaf(a).equals(leaf(b));
    }

    private String leaf(String category) {
        String[] parts = category.split(">");
        return parts[parts.length - 1].trim();
    }

    /**
     * 주소에서 법정동 이름을 뽑는다.
     * "대구광역시 달서구 두류동 620-12" -> "두류동". 번지·건물명으로 끝나는 주소가 많아
     * 마지막 토큰이 아니라 동/읍/면/가/리 로 끝나는 첫 토큰을 찾는다.
     */
    private String dongOf(Store store) {
        if (store.getAddress() == null) return null;
        for (String part : store.getAddress().trim().split(" ")) {
            if (part.length() >= 2 && part.matches(".*[동읍면가리]$") && !part.equals(store.getDistrict())) {
                return part;
            }
        }
        return null;
    }

    /** 위·경도 간 대략 거리(m). 대구 위도 기준 단순 환산. */
    public static double distanceMeters(Store a, Store b) {
        if (a.getLatitude() == null || b.getLatitude() == null) return Double.MAX_VALUE;
        return distanceMeters(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
    }

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dy = (lat1 - lat2) * 111_000;
        double dx = (lon1 - lon2) * 88_800;   // 위도 35.87도 기준 경도 1도 ≒ 88.8km
        return Math.sqrt(dx * dx + dy * dy);
    }
}
