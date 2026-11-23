package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AIService.ChatHistoryTurn;
import com.chefcontrol.application.port.AIService.HistoryAction;
import com.chefcontrol.application.port.ChatExchangeRepository;
import com.chefcontrol.application.port.ChatExchangeRepository.ChatExchange;
import com.chefcontrol.application.port.ChatExchangeRepository.Kind;
import com.chefcontrol.application.port.ChatExchangeRepository.StoredReply;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.application.service.ChatService.ChatResult;
import com.chefcontrol.domain.chat.ChatAction;
import com.chefcontrol.domain.chat.ChatActionRepository;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.shared.time.ChefControlTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * La memoria del chat (F19, tanda 5). La conversación la guarda el back, por usuario y local, así la pantalla, el
 * prompt y WhatsApp ven lo mismo. Es la de hoy: desde la medianoche argentina o desde la última "Nueva conversación"
 * (una fila marcador, que no borra nada). Las filas se guardan 30 días para revisar cómo interpreta el modelo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    /** Lo que va al prompt: los últimos 5 intercambios (unos 10 mensajes). */
    static final int REMEMBERED_EXCHANGES = 5;

    private final ChatExchangeRepository chatExchangeRepository;
    private final ChatActionRepository chatActionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    /** La conversación de hoy del usuario en el local activo, con sus acciones como están ahora. */
    Conversation currentConversation() {
        UUID restaurantId = TenantContext.require();
        Instant midnight = LocalDate.now(clock.withZone(ChefControlTime.DEFAULT_ZONE))
                .atStartOfDay(ChefControlTime.DEFAULT_ZONE).toInstant();
        List<ChatExchange> today = chatExchangeRepository.findChatExchangesSince(
                currentUserProvider.currentUserId(), restaurantId, midnight);
        int start = 0;
        for (int i = 0; i < today.size(); i++) {
            if (today.get(i).kind() == Kind.NEW_CONVERSATION) start = i + 1;
        }
        List<ChatExchange> exchanges = today.subList(start, today.size());
        List<UUID> ids = exchanges.stream().filter(e -> e.reply() != null)
                .flatMap(e -> Stream.concat(e.reply().actionIds().stream(), e.reply().undoneActionIds().stream()))
                .distinct().toList();
        Map<UUID, ChatAction> actions = new HashMap<>();
        chatActionRepository.findAllByIdInAndRestaurantId(ids, restaurantId).forEach(a -> actions.put(a.getId(), a));
        return new Conversation(exchanges, actions);
    }

    /**
     * Guarda el intercambio. Si falla, lo registrado ya está registrado: se loguea y la respuesta sale igual.
     *
     * @param userMessage null en una confirmación
     */
    void rememberChatExchange(Kind kind, String userMessage, ChatResult result) {
        try {
            chatExchangeRepository.saveChatExchange(new ChatExchange(null, TenantContext.require(),
                    currentUserProvider.currentUserId(), kind, userMessage, result.status(),
                    new StoredReply(result.message(), ids(result.actions()), ids(result.undone()), result.proposals(),
                            result.skipped(), result.recipe(), result.invoice(), result.salesReport()),
                    clock.instant()));
        } catch (RuntimeException e) {
            log.error("[Chat] could not remember the exchange: {}", e.getMessage(), e);
        }
    }

    /** Lo que se ve en el chat: la conversación de hoy, con cada acción como está ahora (deshecha o no). */
    public List<ChatExchangeView> listChatMessages() {
        Conversation c = currentConversation();
        return c.exchanges().stream().map(e -> new ChatExchangeView(e.id(), e.createdAt(), e.userMessage(),
                new ChatResult(e.status(), e.reply().message(), live(e.reply().actionIds(), c.actions()),
                        live(e.reply().undoneActionIds(), c.actions()), e.reply().proposals(), e.reply().skipped(),
                        e.reply().recipe(), e.reply().invoice(), e.reply().salesReport()))).toList();
    }

    static final String CANCELLED = "Listo, no registré nada.";

    /**
     * "Cancelar" la pregunta pendiente: queda en la conversación, así un "sí" después no registra lo cancelado. Sin
     * nada pendiente no guarda nada.
     */
    public ChatResult cancelChatProposal() {
        if (currentConversation().pendingProposals().isEmpty()) {
            return ChatResult.of(ChatService.ChatStatus.NOT_REGISTERED, "No había nada para cancelar.");
        }
        ChatResult result = ChatResult.of(ChatService.ChatStatus.NOT_REGISTERED, CANCELLED);
        rememberChatExchange(Kind.CANCELLATION, null, result);
        return result;
    }

    /** "Nueva conversación": la memoria arranca de cero; lo anterior queda guardado. */
    public void startChatConversation() {
        chatExchangeRepository.saveChatExchange(new ChatExchange(null, TenantContext.require(),
                currentUserProvider.currentUserId(), Kind.NEW_CONVERSATION, null, null, null, clock.instant()));
    }

    /** @return cuántos intercambios borró: los de más de {@code retentionDays} días */
    public int purgeChatHistory(int retentionDays) {
        return chatExchangeRepository.deleteChatExchangesBefore(clock.instant().minus(Duration.ofDays(retentionDays)));
    }

    private static List<UUID> ids(List<ChatAction> actions) {
        return actions.stream().map(ChatAction::getId).toList();
    }

    private static List<ChatAction> live(List<UUID> ids, Map<UUID, ChatAction> actions) {
        return ids.stream().map(actions::get).filter(Objects::nonNull).toList();
    }

    /** @param userMessage null si es el resultado de un "Confirmar" o un "Cancelar" */
    public record ChatExchangeView(UUID id, Instant createdAt, String userMessage, ChatResult response) {}

    /** Los intercambios de hoy (sin marcadores), del más viejo al más nuevo, y sus acciones como están ahora. */
    record Conversation(List<ChatExchange> exchanges, Map<UUID, ChatAction> actions) {

        /**
         * La acción viva más nueva de la conversación de ese tipo y de alguno de esos insumos o platos, o null. Por el
         * nombre en su resumen ("Compra: 3 kg Tomate a $ 1.500"): la acción no guarda el insumo.
         * ponytail: "Tomate" también encuentra "Tomate perita"; el error es preguntar de más, nunca registrar mal.
         */
        ChatAction latestLiveAction(com.chefcontrol.domain.chat.ChatOperationType type, List<String> names) {
            for (int i = exchanges.size() - 1; i >= 0; i--) {
                ChatExchange e = exchanges.get(i);
                if (e.reply() == null) continue;
                List<UUID> ids = e.reply().actionIds();
                for (int j = ids.size() - 1; j >= 0; j--) {
                    ChatAction a = actions.get(ids.get(j));
                    if (a == null || a.isUndone() || a.getType() != type) continue;
                    String summary = CatalogImportService.key(a.getSummary());
                    if (names.stream().anyMatch(n -> n != null && summary.contains(CatalogImportService.key(n)))) return a;
                }
            }
            return null;
        }

        /**
         * Las alternativas de la pregunta pendiente: el último NEEDS_CONFIRMATION, si después solo hubo NOT_UNDERSTOOD
         * (una falla del modelo o un mensaje sin sentido no la dejan atrás: pasó con un 503 justo antes de "es el
         * total"). Cualquier otro intercambio (un mensaje que no la contesta, un "Cancelar") sí.
         */
        List<ChatService.Proposal> pendingProposals() {
            for (int i = exchanges.size() - 1; i >= 0; i--) {
                ChatExchange e = exchanges.get(i);
                if (e.status() == ChatService.ChatStatus.NOT_UNDERSTOOD) continue;
                return e.status() == ChatService.ChatStatus.NEEDS_CONFIRMATION && e.reply() != null
                        ? e.reply().proposals() : List.of();
            }
            return List.of();
        }

        /**
         * Lo que va al prompt: los últimos intercambios, con las acciones numeradas en orden (1, 2, 3…). Números cortos y
         * no ids: el modelo copia mal un UUID. Solo se puede corregir una acción que se le mostró.
         */
        PromptHistory promptHistory() {
            // Lo cancelado no vuelve al prompt, ni la pregunta ni el intercambio que la canceló: con la pregunta en el
            // historial, un "sí" después la revivió y registró la compra cancelada (e2e del 2026-11-19).
            List<ChatExchange> remembered = new ArrayList<>();
            for (ChatExchange e : exchanges) {
                if (!cancelled(e)) {
                    remembered.add(e);
                    continue;
                }
                for (int i = remembered.size() - 1; i >= 0; i--) {
                    ChatService.ChatStatus status = remembered.get(i).status();
                    if (status == ChatService.ChatStatus.NOT_UNDERSTOOD) continue;
                    if (status == ChatService.ChatStatus.NEEDS_CONFIRMATION) remembered.remove(i);
                    break;
                }
            }
            List<ChatHistoryTurn> turns = new ArrayList<>();
            Map<Integer, UUID> numbers = new HashMap<>();
            int n = 0;
            for (ChatExchange e : remembered.subList(Math.max(0, remembered.size() - REMEMBERED_EXCHANGES), remembered.size())) {
                List<HistoryAction> listed = new ArrayList<>();
                for (UUID id : e.reply().actionIds()) {
                    ChatAction a = actions.get(id);
                    if (a == null) continue;
                    numbers.put(++n, id);
                    listed.add(new HistoryAction(n, a.getSummary(), a.isUndone()));
                }
                turns.add(new ChatHistoryTurn(e.userMessage(), e.reply().message(), listed));
            }
            return new PromptHistory(turns, numbers);
        }
    }

    /** Un "Cancelar" (el botón) o un "no" por texto contra la pregunta pendiente. */
    private static boolean cancelled(ChatExchange e) {
        return e.kind() == Kind.CANCELLATION || e.kind() == Kind.MESSAGE && e.reply() != null
                && e.status() == ChatService.ChatStatus.NOT_REGISTERED && CANCELLED.equals(e.reply().message());
    }

    record PromptHistory(List<ChatHistoryTurn> turns, Map<Integer, UUID> actionNumbers) {

        /** Lo que escribió el usuario en los intercambios que vio el modelo: de ahí puede salir un precio ya dicho. */
        String userMessages() {
            return String.join(" ", turns.stream().map(ChatHistoryTurn::userMessage).filter(Objects::nonNull).toList());
        }
    }
}
