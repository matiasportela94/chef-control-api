package com.chefcontrol.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** @param idToken la credencial que devuelve el botón de Google */
public record LinkGoogleAccountRequest(@NotBlank String idToken) {}
