package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.ChatMessageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JpaChatMessageRepository extends JpaRepository<ChatMessageJpaEntity, UUID> {

    List<ChatMessageJpaEntity> findByUserIdAndRestaurantIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
            UUID userId, UUID restaurantId, Instant since);

    @Modifying
    @Query("DELETE FROM ChatMessageJpaEntity m WHERE m.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
