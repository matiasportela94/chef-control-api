package com.chefcontrol.application.port;

/**
 * Valida el ID token que entrega el botón "Acceder con Google" y devuelve de quién es.
 * Lanza {@code AppException(GOOGLE_TOKEN_INVALID)} si la firma, el emisor, la audiencia o la vigencia no
 * cierran, si Google no verificó el email, o si el ingreso con Google no está configurado.
 */
public interface GoogleIdentityVerifier {

    GoogleIdentity verify(String idToken);

    /** @param subject claim "sub": identifica a la cuenta de Google para siempre (el email puede cambiar). */
    record GoogleIdentity(String subject, String email, String name) {}
}
