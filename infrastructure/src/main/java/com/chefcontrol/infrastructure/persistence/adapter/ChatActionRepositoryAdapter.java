package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.chat.ChatAction;
import com.chefcontrol.domain.chat.ChatActionRepository;
import com.chefcontrol.infrastructure.persistence.entity.ChatActionJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaChatActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ChatActionRepositoryAdapter implements ChatActionRepository {

    private final JpaChatActionRepository jpa;

    @Override
    public ChatAction save(ChatAction action) {
        return jpa.saveAndFlush(ChatActionJpaEntity.from(action)).toDomain();
    }

    @Override
    public Optional<ChatAction> findByIdAndRestaurantId(UUID id, UUID restaurantId) {
        return jpa.findByIdAndRestaurantId(id, restaurantId).map(ChatActionJpaEntity::toDomain);
    }
}
