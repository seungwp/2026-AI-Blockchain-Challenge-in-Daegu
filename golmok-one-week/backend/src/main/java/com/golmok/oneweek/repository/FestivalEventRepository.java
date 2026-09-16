package com.golmok.oneweek.repository;

import com.golmok.oneweek.entity.FestivalEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface FestivalEventRepository extends JpaRepository<FestivalEvent, Long> {
    /** 기간이 [start, end]와 겹치는 행사: startDate <= end AND endDate >= start */
    List<FestivalEvent> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate end, LocalDate start);
}
