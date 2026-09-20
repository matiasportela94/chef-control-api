package com.chefcontrol.application.service;

import com.chefcontrol.domain.account.Account;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.repository.AccountRepository;
import com.chefcontrol.domain.repository.RestaurantRepository;
import com.chefcontrol.domain.repository.UserRepository;
import com.chefcontrol.domain.repository.UserRestaurantRepository;
import com.chefcontrol.domain.restaurant.Restaurant;
import com.chefcontrol.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Datos de la cuenta (plan, uso, dueño) para la pantalla de administración.
 *
 * ponytail: no hay historial de pagos porque todavía no existe facturación real
 * (ni Stripe/MercadoPago ni tabla de invoices). Cuando se integre el cobro, los pagos
 * salen del proveedor, no de una tabla mantenida a mano que nadie va a mantener.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final UserRestaurantRepository userRestaurantRepository;

    @Transactional(readOnly = true)
    public AccountOverview getCurrentAccount() {
        UUID accountId = restaurantRepository.findByIdAndIsActiveTrue(TenantContext.require())
                .orElseThrow(() -> new IllegalStateException("Restaurant not found"))
                .getAccountId();

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account not found"));

        List<Restaurant> restaurants = restaurantRepository.findAllByAccountId(accountId);
        User owner = userRepository.findById(account.getOwnerUserId()).orElse(null);

        Set<UUID> users = restaurants.stream()
                .flatMap(r -> userRestaurantRepository.findActiveByRestaurantId(r.getId()).stream())
                .map(m -> m.getUserId())
                .collect(Collectors.toSet());

        return new AccountOverview(account, owner, restaurants, users.size());
    }

    public record AccountOverview(Account account, User owner, List<Restaurant> restaurants, int userCount) {}
}
