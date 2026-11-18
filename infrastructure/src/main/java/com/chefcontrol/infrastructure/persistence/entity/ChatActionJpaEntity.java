package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.chat.ChatAction;
import com.chefcontrol.domain.chat.ChatOperationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_actions")
@Getter @Setter @NoArgsConstructor
public class ChatActionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatOperationType type;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false)
    private String summary;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "undone_at")
    private Instant undoneAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static ChatActionJpaEntity from(ChatAction d) {
        ChatActionJpaEntity e = new ChatActionJpaEntity();
        e.setId(d.getId());
        e.setRestaurantId(d.getRestaurantId());
        e.setUserId(d.getUserId());
        e.setType(d.getType());
        e.setEntityId(d.getEntityId());
        e.setSummary(d.getSummary());
        e.setCreatedAt(d.getCreatedAt());
        e.setUndoneAt(d.getUndoneAt());
        return e;
    }

    public ChatAction toDomain() {
        return ChatAction.builder().id(id).restaurantId(restaurantId).userId(userId).type(type).entityId(entityId)
                .summary(summary).createdAt(createdAt).undoneAt(undoneAt).build();
    }
}
