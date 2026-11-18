package com.chefcontrol.api.ai.dto;

import com.chefcontrol.application.service.ChatHistoryService.ChatExchangeView;

import java.time.Instant;
import java.util.UUID;

/**
 * Un intercambio de la conversación de hoy: lo que dijo el usuario y lo que se respondió.
 *
 * @param userMessage null si es el resultado de un "Confirmar"
 * @param response    como salió, con las acciones como están ahora (undoneAt al día)
 */
public record ChatExchangeResponse(UUID id, Instant createdAt, String userMessage, ChatResponse response) {

    public static ChatExchangeResponse from(ChatExchangeView v) {
        return new ChatExchangeResponse(v.id(), v.createdAt(), v.userMessage(), ChatResponse.from(v.response()));
    }
}
