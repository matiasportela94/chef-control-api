package com.chefcontrol.domain.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Una entidad que registró el chat de la IA, para poder deshacerla. Solo lo del chat: lo cargado por formulario se
 * revierte como siempre, con el permiso de borrar.
 */
@Getter @Builder @AllArgsConstructor
public class ChatAction {

    /** Lo que tiene quien lo cargó para deshacerlo sin el permiso de borrar (Mati, 2026-11-17). */
    public static final Duration OWNER_UNDO_WINDOW = Duration.ofMinutes(15);

    private UUID id;
    private UUID restaurantId;
    /** null si el usuario ya no existe: desde ahí solo lo deshace quien tenga el permiso de borrar. */
    private UUID userId;
    private ChatOperationType type;
    private UUID entityId;
    private String summary;
    /** Lo que hay que avisar al deshacerla y que el deshacer no arregla solo; null casi siempre. */
    private String undoNote;
    private Instant createdAt;
    private Instant undoneAt;

    public Instant undoableUntil() {
        return createdAt.plus(OWNER_UNDO_WINDOW);
    }

    /** Un cocinero no suele tener el permiso de borrar: sin esto no podría corregir su propio error. */
    public boolean undoableWithoutPermissionBy(UUID user, Instant now) {
        return user != null && user.equals(userId) && now.isBefore(undoableUntil());
    }

    public boolean isUndone() {
        return undoneAt != null;
    }

    public void markUndone(Instant now) {
        undoneAt = now;
    }
}
