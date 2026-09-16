package com.golmok.oneweek.repository;

import com.golmok.oneweek.entity.Store;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StoreRepository extends JpaRepository<Store, Long> {

    /** 상호명 또는 주소 부분일치 검색. 대구 전체가 2만 건대라 상한을 둔다. */
    List<Store> findByCityAndNameContainingIgnoreCaseOrCityAndAddressContainingIgnoreCase(
            String city1, String name, String city2, String address, Limit limit);

    /** 반경 집계용 경계상자 조회. 도시 전체를 메모리로 읽지 않기 위해 좌표로 먼저 좁힌다. */
    List<Store> findByCityAndLatitudeBetweenAndLongitudeBetween(
            String city, double minLat, double maxLat, double minLon, double maxLon);
}
