package com.golmok.oneweek.entity;

import com.golmok.oneweek.entity.Enums.MenuCategory;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "analysis_reports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnalysisReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long storeId;
    private String mainMenu;
    @Enumerated(EnumType.STRING) private MenuCategory menuCategory;
    private LocalDate analysisStartDate;
    private LocalDate analysisEndDate;
    @Column(length = 1000) private String summary;
    @Lob private String weeklyWeatherJson;
    @Lob private String commercialAreaJson;
    @Lob private String festivalJson;
    @Lob private String recommendationsJson;
    @Column(name = "is_demo_data") private boolean demoData;
    private LocalDateTime createdAt;

    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
