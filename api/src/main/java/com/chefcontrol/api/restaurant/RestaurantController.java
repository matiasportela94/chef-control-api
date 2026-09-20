package com.chefcontrol.api.restaurant;

import com.chefcontrol.api.restaurant.dto.CreateRestaurantRequest;
import com.chefcontrol.api.restaurant.dto.RestaurantResponse;
import com.chefcontrol.application.service.RestaurantRegistrationService;
import com.chefcontrol.application.service.RestaurantRegistrationService.CreateRestaurantCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Restaurantes de la cuenta actual (la de {@code TenantContext}). Crear uno nuevo respeta
 * el límite del plan (Feature.MULTI_RESTAURANT) — ver RestaurantRegistrationService.
 */
@RestController
@RequestMapping("/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantRegistrationService restaurantRegistrationService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_RESTAURANTS_VIEW')")
    public ResponseEntity<List<RestaurantResponse>> list() {
        return ResponseEntity.ok(
                restaurantRegistrationService.listAccountRestaurants().stream()
                        .map(RestaurantResponse::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_RESTAURANTS_CREATE')")
    public ResponseEntity<RestaurantResponse> create(@Valid @RequestBody CreateRestaurantRequest request) {
        var restaurant = restaurantRegistrationService.createAdditionalRestaurant(
                new CreateRestaurantCommand(request.name(), request.timezone()));
        return ResponseEntity.status(HttpStatus.CREATED).body(RestaurantResponse.from(restaurant));
    }
}
