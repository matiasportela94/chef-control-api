package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.account.Account;
import com.chefcontrol.domain.repository.AccountRepository;
import com.chefcontrol.infrastructure.persistence.entity.AccountJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AccountRepositoryAdapter implements AccountRepository {

    private final JpaAccountRepository jpa;

    @Override
    public Account save(Account account) {
        return jpa.save(AccountJpaEntity.from(account)).toDomain();
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return jpa.findById(id).map(AccountJpaEntity::toDomain);
    }
}
