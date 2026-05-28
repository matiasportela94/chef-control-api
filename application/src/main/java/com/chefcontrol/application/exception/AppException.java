package com.chefcontrol.application.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static AppException notFound(ErrorCode errorCode, String message) {
        return new AppException(errorCode, message);
    }

    public static AppException forbidden(ErrorCode errorCode, String message) {
        return new AppException(errorCode, message);
    }

    public static AppException conflict(ErrorCode errorCode, String message) {
        return new AppException(errorCode, message);
    }

    public static AppException badRequest(ErrorCode errorCode, String message) {
        return new AppException(errorCode, message);
    }

    public static AppException unprocessable(ErrorCode errorCode, String message) {
        return new AppException(errorCode, message);
    }
}
