package com.golmok.oneweek.provider;

import com.golmok.oneweek.dto.ReportDtos.FestivalInfo;
import com.golmok.oneweek.entity.FestivalEvent;
import com.golmok.oneweek.provider.Providers.FestivalProvider;
import com.golmok.oneweek.repository.FestivalEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** H2에 seed 된 대구 축제 실데이터(TourAPI)에서 기간이 겹치는 것을 돌려준다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "golmok.providers.festival", havingValue = "mock", matchIfMissing = true)
public class MockFestivalProvider implements FestivalProvider {

    private final FestivalEventRepository festivalRepository;

    @Override
    public List<FestivalInfo> findFestivals(LocalDate start, LocalDate end) {
        return festivalRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(end, start).stream()
                .map(this::toInfo).toList();
    }

    private FestivalInfo toInfo(FestivalEvent f) {
        // 거리·영향 구분은 가게 좌표를 아는 서비스 단계에서 채운다.
        return new FestivalInfo(f.getId(), f.getName(), f.getStartDate(), f.getEndDate(), f.getLocationName(),
                f.getAddress(), f.getLatitude(), f.getLongitude(), null, null, f.getImpactNote(),
                f.isDemoData(), f.getSourceId());
    }
}
