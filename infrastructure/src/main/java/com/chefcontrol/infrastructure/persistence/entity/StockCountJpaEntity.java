package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.stock.StockCount;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_counts")
@Getter @Setter @NoArgsConstructor
public class StockCountJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    private String notes;

    @Column(name = "counted_at", nullable = false)
    private Instant countedAt = Instant.now();

    public static StockCountJpaEntity from(StockCount domain) {
        StockCountJpaEntity e = new StockCountJpaEntity();
        e.setId(domain.getId());
        e.setRestaurantId(domain.getRestaurantId());
        e.setUserId(domain.getUserId());
        e.setNotes(domain.getNotes());
        e.setCountedAt(domain.getCountedAt());
        return e;
    }

    public StockCount toDomain() {
        StockCount s = new StockCount();
        s.setId(id);
        s.setRestaurantId(restaurantId);
        s.setUserId(userId);
        s.setNotes(notes);
        s.setCountedAt(countedAt);
        return s;
    }
}
