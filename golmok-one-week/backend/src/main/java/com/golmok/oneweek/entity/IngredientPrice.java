package com.golmok.oneweek.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/** KAMIS 대구 소매가격 스냅샷 + 급등확률 모델 결과 1건. */
@Entity @Table(name = "ingredient_prices")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IngredientPrice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String item;
    /** 소매 표준 단위. 예: 1포기, 1kg, 100g, 30구(1판). */
    private String unit;
    private Double price;
    private LocalDate priceDate;
    /** 향후 7일 내 10% 이상 상승 확률 (pipeline/price_spike_model.py). */
    private Double probSpike;
    private boolean alert;
    /** 평년 가격 대비 비율. 0.16 = 평년보다 16% 높음. */
    private Double vsNormalRatio;
    private Long sourceId;
    @Column(name = "is_demo_data") private boolean demoData;
}
