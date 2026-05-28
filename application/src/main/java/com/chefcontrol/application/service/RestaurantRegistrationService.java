package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.PasswordEncoderPort;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.plan.PlanTier;
import com.chefcontrol.domain.repository.RestaurantRepository;
import com.chefcontrol.domain.repository.RoleRepository;
import com.chefcontrol.domain.repository.UserRepository;
import com.chefcontrol.domain.repository.UserRestaurantRepository;
import com.chefcontrol.domain.restaurant.Restaurant;
import com.chefcontrol.domain.user.RoleName;
import com.chefcontrol.domain.user.User;
import com.chefcontrol.domain.user.UserRestaurant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RestaurantRegistrationService {

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRestaurantRepository userRestaurantRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final AuditService auditService;

    @Transactional
    public RegisteredRestaurant register(RegisterCommand cmd) {
        if (userRepository.existsByEmail(cmd.ownerEmail())) {
            throw AppException.conflict(ErrorCode.DUPLICATE_EMAIL, "Email already in use");
        }
        if (cmd.ownerPhone() != null && userRepository.existsByPhone(cmd.ownerPhone())) {
            throw AppException.conflict(ErrorCode.DUPLICATE_PHONE, "Phone number already registered");
        }

        Restaurant restaurant = new Restaurant();
        restaurant.setName(cmd.restaurantName());
        restaurant.setSlug(uniqueSlug(cmd.restaurantName()));
        restaurant.setTimezone(cmd.timezone() != null ? cmd.timezone() : "America/Argentina/Buenos_Aires");
        restaurant.setPlan(PlanTier.TRIAL);
        restaurant = restaurantRepository.save(restaurant);

        User owner = new User();
        owner.setName(cmd.ownerName());
        owner.setEmail(cmd.ownerEmail());
        owner.setPhone(cmd.ownerPhone());
        owner.setPasswordHash(passwordEncoder.encode(cmd.ownerPassword()));
        owner = userRepository.save(owner);

        var ownerRole = roleRepository.findByName(RoleName.OWNER)
                .orElseThrow(() -> new IllegalStateException("OWNER role not found"));

        UserRestaurant membership = UserRestaurant.builder()
                .userId(owner.getId())
                .restaurantId(restaurant.getId())
                .roleId(ownerRole.getId())
                .roleName(ownerRole.getName())
                .isActive(true)
                .build();
        membership = userRestaurantRepository.save(membership);

        auditService.log(AuditAction.RESTAURANT_REGISTERED, "Restaurant", restaurant.getId(),
                Map.of("ownerEmail", cmd.ownerEmail(), "plan", PlanTier.TRIAL.name()));

        return new RegisteredRestaurant(owner, restaurant, List.of(membership));
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

    public record RegisteredRestaurant(User owner, Restaurant restaurant, List<UserRestaurant> memberships) {}
}
