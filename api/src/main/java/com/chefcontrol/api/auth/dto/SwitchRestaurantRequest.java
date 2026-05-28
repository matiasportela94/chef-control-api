package com.chefcontrol.api.auth.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SwitchRestaurantRequest(@NotNull UUID restaurantId) {}
