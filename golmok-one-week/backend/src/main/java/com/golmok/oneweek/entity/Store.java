package com.golmok.oneweek.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "stores")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Store {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String name;
    private String category;
    private String detailCategoryCode;
    private String detailCategoryName;
    private String categoryAsOf;
    private String address;
    private String roadAddress;
    private Double latitude;
    private Double longitude;
    private String city;
    private String district;
    @Column(name = "is_demo_data") private boolean demoData;
    private LocalDateTime createdAt;

    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
