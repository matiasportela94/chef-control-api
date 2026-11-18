package com.chefcontrol.api.ai;

import com.chefcontrol.api.ai.dto.ChatActionResponse;
import com.chefcontrol.api.ai.dto.ChatOperation;
import com.chefcontrol.api.ai.dto.ChatResponse;
import com.chefcontrol.api.plan.RequiresFeature;
import com.chefcontrol.application.service.ChatService;
import com.chefcontrol.domain.plan.Feature;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.chefcontrol.api.shared.Authorities.permissionsOf;

/**
 * El chat de la IA (F19). Además de AI_USE, cada operación pide el permiso de su pantalla, pero eso lo decide
 * {@link ChatService} por operación: sin el permiso esa parte no se registra y la respuesta lo dice, en vez de un 403
 * de todo el mensaje.
 */
@RestController
@RequestMapping("/ai/chat")
@RequiresFeature(Feature.AI_INTERPRETER)
@RequiredArgsConstructor
public class AIChatController {

    private final ChatService chatService;

    /** Interpreta y resuelve: registra en el acto (con deshacer), pide confirmación o dice por qué no. */
    @PostMapping
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    public ResponseEntity<ChatResponse> sendChatMessage(@Valid @RequestBody SendChatMessageRequest request,
                                                        Authentication authentication) {
        return ResponseEntity.ok(ChatResponse.from(
                chatService.sendChatMessage(request.message().trim(), permissionsOf(authentication))));
    }

    /** Registra la propuesta de un NEEDS_CONFIRMATION, tal cual o editada. Se revalida todo. */
    @PostMapping("/confirm")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    public ResponseEntity<ChatResponse> confirmChatOperations(@Valid @RequestBody ConfirmChatOperationsRequest request,
                                                              Authentication authentication) {
        return ResponseEntity.ok(ChatResponse.from(chatService.confirmChatOperations(
                request.operations().stream().map(ChatOperation::toCommand).toList(), permissionsOf(authentication))));
    }

    /**
     * Quien lo cargó, durante 15 minutos, sin el permiso de borrar; después, o lo de otro, con el permiso de borrar de
     * esa operación (CHAT_UNDO_NOT_ALLOWED si falta).
     */
    @PostMapping("/actions/{actionId}/undo")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    public ResponseEntity<ChatActionResponse> undoChatAction(@PathVariable UUID actionId,
                                                             Authentication authentication) {
        return ResponseEntity.ok(ChatActionResponse.from(
                chatService.undoChatAction(actionId, permissionsOf(authentication))));
    }

    /** 2000 y no los 500 de /ai/interpret: un audio de 2 minutos transcripto pasa los 500 caracteres. */
    record SendChatMessageRequest(@NotBlank @Size(max = 2000) String message) {}

    record ConfirmChatOperationsRequest(@NotNull @Size(min = 1, max = 20) List<@Valid @NotNull ChatOperation> operations) {}
}
