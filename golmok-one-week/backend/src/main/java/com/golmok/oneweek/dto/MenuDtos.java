package com.golmok.oneweek.dto;

import com.golmok.oneweek.entity.Enums.Confidence;
import com.golmok.oneweek.entity.Enums.MenuCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class MenuDtos {
    private MenuDtos() {}

    public record ClassifyRequest(
            @NotBlank(message = "대표 메뉴를 입력해주세요.") @Size(max = 100) String menuName,
            @Size(max = 200) String storeCategory) {}

    public record ClassifyResponse(MenuCategory menuCategory, Confidence confidence, List<String> matchedKeywords, boolean isDemoData) {}
}
