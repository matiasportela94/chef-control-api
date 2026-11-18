package com.chefcontrol.api.ai.dto;

import com.chefcontrol.application.service.ChatService;
import com.chefcontrol.application.service.ChatService.ChatStatus;

import java.util.List;

/**
 * @param message       el texto para el usuario, armado por el back con lo que pasó de verdad
 * @param actions       lo registrado, cada uno con su deshacer (REGISTERED)
 * @param proposal      lo que se confirmaría con /ai/chat/confirm (NEEDS_CONFIRMATION)
 * @param skipped       lo que no se registró ni se propone, con su motivo
 * @param catalogImport la vista previa de una receta (RECIPE_PREVIEW): llega con la tanda 4, hoy siempre null
 */
public record ChatResponse(ChatStatus status, String message, List<ChatActionResponse> actions,
                           List<ChatOperation> proposal, List<ChatSkip> skipped, Object catalogImport) {

    public static ChatResponse from(ChatService.ChatResult r) {
        return new ChatResponse(r.status(), r.message(),
                r.actions().stream().map(ChatActionResponse::from).toList(),
                r.proposal().stream().map(ChatOperation::from).toList(),
                r.skipped().stream().map(ChatSkip::from).toList(), null);
    }
}
