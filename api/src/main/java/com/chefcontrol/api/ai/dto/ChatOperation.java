package com.chefcontrol.api.ai.dto;

import com.chefcontrol.application.service.ChatService;
import com.chefcontrol.domain.chat.ChatOperationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Una operación del chat: la propuesta de un NEEDS_CONFIRMATION y, tal cual o editada, lo que se confirma. */
public record ChatOperation(@NotNull ChatOperationType type,
                            @NotNull @Size(max = 100) List<@Valid @NotNull ChatOperationItem> items) {

    public static ChatOperation from(ChatService.Operation op) {
        return new ChatOperation(op.type(), op.items().stream().map(ChatOperationItem::from).toList());
    }

    public ChatService.Operation toCommand() {
        return new ChatService.Operation(type, items.stream().map(ChatOperationItem::toCommand).toList());
    }
}
