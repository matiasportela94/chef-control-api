package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.account.Account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(UUID id);

    /**
     * Borra la cuenta y absolutamente todo lo que cuelga de ella: restaurantes (con toda su
     * data, vía cascada), roles, audit_log y los usuarios que quedan sin ninguna membresía.
     * Un usuario que además trabaja en otra cuenta sobrevive.
     */
    void deleteWithAllData(UUID accountId);
}
