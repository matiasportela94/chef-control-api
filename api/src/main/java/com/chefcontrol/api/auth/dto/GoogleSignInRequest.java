package com.chefcontrol.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param idToken        la credencial que devuelve el botón de Google
 * @param restaurantName solo para el alta: si el usuario no existe y falta, responde GOOGLE_ACCOUNT_NOT_REGISTERED
 */
public record GoogleSignInRequest(
        @NotBlank String idToken,
        String restaurantName,
        String timezone
) {}
