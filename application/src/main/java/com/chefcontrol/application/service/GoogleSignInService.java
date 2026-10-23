package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.GoogleIdentityVerifier;
import com.chefcontrol.application.port.GoogleIdentityVerifier.GoogleIdentity;
import com.chefcontrol.application.port.PasswordEncoderPort;
import com.chefcontrol.application.service.RestaurantRegistrationService.RegisterCommand;
import com.chefcontrol.application.service.RestaurantRegistrationService.RegisteredRestaurant;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.repository.UserRepository;
import com.chefcontrol.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * "Acceder con Google". El usuario se identifica por el {@code sub} de Google. La primera vez que entra con
 * Google se lo vincula por email con la cuenta que ya tuviera: Google verificó ese email (el verificador
 * rechaza los que no), así que es la misma persona. Si no existe, el alta necesita el nombre del
 * restaurante — es lo único que Google no sabe —; sin él, {@code GOOGLE_ACCOUNT_NOT_REGISTERED} le dice al
 * front que lo pida y vuelva con el mismo token.
 */
@Service
@RequiredArgsConstructor
public class GoogleSignInService {

    private final GoogleIdentityVerifier verifier;
    private final UserRepository userRepository;
    private final RestaurantRegistrationService registrationService;
    private final AuditService auditService;
    private final PasswordEncoderPort passwordEncoder;

    @Transactional
    public GoogleSignIn signIn(String idToken, String restaurantName, String timezone) {
        GoogleIdentity google = verifier.verify(idToken);

        Optional<User> found = userRepository.findByGoogleSubject(google.subject())
                .or(() -> userRepository.findByEmail(google.email()).map(user -> link(user, google)));
        if (found.isPresent()) {
            if (!found.get().isActive()) {
                throw AppException.forbidden(ErrorCode.USER_INACTIVE, "User is inactive");
            }
            return new GoogleSignIn(found.get(), null);
        }

        if (restaurantName == null || restaurantName.isBlank()) {
            throw AppException.notFound(ErrorCode.GOOGLE_ACCOUNT_NOT_REGISTERED, "No account for this Google user yet");
        }
        RegisteredRestaurant registered = registrationService.register(new RegisterCommand(
                restaurantName.trim(), google.name(), google.email(), null, null, timezone, google.subject()));
        return new GoogleSignIn(registered.owner(), registered);
    }

    private User link(User user, GoogleIdentity google) {
        // Ya vinculado a OTRA cuenta de Google con el mismo email (Google reasignó la dirección): no se pisa.
        if (user.getGoogleSubject() != null) {
            throw AppException.conflict(ErrorCode.DUPLICATE_EMAIL, "Email already linked to another Google account");
        }
        user.setGoogleSubject(google.subject());
        User saved = userRepository.save(user);
        auditService.log(AuditAction.USER_GOOGLE_LINKED, "User", saved.getId(), Map.of("email", google.email()));
        return saved;
    }

    /**
     * Vincular Google desde el perfil, con la sesión abierta. A diferencia del ingreso, el email de Google puede ser
     * otro que el del usuario (decidido por Mati, 2026-10-23): quien vincula ya probó quién es con su sesión.
     */
    @Transactional
    public void linkGoogleAccount(UUID userId, String idToken) {
        GoogleIdentity google = verifier.verify(idToken);
        User user = requireUser(userId);
        if (google.subject().equals(user.getGoogleSubject())) {
            return;
        }
        if (user.getGoogleSubject() != null) {
            throw AppException.conflict(ErrorCode.USER_ALREADY_LINKED_TO_GOOGLE, "User already linked to another Google account");
        }
        if (userRepository.findByGoogleSubject(google.subject()).isPresent()) {
            throw AppException.conflict(ErrorCode.GOOGLE_ACCOUNT_ALREADY_LINKED, "Google account linked to another user");
        }
        user.setGoogleSubject(google.subject());
        userRepository.save(user);
        auditService.log(AuditAction.USER_GOOGLE_LINKED, "User", userId, Map.of("email", google.email()));
    }

    /**
     * Desvincular obliga a poner una contraseña nueva, que pisa la que hubiera: quien se registró con Google no
     * conoce la suya (es al azar). Como no se pide la actual, el token de Google es la confirmación: una sesión
     * robada sola no alcanza para quedarse con la cuenta.
     */
    @Transactional
    public void unlinkGoogleAccount(UUID userId, String idToken, String newPassword) {
        GoogleIdentity google = verifier.verify(idToken);
        User user = requireUser(userId);
        if (!google.subject().equals(user.getGoogleSubject())) {
            throw AppException.conflict(ErrorCode.GOOGLE_ACCOUNT_MISMATCH, "Token is not from the linked Google account");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setGoogleSubject(null);
        userRepository.save(user);
        auditService.log(AuditAction.USER_GOOGLE_UNLINKED, "User", userId, Map.of("email", google.email()));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.USER_NOT_FOUND, "User not found"));
    }

    /** @param registered no null cuando este ingreso dio de alta la cuenta. */
    public record GoogleSignIn(User user, RegisteredRestaurant registered) {}
}
