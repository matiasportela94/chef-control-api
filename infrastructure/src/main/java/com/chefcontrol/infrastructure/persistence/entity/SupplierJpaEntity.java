package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.supplier.ContactInfo;
import com.chefcontrol.domain.supplier.Supplier;
import com.chefcontrol.infrastructure.persistence.entity.converter.ContactInfoConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
@Getter @Setter @NoArgsConstructor
public class SupplierJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(nullable = false)
    private String name;

    @Convert(converter = ContactInfoConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact_info", columnDefinition = "jsonb")
    private ContactInfo contactInfo;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static SupplierJpaEntity from(Supplier domain) {
        SupplierJpaEntity e = new SupplierJpaEntity();
        e.setId(domain.getId());
        e.setRestaurantId(domain.getRestaurantId());
        e.setName(domain.getName());
        e.setContactInfo(domain.getContactInfo());
        e.setActive(domain.isActive());
        e.setCreatedAt(domain.getCreatedAt());
        return e;
    }

    public Supplier toDomain() {
        Supplier s = new Supplier();
        s.setId(id);
        s.setRestaurantId(restaurantId);
        s.setName(name);
        s.setContactInfo(contactInfo);
        s.setActive(isActive);
        s.setCreatedAt(createdAt);
        return s;
    }
}
