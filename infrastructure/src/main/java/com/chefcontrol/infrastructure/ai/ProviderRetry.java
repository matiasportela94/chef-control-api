package com.chefcontrol.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientResponseException;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Un reintento, con una espera corta, cuando el proveedor dice que está ocupado ("high demand", límite por minuto): en
 * la prueba del 2026-11-18 era la única falla de Gemini y salía al usuario como "No pude interpretar el mensaje". Nada
 * más se reintenta. Queda en WARN para ver si se vuelve frecuente.
 */
@Slf4j
final class ProviderRetry {

    static final long DELAY_MS = 1000;

    private ProviderRetry() {}

    /** @param busy los status de "ocupado" de ese proveedor */
    static JsonNode once(String provider, Set<Integer> busy, Supplier<JsonNode> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            if (!busy.contains(e.getStatusCode().value())) throw e;
            log.warn("[{}] busy ({}): retrying once in {} ms", provider, e.getStatusCode().value(), DELAY_MS);
            try {
                Thread.sleep(DELAY_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw e;
            }
            return request.get();
        }
    }
}
