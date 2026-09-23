package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.PasswordEncoderPort;
import com.chefcontrol.domain.account.Account;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.plan.Feature;
import com.chefcontrol.domain.plan.PlanTier;
import com.chefcontrol.domain.repository.AccountRepository;
import com.chefcontrol.domain.repository.AuditLogRepository;
import com.chefcontrol.domain.repository.RestaurantRepository;
import com.chefcontrol.domain.repository.RoleRepository;
import com.chefcontrol.domain.repository.UserRepository;
import com.chefcontrol.domain.repository.UserRestaurantRepository;
import com.chefcontrol.domain.restaurant.Restaurant;
import com.chefcontrol.domain.user.Permission;
import com.chefcontrol.domain.user.Role;
import com.chefcontrol.domain.user.User;
import com.chefcontrol.domain.user.UserRestaurant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.chefcontrol.domain.user.Permission.*;

@Service
@RequiredArgsConstructor
public class RestaurantRegistrationService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRestaurantRepository userRestaurantRepository;
    private final RoleRepository roleRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final AuditService auditService;

    /**
     * Permisos iniciales de MANAGER/KITCHEN/READONLY al crear una cuenta — son solo el punto
     * de partida, el dueño los puede editar libremente después. SUPERADMIN no está acá porque
     * nunca guarda permisos: siempre tiene el catálogo entero (ver Role.effectivePermissions()).
     * ROLES_* y RESTAURANTS_* quedan afuera del default de MANAGER — son acciones con
     * implicancia de facturación/administración de cuenta, el dueño las delega si quiere.
     */
    private static final Set<Permission> MANAGER_DEFAULTS = EnumSet.complementOf(EnumSet.of(
            ROLES_VIEW, ROLES_CREATE, ROLES_UPDATE, ROLES_DELETE,
            RESTAURANTS_VIEW, RESTAURANTS_CREATE, RESTAURANTS_UPDATE, RESTAURANTS_DELETE,
            ACCOUNT_VIEW));

    private static final Set<Permission> KITCHEN_DEFAULTS = EnumSet.of(
            AI_USE, WASTE_VIEW, WASTE_CREATE, STOCK_VIEW,
            STOCK_COUNTS_VIEW, STOCK_COUNTS_CREATE, MENU_VIEW,
            PRODUCTS_VIEW, PURCHASES_VIEW, SALES_VIEW);

    private static final Set<Permission> READONLY_DEFAULTS = EnumSet.of(
            PRODUCTS_VIEW, CATEGORIES_VIEW, SUPPLIERS_VIEW, PURCHASES_VIEW, SALES_VIEW,
            WASTE_VIEW, STOCK_VIEW, STOCK_COUNTS_VIEW, MENU_VIEW, FOOD_COST_VIEW, ALERTS_VIEW);

    @Transactional
    public RegisteredRestaurant register(RegisterCommand cmd) {
        if (userRepository.existsByEmail(cmd.ownerEmail())) {
            throw AppException.conflict(ErrorCode.DUPLICATE_EMAIL, "Email already in use");
        }
        if (cmd.ownerPhone() != null && userRepository.existsByPhone(cmd.ownerPhone())) {
            throw AppException.conflict(ErrorCode.DUPLICATE_PHONE, "Phone number already registered");
        }

        User owner = new User();
        owner.setName(cmd.ownerName());
        owner.setEmail(cmd.ownerEmail());
        owner.setPhone(cmd.ownerPhone());
        owner.setPasswordHash(passwordEncoder.encode(cmd.ownerPassword()));
        owner = userRepository.save(owner);

        Account account = Account.builder()
                .ownerUserId(owner.getId())
                .name(cmd.restaurantName())
                .plan(PlanTier.TRIAL)
                .build();
        account = accountRepository.save(account);

        Restaurant restaurant = new Restaurant();
        restaurant.setAccountId(account.getId());
        restaurant.setName(cmd.restaurantName());
        restaurant.setSlug(uniqueSlug(cmd.restaurantName()));
        restaurant.setTimezone(cmd.timezone() != null ? cmd.timezone() : "America/Argentina/Buenos_Aires");
        restaurant = restaurantRepository.save(restaurant);

        Role superadmin = roleRepository.save(Role.builder()
                .accountId(account.getId()).name("SUPERADMIN").isSystem(true).permissions(Set.of()).build());
        roleRepository.save(Role.builder()
                .accountId(account.getId()).name("MANAGER").isSystem(false).permissions(MANAGER_DEFAULTS).build());
        roleRepository.save(Role.builder()
                .accountId(account.getId()).name("KITCHEN").isSystem(false).permissions(KITCHEN_DEFAULTS).build());
        roleRepository.save(Role.builder()
                .accountId(account.getId()).name("READONLY").isSystem(false).permissions(READONLY_DEFAULTS).build());

        UserRestaurant membership = UserRestaurant.builder()
                .userId(owner.getId())
                .restaurantId(restaurant.getId())
                .roleId(superadmin.getId())
                .roleName(superadmin.getName())
                .isActive(true)
                .build();
        membership = userRestaurantRepository.save(membership);

        auditService.log(AuditAction.RESTAURANT_REGISTERED, "Restaurant", restaurant.getId(),
                Map.of("ownerEmail", cmd.ownerEmail(), "plan", PlanTier.TRIAL.name()));

        return new RegisteredRestaurant(owner, restaurant, List.of(membership));
    }

    /**
     * Agrega un restaurante a la cuenta del restaurante activo (TenantContext) — la cuenta,
     * los roles y el dueño ya existen, solo se suma un local más. El dueño de la cuenta queda
     * con acceso automático ahí mismo (misma lógica que en el registro: SUPERADMIN es "el
     * dueño de la cuenta", no de un local puntual).
     */
    @Transactional
    public Restaurant createAdditionalRestaurant(CreateRestaurantCommand cmd) {
        UUID accountId = currentAccountId();

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account not found"));

        List<Restaurant> existingRestaurants = restaurantRepository.findAllByAccountId(accountId);
        if (!account.hasFeature(Feature.MULTI_RESTAURANT) && !existingRestaurants.isEmpty()) {
            throw AppException.forbidden(ErrorCode.PLAN_LIMIT_REACHED,
                    "Your plan (" + account.getPlan() + ") allows only one restaurant per account");
        }

        Restaurant restaurant = new Restaurant();
        restaurant.setAccountId(accountId);
        restaurant.setName(cmd.name());
        restaurant.setSlug(uniqueSlug(cmd.name()));
        restaurant.setTimezone(cmd.timezone() != null ? cmd.timezone() : "America/Argentina/Buenos_Aires");
        restaurant = restaurantRepository.save(restaurant);

        Role superadmin = roleRepository.findByAccountIdAndIsSystemTrue(accountId)
                .orElseThrow(() -> new IllegalStateException("System role not found for account: " + accountId));

        UserRestaurant membership = UserRestaurant.builder()
                .userId(account.getOwnerUserId())
                .restaurantId(restaurant.getId())
                .roleId(superadmin.getId())
                .roleName(superadmin.getName())
                .isActive(true)
                .build();
        userRestaurantRepository.save(membership);

        auditService.log(AuditAction.RESTAURANT_CREATED, "Restaurant", restaurant.getId(),
                Map.of("accountId", accountId, "name", cmd.name()));

        return restaurant;
    }

    @Transactional
    public Restaurant updateRestaurant(UUID id, CreateRestaurantCommand cmd) {
        Restaurant restaurant = requireFromCurrentAccount(id);
        restaurant.setName(cmd.name());
        if (cmd.timezone() != null) restaurant.setTimezone(cmd.timezone());
        restaurant = restaurantRepository.save(restaurant);

        auditService.log(AuditAction.RESTAURANT_UPDATED, "Restaurant", id,
                Map.of("name", restaurant.getName(), "timezone", restaurant.getTimezone()));
        return restaurant;
    }

    /**
     * Deshabilitar un restaurante lo saca de todos lados sin borrar nada: los queries ya
     * filtran por is_active (incluido el listado de locales al loguear/cambiar de local).
     */
    @Transactional
    public Restaurant setRestaurantActive(UUID id, boolean active) {
        Restaurant restaurant = requireFromCurrentAccount(id);
        if (!active) requireNotCurrent(id);

        restaurant.setActive(active);
        restaurant = restaurantRepository.save(restaurant);

        auditService.log(active ? AuditAction.RESTAURANT_ACTIVATED : AuditAction.RESTAURANT_DEACTIVATED,
                "Restaurant", id, Map.of("name", restaurant.getName()));
        return restaurant;
    }

    /**
     * Borrado duro: se lleva puesta absolutamente toda la data del restaurante (stock, ventas,
     * compras, recetas, usuarios asignados...) vía ON DELETE CASCADE — ver V15 — incluido su
     * audit_log, que se borra a mano porque no tiene FK a restaurants.
     *
     * Lo único que queda en la cuenta es la entrada RESTAURANT_DELETED de acá abajo: se audita
     * con el tenant del local en el que estás parado, no con el que se borra, así que sobrevive
     * al DELETE. Es una línea diciendo "se eliminó este local" y nada más.
     */
    @Transactional
    public void deleteRestaurant(UUID id) {
        Restaurant restaurant = requireFromCurrentAccount(id);
        requireNotCurrent(id);

        auditService.log(AuditAction.RESTAURANT_DELETED, "Restaurant", id,
                Map.of("name", restaurant.getName(), "slug", restaurant.getSlug()));

        auditLogRepository.deleteByRestaurantId(id);
        restaurantRepository.deleteById(id);
    }

    private Restaurant requireFromCurrentAccount(UUID id) {
        UUID accountId = currentAccountId();
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> AppException.notFound(ErrorCode.RESTAURANT_NOT_FOUND, "Restaurant not found"));
        if (!accountId.equals(restaurant.getAccountId())) {
            throw AppException.notFound(ErrorCode.RESTAURANT_NOT_FOUND, "Restaurant not found");
        }
        return restaurant;
    }

    /**
     * No se puede deshabilitar ni borrar el local en el que estás parado: te quedarías con el
     * tenant del JWT apuntando a algo muerto. Además garantiza que siempre queda al menos un
     * local activo en la cuenta (el actual), o sea nadie se puede autobloquear el acceso.
     */
    private void requireNotCurrent(UUID id) {
        if (id.equals(TenantContext.require())) {
            throw AppException.conflict(ErrorCode.CANNOT_MODIFY_CURRENT_RESTAURANT,
                    "Switch to another restaurant before disabling or deleting this one");
        }
    }

    private UUID currentAccountId() {
        return restaurantRepository.findByIdAndIsActiveTrue(TenantContext.require())
                .orElseThrow(() -> new IllegalStateException("Restaurant not found"))
                .getAccountId();
    }

    public List<Restaurant> listAccountRestaurants() {
        return restaurantRepository.findAllByAccountId(currentAccountId());
    }

    private String uniqueSlug(String name) {
        String base = Restaurant.generateBaseSlug(name);

        if (!restaurantRepository.existsBySlug(base)) return base;

        int i = 2;
        while (restaurantRepository.existsBySlug(base + "-" + i)) i++;
        return base + "-" + i;
    }

    // ── Commands / Results ────────────────────────────────────────────────────

    public record RegisterCommand(
            String restaurantName,
            String ownerName,
            String ownerEmail,
            String ownerPassword,
            String ownerPhone,
            String timezone
    ) {}

    public record CreateRestaurantCommand(String name, String timezone) {}

    public record RegisteredRestaurant(User owner, Restaurant restaurant, List<UserRestaurant> memberships) {}
}
