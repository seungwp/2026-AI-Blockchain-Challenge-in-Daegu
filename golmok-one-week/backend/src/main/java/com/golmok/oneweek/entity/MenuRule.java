package com.golmok.oneweek.entity;

import com.golmok.oneweek.entity.Enums.*;
import jakarta.persistence.*;
import lombok.*;

/**
 * 운영 가이드 규칙. menuCategory=COMMON 이면 모든 메뉴에 적용.
 * conditionValue 해석: RAIN=강수확률(%) 이상, HOT=최고기온(℃) 이상, COLD=최저기온(℃) 이하,
 * WEEKEND=없음, FESTIVAL=행사장 거리(m, "1000" 또는 "1000-3000" 구간), COMPETITION=500m 내 동종 점포 수 이상,
 * HOLIDAY=CHUSEOK_EVE/CHUSEOK_DAY/CHUSEOK_PERIOD/CHUSEOK_LAST 중 하나, PRICE_SPIKE=없음(카테고리 품목 중 급등확률 alert=true 하나라도 있으면 발동)
 */
@Entity @Table(name = "menu_rules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MenuRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) private MenuCategory menuCategory;
    @Enumerated(EnumType.STRING) private ConditionType conditionType;
    private String conditionValue;
    @Enumerated(EnumType.STRING) private RecommendationType recommendationType;
    @Column(length = 1000) private String recommendationText;
    private Long sourceId;
    @Enumerated(EnumType.STRING) private Confidence confidence;
}
