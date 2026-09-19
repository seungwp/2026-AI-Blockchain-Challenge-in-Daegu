package com.golmok.oneweek.service;

import com.golmok.oneweek.dto.ReportDtos.FestivalInfo;
import com.golmok.oneweek.entity.Store;
import com.golmok.oneweek.entity.Enums.DataStatus;
import com.golmok.oneweek.provider.Providers.FestivalProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 리포트의 주변 행사 조각. 행사 목록은 {@link FestivalProvider}, 거리 계산은 {@link CommercialAreaService#distanceMeters}. */
@Service
@RequiredArgsConstructor
public class FestivalService {

    private final FestivalProvider festivalProvider;

    /**
     * 가게에서 3km 이내 행사만 담고 거리·영향 구분(1km/3km)을 채운다.
     * 그보다 먼 행사는 권고 근거가 없어(docs/coefficients.md 축제 1km 계수) 목록에서 뺀다.
     */
    public List<FestivalInfo> nearby(Store store, LocalDate start, LocalDate end) {
        List<FestivalInfo> out = new ArrayList<>();
        if (store.getLatitude() == null || store.getLongitude() == null) return out;
        for (FestivalInfo f : festivalProvider.findFestivals(start, end)) {
            if (f.latitude() == null || f.longitude() == null) continue;
            int distance = (int) Math.round(CommercialAreaService.distanceMeters(
                    store.getLatitude(), store.getLongitude(), f.latitude(), f.longitude()));
            if (distance > 3000) continue;
            String impact = distance <= 1000 ? "직접 영향 가능" : "간접 영향 가능";
            String note = distance <= 1000
                    ? "행사장과 가까워 방문객 유입 가능성이 있으며, 교통·주차 혼잡도 함께 고려가 필요합니다."
                    : "행사장과 다소 떨어져 있어 간접적인 유동 변화 가능성이 있습니다. (중간 신뢰도)";
            out.add(new FestivalInfo(f.id(), f.name(), f.startDate(), f.endDate(), f.locationName(), f.address(),
                    f.latitude(), f.longitude(), distance, impact, note, f.isDemoData(), f.sourceId(),
                    f.playTime(), f.fee(), f.contact(), f.fetchedAt(),
                    f.isDemoData() ? DataStatus.DEMO : DataStatus.SNAPSHOT,
                    f.fetchedAt() == null ? null : f.fetchedAt().toString()));
        }
        out.sort(Comparator.comparing(FestivalInfo::distanceMeters));
        return out;
    }
}
