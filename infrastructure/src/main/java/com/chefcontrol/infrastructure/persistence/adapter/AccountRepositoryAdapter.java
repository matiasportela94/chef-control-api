package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.account.Account;
import com.chefcontrol.domain.repository.AccountRepository;
import com.chefcontrol.infrastructure.persistence.entity.AccountJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AccountRepositoryAdapter implements AccountRepository {

    private final JpaAccountRepository jpa;

    @PersistenceContext
    private EntityManager em;

    @Override
    public Account save(Account account) {
        return jpa.save(AccountJpaEntity.from(account)).toDomain();
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return jpa.findById(id).map(AccountJpaEntity::toDomain);
    }

    /**
     * SQL nativo a propósito: el borrado se apoya en las cascadas de la base (V15/V16), que es
     * donde vive el grafo real de dependencias. Recrearlo en JPA sería duplicarlo y dejarlo rotar.
     * El orden importa: los usuarios se juntan ANTES de borrar los restaurantes, porque las
     * membresías que los identifican desaparecen con la cascada.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void deleteWithAllData(UUID accountId) {
        List<UUID> accountUserIds = em.createNativeQuery("""
                SELECT DISTINCT ur.user_id FROM user_restaurants ur
                JOIN restaurants r ON r.id = ur.restaurant_id
                WHERE r.account_id = :accountId""")
                .setParameter("accountId", accountId).getResultList();

        em.createNativeQuery("""
                DELETE FROM audit_log
                WHERE restaurant_id IN (SELECT id FROM restaurants WHERE account_id = :accountId)""")
                .setParameter("accountId", accountId).executeUpdate();

        em.createNativeQuery("DELETE FROM restaurants WHERE account_id = :accountId")
                .setParameter("accountId", accountId).executeUpdate();

        em.createNativeQuery("DELETE FROM roles WHERE account_id = :accountId")
                .setParameter("accountId", accountId).executeUpdate();

        em.createNativeQuery("DELETE FROM accounts WHERE id = :accountId")
                .setParameter("accountId", accountId).executeUpdate();

        // Solo los de esta cuenta, y solo si no les quedó ninguna membresía: el que además
        // trabaja en otra cuenta conserva filas en user_restaurants y este DELETE lo saltea.
        if (!accountUserIds.isEmpty()) {
            em.createNativeQuery("""
                    DELETE FROM users u
                    WHERE u.id IN (:userIds)
                      AND NOT EXISTS (SELECT 1 FROM user_restaurants ur WHERE ur.user_id = u.id)""")
                    .setParameter("userIds", accountUserIds).executeUpdate();
        }
    }
}
