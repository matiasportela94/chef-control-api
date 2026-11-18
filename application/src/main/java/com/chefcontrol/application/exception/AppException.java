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

    /**
     * El local de la sesión ya no existe o está deshabilitado (lo borraron con la sesión abierta): esa sesión no
     * sirve más. 401 para que el front la limpie y mande a /login, en vez de un 500.
     */
    public static AppException sessionRestaurantGone() {
        return new AppException(ErrorCode.AUTHENTICATION_REQUIRED, "The session's restaurant no longer exists");
    }
}
