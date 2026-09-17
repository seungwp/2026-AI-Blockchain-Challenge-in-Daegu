package com.golmok.oneweek.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ChatDtos {
    private ChatDtos() {}

    public record ChatMessage(String role, String content) {}

    public record ChatRequest(
            @NotBlank(message = "질문을 입력해주세요.") @Size(max = 300) String question,
            List<ChatMessage> history) {}

    public record ChatResponse(String answer) {}
}
