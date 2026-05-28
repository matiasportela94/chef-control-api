package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.repository.UserRepository;
import com.chefcontrol.domain.user.User;
import com.chefcontrol.infrastructure.persistence.entity.UserJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final JpaUserRepository jpa;

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id).map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> findByEmailAndIsActiveTrue(String email) {
        return jpa.findByEmailAndIsActiveTrue(email).map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> findByPhoneAndIsActiveTrue(String phone) {
        return jpa.findByPhoneAndIsActiveTrue(phone).map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmail(email).map(UserJpaEntity::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmail(email);
    }

    @Override
    public boolean existsByPhone(String phone) {
        return jpa.existsByPhone(phone);
    }

    @Override
    public User save(User user) {
        return jpa.save(UserJpaEntity.from(user)).toDomain();
    }
}
