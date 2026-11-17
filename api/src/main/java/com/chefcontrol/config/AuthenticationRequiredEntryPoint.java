package com.chefcontrol.config;

import com.chefcontrol.api.exception.ErrorResponse;
import com.chefcontrol.application.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Un pedido sin sesión (sin cookie, vencida o inválida) a una ruta que la pide: 401 con el mismo cuerpo que el resto
 * de los errores. Sin esto Spring respondía 403 vacío, y el front, que manda a /login solo ante un 401, dejaba al
 * usuario adentro con todas las vistas fallando.
 */
@Component
@RequiredArgsConstructor
public class AuthenticationRequiredEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(
                ErrorCode.AUTHENTICATION_REQUIRED, "Authentication required", request.getRequestURI()));
    }
}
