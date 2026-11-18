package com.chefcontrol.application.port;

import com.chefcontrol.application.service.ChatRecipeService.RecipePreview;
import com.chefcontrol.application.service.ChatService.ChatStatus;
import com.chefcontrol.application.service.ChatService.Proposal;
import com.chefcontrol.application.service.ChatService.Skipped;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** La conversación del chat: una fila por intercambio (un mensaje y su respuesta). Tabla chat_messages. */
public interface ChatExchangeRepository {

    ChatExchange saveChatExchange(ChatExchange exchange);

    /** Los de ese usuario en ese local desde {@code since}, del más viejo al más nuevo. */
    List<ChatExchange> findChatExchangesSince(UUID userId, UUID restaurantId, Instant since);

    /** @return cuántos borró */
    int deleteChatExchangesBefore(Instant cutoff);

    /**
     * CONFIRMATION = el resultado de un "Confirmar"; CANCELLATION = un "Cancelar" (la pregunta deja de estar pendiente);
     * NEW_CONVERSATION = donde arranca la memoria, sin borrar nada.
     */
    enum Kind { MESSAGE, CONFIRMATION, CANCELLATION, NEW_CONVERSATION }

    /**
     * @param userMessage null salvo en MESSAGE
     * @param reply       null en NEW_CONVERSATION
     */
    record ChatExchange(UUID id, UUID restaurantId, UUID userId, Kind kind, String userMessage, ChatStatus status,
                        StoredReply reply, Instant createdAt) {}

    /** La respuesta como salió; las acciones por id, para leerlas con su estado de ahora (deshecha o no). */
    record StoredReply(String message, List<UUID> actionIds, List<UUID> undoneActionIds, List<Proposal> proposals,
                       List<Skipped> skipped, RecipePreview recipe) {}
}
