package com.golmok.oneweek.entity;

import com.golmok.oneweek.entity.Enums.SourceType;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "sources")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Source {
    /** docs/coefficients.md 의 출처 번호와 같은 고정 ID. resources/data/sources.csv 에서 부여한다. */
    @Id
    private Long id;
    @Enumerated(EnumType.STRING) private SourceType sourceType;
    @Column(nullable = false, length = 500) private String title;
    private String organization;
    private String authors;
    private Integer publicationYear;
    @Column(length = 1000) private String url;
    @Column(length = 1000) private String description;
    @Column(length = 1000) private String reliabilityNote;
}
