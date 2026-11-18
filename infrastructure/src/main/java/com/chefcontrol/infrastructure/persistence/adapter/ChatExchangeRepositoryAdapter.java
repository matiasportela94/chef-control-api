package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.application.port.ChatExchangeRepository;
import com.chefcontrol.application.service.ChatService.ChatStatus;
import com.chefcontrol.infrastructure.persistence.entity.ChatMessageJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaChatMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ChatExchangeRepositoryAdapter implements ChatExchangeRepository {

    private final JpaChatMessageRepository jpa;
    private final ObjectMapper objectMapper;

    @Override
    public ChatExchange saveChatExchange(ChatExchange x) {
        ChatMessageJpaEntity e = new ChatMessageJpaEntity();
        e.setId(x.id());
        e.setRestaurantId(x.restaurantId());
        e.setUserId(x.userId());
        e.setKind(x.kind().name());
        e.setUserMessage(x.userMessage());
        e.setStatus(x.status() == null ? null : x.status().name());
        e.setReply(x.reply() == null ? null : write(x.reply()));
        e.setCreatedAt(x.createdAt());
        return toDomain(jpa.saveAndFlush(e));
    }

    @Override
    public List<ChatExchange> findChatExchangesSince(UUID userId, UUID restaurantId, Instant since) {
        return jpa.findByUserIdAndRestaurantIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(userId, restaurantId, since)
                .stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public int deleteChatExchangesBefore(Instant cutoff) {
        return jpa.deleteCreatedBefore(cutoff);
    }

    private ChatExchange toDomain(ChatMessageJpaEntity e) {
        return new ChatExchange(e.getId(), e.getRestaurantId(), e.getUserId(), Kind.valueOf(e.getKind()),
                e.getUserMessage(), e.getStatus() == null ? null : ChatStatus.valueOf(e.getStatus()),
                e.getReply() == null ? null : read(e.getReply()), e.getCreatedAt());
    }

    String write(StoredReply reply) {
        try {
            return objectMapper.writeValueAsString(reply);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not write the chat reply", ex);
        }
    }

    StoredReply read(String json) {
        try {
            return objectMapper.readValue(json, StoredReply.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read the chat reply", ex);
        }
    }
}
