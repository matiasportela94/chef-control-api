package com.chefcontrol.domain.chat;

import java.util.Optional;
import java.util.UUID;

public interface ChatActionRepository {

    ChatAction save(ChatAction action);

    Optional<ChatAction> findByIdAndRestaurantId(UUID id, UUID restaurantId);
}
