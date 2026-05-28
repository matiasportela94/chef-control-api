package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.user.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByEmailAndIsActiveTrue(String email);

    Optional<User> findByPhoneAndIsActiveTrue(String phone);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    User save(User user);
}
