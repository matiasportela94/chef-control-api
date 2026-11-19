package com.chefcontrol.api.ai.dto;

import com.chefcontrol.domain.chat.ChatAction;
import com.chefcontrol.domain.chat.ChatOperationType;

import java.time.Instant;
import java.util.UUID;

/**
 * Algo que registró el chat.
 *
 * @param actionId      el id para deshacer
 * @param entityId      según type: la compra, el waste_event, la venta, el conteo o la producción
 * @param undoableUntil hasta cuándo quien lo cargó lo deshace sin el permiso de borrar; después, o para otro usuario,
 *                      hace falta ese permiso
 */
public record ChatActionResponse(UUID actionId, ChatOperationType type, UUID entityId, String summary,
                                 Instant createdAt, Instant undoableUntil, Instant undoneAt,
                                 /** Lo que hay que avisar al deshacerla y que el deshacer no arregla solo; null casi siempre. */
                                 String undoNote) {

    public static ChatActionResponse from(ChatAction a) {
        return new ChatActionResponse(a.getId(), a.getType(), a.getEntityId(), a.getSummary(), a.getCreatedAt(),
                a.undoableUntil(), a.getUndoneAt(), a.getUndoNote());
    }
}
