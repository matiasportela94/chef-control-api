package com.chefcontrol.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param idToken     confirmación con Google en el momento: tiene que ser de la cuenta vinculada
 * @param newPassword pisa la contraseña que tuviera (quien se registró con Google no la conoce)
 */
public record UnlinkGoogleAccountRequest(
        @NotBlank String idToken,
        @NotBlank @Size(min = 8, max = 100) String newPassword
) {}
