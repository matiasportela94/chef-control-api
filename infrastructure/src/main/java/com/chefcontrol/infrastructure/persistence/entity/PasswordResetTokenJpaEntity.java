package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.token.PasswordResetToken;
import com.chefcontrol.domain.token.TokenType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
@Getter @Setter @NoArgsConstructor
public class PasswordResetTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private UserJpaEntity user;

    @Column(nullable = false, unique = true)
    private UUID token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenType type;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static PasswordResetTokenJpaEntity from(PasswordResetToken domain) {
        PasswordResetTokenJpaEntity e = new PasswordResetTokenJpaEntity();
        e.setId(domain.getId());
        e.setUserId(domain.getUserId());
        e.setToken(domain.getToken());
        e.setType(domain.getType());
        e.setExpiresAt(domain.getExpiresAt());
        e.setUsedAt(domain.getUsedAt());
        e.setCreatedAt(domain.getCreatedAt());
        return e;
    }

    public PasswordResetToken toDomain() {
        PasswordResetToken t = new PasswordResetToken();
        t.setId(id);
        t.setUserId(userId);
        t.setToken(token);
        t.setType(type);
        t.setExpiresAt(expiresAt);
        t.setUsedAt(usedAt);
        t.setCreatedAt(createdAt);
        return t;
    }
}
