package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.token.PasswordResetToken;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository {

    Optional<PasswordResetToken> findByToken(UUID token);

    PasswordResetToken save(PasswordResetToken token);

    void deleteByUserId(UUID userId);
}
