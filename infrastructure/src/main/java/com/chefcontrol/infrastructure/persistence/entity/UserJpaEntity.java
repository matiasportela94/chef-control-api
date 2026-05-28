package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class UserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String phone;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<UserRestaurantJpaEntity> memberships = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static UserJpaEntity from(User domain) {
        UserJpaEntity e = new UserJpaEntity();
        e.setId(domain.getId());
        e.setEmail(domain.getEmail());
        e.setPasswordHash(domain.getPasswordHash());
        e.setName(domain.getName());
        e.setPhone(domain.getPhone());
        e.setActive(domain.isActive());
        e.setCreatedAt(domain.getCreatedAt());
        return e;
    }

    public User toDomain() {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setPasswordHash(passwordHash);
        u.setName(name);
        u.setPhone(phone);
        u.setActive(isActive);
        u.setCreatedAt(createdAt);
        return u;
    }
}
