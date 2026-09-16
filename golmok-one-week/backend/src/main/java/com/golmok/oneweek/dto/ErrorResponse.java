package com.golmok.oneweek.dto;

import java.time.LocalDateTime;

public record ErrorResponse(LocalDateTime timestamp, int status, String code, String message) {
    public static ErrorResponse of(int status, String code, String message) {
        return new ErrorResponse(LocalDateTime.now().withNano(0), status, code, message);
    }
}
