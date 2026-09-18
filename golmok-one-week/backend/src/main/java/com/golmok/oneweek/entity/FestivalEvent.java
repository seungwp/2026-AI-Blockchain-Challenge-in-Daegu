package com.golmok.oneweek.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity @Table(name = "festival_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FestivalEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private String locationName;
    private String address;
    private Double latitude;
    private Double longitude;
    private Long sourceId;
    @Column(length = 2000) private String playTime;
    @Column(length = 2000) private String fee;
    @Column(length = 1000) private String contact;
    private LocalDate fetchedAt;
    @Column(name = "is_demo_data") private boolean demoData;
}
