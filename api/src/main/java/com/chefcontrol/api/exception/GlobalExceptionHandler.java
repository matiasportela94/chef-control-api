package com.chefcontrol.api.exception;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.exception.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException ex, HttpServletRequest request) {
        log.warn("DomainException: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(ErrorCode.UNPROCESSABLE, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
        log.warn("AppException [{}]: {}", ex.getErrorCode(), ex.getMessage());
        HttpStatus status = toHttpStatus(ex.getErrorCode().getCategory());
        return ResponseEntity
                .status(status)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN) // no 401: el front manda a /login ante un 401
                .body(ErrorResponse.of(ErrorCode.INVALID_CREDENTIALS,
                        "Invalid email or password", request.getRequestURI()));
    }

    /**
     * El limite de multipart corta el request antes de llegar al servicio, asi que sin esto
     * una foto grande devuelve 500 en vez de decir que es grande.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(HttpServletRequest request) {
        // El límite es global: el código dice qué era lo que no entraba.
        String uri = request.getRequestURI();
        ErrorCode code = uri.endsWith("/ai/transcribe") ? ErrorCode.AI_AUDIO_TOO_LARGE
                : uri.contains("/attachments") || uri.endsWith("/ai/chat/documents") ? ErrorCode.ATTACHMENT_TOO_LARGE
                : ErrorCode.IMAGE_TOO_LARGE;
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(code, "The file is too large", request.getRequestURI()));
    }

    /** Un multipart sin el archivo esperado: 400, no el 500 del handler genérico. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex,
                                                           HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR,
                        "Missing part: " + ex.getRequestPartName(), request.getRequestURI()));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(ErrorCode.FORBIDDEN,
                        "Access denied", request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, detail, request.getRequestURI()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                    HttpServletRequest request) {
        String detail = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, detail, request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR,
                        "An unexpected error occurred", request.getRequestURI()));
    }

    private static HttpStatus toHttpStatus(ErrorCode.Category category) {
        return switch (category) {
            case NOT_FOUND     -> HttpStatus.NOT_FOUND;
            case UNAUTHORIZED  -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN     -> HttpStatus.FORBIDDEN;
            case CONFLICT      -> HttpStatus.CONFLICT;
            case BAD_REQUEST   -> HttpStatus.BAD_REQUEST;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case INTERNAL      -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
