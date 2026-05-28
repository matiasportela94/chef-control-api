package com.chefcontrol.infrastructure.ai;

import com.chefcontrol.application.port.AIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Placeholder until the AI microservice adapter is implemented (Fase 2).
 */
@Slf4j
@Service
@ConditionalOnMissingBean(value = AIService.class, ignored = NoOpAIService.class)
public class NoOpAIService implements AIService {

    @Override
    public AIInterpretation interpret(InterpretRequest request) {
        log.warn("[AI no-op] interpret called — Fase 2 not implemented yet");
        return new AIInterpretation("unknown", 0, false, null, "Lo siento, el servicio de IA no está disponible.");
    }
}
