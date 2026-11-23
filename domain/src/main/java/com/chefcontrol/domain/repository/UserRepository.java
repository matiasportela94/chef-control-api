package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.user.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByEmailAndIsActiveTrue(String email);


    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleSubject(String googleSubject);

    boolean existsByEmail(String email);


    User save(User user);
}
