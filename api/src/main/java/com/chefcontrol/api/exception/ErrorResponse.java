package com.chefcontrol.api.exception;

import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.shared.time.ChefControlTime;

import java.time.Instant;

public record ErrorResponse(
        String errorCode,
        String message,
        Instant timestamp,
        String path
) {
    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        return new ErrorResponse(errorCode.name(), message, ChefControlTime.nowInstant(), path);
    }
}
