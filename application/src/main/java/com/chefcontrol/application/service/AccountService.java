package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.domain.account.Account;
import com.chefcontrol.domain.audit.AuditAction;
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
import java.util.Map;
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
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;

    /**
     * Cierra la cuenta entera: todos los restaurantes con toda su data, los roles, el audit_log
     * y los usuarios que no trabajen también en otra cuenta. No queda nada ni rastro — es la
     * salida del dueño que quiere irse de la plataforma, y la contracara del guard que impide
     * borrar el último local desde la pantalla de restaurantes.
     *
     * Solo el dueño de la cuenta, y no se delega por permiso: es irreversible y afecta a todos
     * los usuarios de la cuenta, no solo a quien aprieta el botón.
     */
    @Transactional
    public void deleteCurrentAccount() {
        Account account = currentAccount();

        if (!account.getOwnerUserId().equals(currentUserProvider.currentUserId())) {
            throw AppException.forbidden(ErrorCode.NOT_ACCOUNT_OWNER,
                    "Only the account owner can delete the account");
        }

        accountRepository.deleteWithAllData(account.getId());
    }

    /**
     * Renombra la cuenta. Solo el dueño, y sin permiso delegable — mismo criterio que el cierre:
     * el nombre de la cuenta es el de la unidad de facturación, no el de un local.
     *
     * <p>Existe porque V13 la nombró con el nombre de un restaurante y V26 la renombró a
     * "Grupo <dueño>" al fusionar: ninguno de los dos es necesariamente como se llama la empresa,
     * y hasta ahora no había forma de arreglarlo que no fuera un UPDATE a mano.
     */
    @Transactional
    public Account renameCurrentAccount(String name) {
        Account account = currentAccount();

        if (!account.getOwnerUserId().equals(currentUserProvider.currentUserId())) {
            throw AppException.forbidden(ErrorCode.NOT_ACCOUNT_OWNER,
                    "Only the account owner can rename the account");
        }

        account.setName(name.trim());
        Account saved = accountRepository.save(account);
        auditService.log(AuditAction.ACCOUNT_RENAMED, "Account", saved.getId(),
                Map.of("name", saved.getName()));
        return saved;
    }

    @Transactional(readOnly = true)
    public AccountOverview getCurrentAccount() {
        Account account = currentAccount();
        UUID accountId = account.getId();
        List<Restaurant> restaurants = restaurantRepository.findAllByAccountId(accountId);
        User owner = userRepository.findById(account.getOwnerUserId()).orElse(null);

        Set<UUID> users = restaurants.stream()
                .flatMap(r -> userRestaurantRepository.findActiveByRestaurantId(r.getId()).stream())
                .map(m -> m.getUserId())
                .collect(Collectors.toSet());

        return new AccountOverview(account, owner, restaurants, users.size());
    }

    private Account currentAccount() {
        UUID accountId = restaurantRepository.findByIdAndIsActiveTrue(TenantContext.require())
                .orElseThrow(() -> new IllegalStateException("Restaurant not found"))
                .getAccountId();
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account not found"));
    }

    public record AccountOverview(Account account, User owner, List<Restaurant> restaurants, int userCount) {}
}
