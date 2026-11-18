package com.chefcontrol.api.ai.dto;

import com.chefcontrol.application.service.ChatService;
import com.chefcontrol.domain.chat.ChatOperationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Una operación del chat: la de una alternativa de un NEEDS_CONFIRMATION y, tal cual o editada, lo que se confirma.
 *
 * @param correctsActionId la acción que corrige ("no, eran 5 kg"): al confirmar se deshace con sus reglas de siempre;
 *                         null si no corrige nada
 */
public record ChatOperation(@NotNull ChatOperationType type,
                            @NotNull @Size(max = 100) List<@Valid @NotNull ChatOperationItem> items,
                            UUID correctsActionId) {

    public static ChatOperation from(ChatService.Operation op) {
        return new ChatOperation(op.type(), op.items().stream().map(ChatOperationItem::from).toList(),
                op.correctsActionId());
    }

    public ChatService.Operation toCommand() {
        return new ChatService.Operation(type, items.stream().map(ChatOperationItem::toCommand).toList(),
                correctsActionId);
    }
}
