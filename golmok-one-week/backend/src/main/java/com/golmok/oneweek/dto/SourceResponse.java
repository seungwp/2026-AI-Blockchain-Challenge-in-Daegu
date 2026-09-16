package com.golmok.oneweek.dto;

import com.golmok.oneweek.entity.Enums.SourceType;
import com.golmok.oneweek.entity.Source;

public record SourceResponse(Long id, SourceType sourceType, String title, String organization, String authors,
                             Integer publicationYear, String url, String description, String reliabilityNote) {
    public static SourceResponse from(Source s) {
        return new SourceResponse(s.getId(), s.getSourceType(), s.getTitle(), s.getOrganization(), s.getAuthors(),
                s.getPublicationYear(), s.getUrl(), s.getDescription(), s.getReliabilityNote());
    }
}
