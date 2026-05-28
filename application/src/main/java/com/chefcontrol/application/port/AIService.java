package com.chefcontrol.application.port;

import java.util.UUID;

public interface AIService {

    AIInterpretation interpret(InterpretRequest request);

    record InterpretRequest(
        String message,
        UUID restaurantId,
        UUID sessionId,
        String catalog
    ) {}

    record AIInterpretation(
        String intent,
        int confidence,
        boolean needsConfirmation,
        Object extractedData,
        String responseToUser
    ) {}
}
