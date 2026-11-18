package com.chefcontrol.domain.chat;

import java.util.Optional;
import java.util.UUID;

public interface ChatActionRepository {

    ChatAction save(ChatAction action);

    Optional<ChatAction> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    /** Las de esos ids en ese local, para mostrar el historial con su estado de ahora. */
    java.util.List<ChatAction> findAllByIdInAndRestaurantId(java.util.Collection<UUID> ids, UUID restaurantId);
}
