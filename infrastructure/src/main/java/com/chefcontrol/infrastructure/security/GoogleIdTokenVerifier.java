package com.chefcontrol.infrastructure.security;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.GoogleIdentityVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Verifica ID tokens de Google con las claves públicas que Google publica (JWKS, cacheadas por Nimbus).
 * La firma sola no alcanza: un token emitido para OTRA aplicación también está bien firmado, por eso la
 * audiencia tiene que ser nuestro client id. Sin {@code GOOGLE_CLIENT_ID} rechaza todo (falla cerrado),
 * igual que el webhook de MercadoPago sin su secreto.
 */
@Slf4j
@Component
public class GoogleIdTokenVerifier implements GoogleIdentityVerifier {

    static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    /** Google emite con cualquiera de los dos; su doc pide aceptar ambos. */
    static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final NimbusJwtDecoder decoder;

    @Autowired
    public GoogleIdTokenVerifier(@Value("${app.google-sign-in.client-id:}") String clientId) {
        this(clientId, NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build());
    }

    /** Para tests: un decoder con una clave propia en vez de la JWKS de Google. */
    GoogleIdTokenVerifier(String clientId, NimbusJwtDecoder decoder) {
        if (clientId == null || clientId.isBlank()) {
            log.warn("[AUTH] GOOGLE_CLIENT_ID not configured: Google sign-in rejects every token");
            this.decoder = null;
            return;
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), claimsValidator(clientId)));
        this.decoder = decoder;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (decoder == null) {
            throw AppException.forbidden(ErrorCode.GOOGLE_TOKEN_INVALID, "Google sign-in is not configured");
        }
        try {
            Jwt jwt = decoder.decode(idToken);
            String email = jwt.getClaimAsString("email");
            String name = jwt.getClaimAsString("name");
            return new GoogleIdentity(jwt.getSubject(), email,
                    name != null && !name.isBlank() ? name : email.substring(0, email.indexOf('@')));
        } catch (JwtException e) {
            // También cae acá si no se pudo bajar la JWKS de Google: para el usuario es "probá de nuevo" igual.
            log.info("[AUTH] Google ID token rejected: {}", e.getMessage());
            throw AppException.forbidden(ErrorCode.GOOGLE_TOKEN_INVALID, "Invalid Google token");
        }
    }

    private static OAuth2TokenValidator<Jwt> claimsValidator(String clientId) {
        return jwt -> {
            boolean valid = ISSUERS.contains(jwt.getClaimAsString(JwtClaimNames.ISS))
                    && jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                    && jwt.getSubject() != null
                    && jwt.getClaimAsString("email") != null
                    // Sin email verificado no se puede vincular por email con una cuenta existente.
                    && Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                            "Google token issuer, audience or email verification mismatch", null));
        };
    }
}
