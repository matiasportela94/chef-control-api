package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.account.Account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(UUID id);
}
