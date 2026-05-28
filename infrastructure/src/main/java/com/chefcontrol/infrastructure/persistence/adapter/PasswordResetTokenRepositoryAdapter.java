package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.repository.PasswordResetTokenRepository;
import com.chefcontrol.domain.token.PasswordResetToken;
import com.chefcontrol.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaPasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

    private final JpaPasswordResetTokenRepository jpa;

    @Override
    public Optional<PasswordResetToken> findByToken(UUID token) {
        return jpa.findByToken(token).map(PasswordResetTokenJpaEntity::toDomain);
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        return jpa.save(PasswordResetTokenJpaEntity.from(token)).toDomain();
    }

    @Override
    public void deleteByUserId(UUID userId) {
        jpa.deleteByUserId(userId);
    }
}
