package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.EmailService;
import com.chefcontrol.application.port.PasswordEncoderPort;
import com.chefcontrol.application.port.WhatsAppService;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.repository.PasswordResetTokenRepository;
import com.chefcontrol.domain.repository.UserRepository;
import com.chefcontrol.domain.restaurant.Restaurant;
import com.chefcontrol.domain.token.PasswordResetToken;
import com.chefcontrol.domain.token.TokenType;
import com.chefcontrol.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    @Value("${app.token.password-reset-expiration-minutes:15}")
    private int passwordResetExpirationMinutes;

    @Value("${app.token.set-password-expiration-hours:48}")
    private int setPasswordExpirationHours;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final AuditService auditService;

    /**
     * Initiates a password reset. Never reveals whether the email exists.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        // Pasado el límite, nada: ni token ni mail. La respuesta es la misma, así no dice si el email existe.
        if (!resetLimiter.allow(User.normalizeEmail(email), Instant.now())) {
            log.info("[AUTH] password reset throttled");
            return;
        }
        userRepository.findByEmailAndIsActiveTrue(email).ifPresent(user -> {
            UUID token = buildAndSaveToken(user, TokenType.PASSWORD_RESET,
                    Duration.ofMinutes(passwordResetExpirationMinutes));

            String link = frontendUrl + "/reset-password?token=" + token;

            emailService.sendPasswordResetLink(user.getEmail(), user.getName(), link);

            if (user.getPhone() != null) {
                whatsAppService.sendPasswordResetLink(user.getPhone(), user.getName(), link);
            }

            auditService.log(AuditAction.PASSWORD_RESET_REQUESTED, "User", user.getId(),
                    Map.of("email", email));
        });
    }

    /**
     * Validates the token and updates the user's password. Works for both
     * PASSWORD_RESET (forgot-password) and SET_PASSWORD (new-user invite) flows.
     */
    @Transactional
    public void resetPassword(UUID token, String newPassword) {
        PasswordResetToken prt = tokenRepository.findByTokenHash(PasswordResetToken.hash(token))
                .orElseThrow(() -> AppException.badRequest(ErrorCode.INVALID_TOKEN, "Invalid or unknown token"));

        if (prt.isExpired()) {
            throw AppException.badRequest(ErrorCode.TOKEN_EXPIRED, "This link has expired");
        }
        if (prt.isUsed()) {
            throw AppException.badRequest(ErrorCode.TOKEN_ALREADY_USED, "This link has already been used");
        }

        User user = userRepository.findById(prt.getUserId())
                .orElseThrow(() -> AppException.notFound(ErrorCode.USER_NOT_FOUND, "User not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        prt.markUsed();
        tokenRepository.save(prt);

        auditService.log(AuditAction.PASSWORD_RESET_COMPLETED, "User", user.getId(),
                Map.of("type", prt.getType().name()));
    }

    /**
     * Creates a SET_PASSWORD token for a newly invited user and sends the link
     * via all available channels.
     * Called within the user creation transaction.
     */
    @Transactional
    public void createAndSendSetPasswordToken(User user, Restaurant restaurant) {
        UUID token = buildAndSaveToken(user, TokenType.SET_PASSWORD, Duration.ofHours(setPasswordExpirationHours));

        String link = frontendUrl + "/set-password?token=" + token;

        emailService.sendSetPasswordLink(user.getEmail(), user.getName(), restaurant.getName(), link);

        if (user.getPhone() != null) {
            whatsAppService.sendSetPasswordLink(user.getPhone(), user.getName(), restaurant.getName(), link);
        }
    }

    /** Guarda el hash y devuelve el token, que va solo en el link. */
    private UUID buildAndSaveToken(User user, TokenType type, Duration duration) {
        tokenRepository.deleteByUserId(user.getId());

        UUID token = UUID.randomUUID();
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUserId(user.getId());
        prt.setTokenHash(PasswordResetToken.hash(token));
        prt.setType(type);
        prt.setExpiresAt(Instant.now().plus(duration));
        tokenRepository.save(prt);
        return token;
    }

    private final ResetLimiter resetLimiter = new ResetLimiter();

    /**
     * Pedidos de "olvidé mi contraseña" por email: uno por minuto y cinco por hora. Sin esto, cualquiera podía llenarle la
     * casilla a otro.
     * ponytail: en memoria y por instancia (con dos instancias del back cada una cuenta aparte) y la limpieza recorre
     * todo en cada pedido; con más de una instancia o mucho tráfico, a la base o a Redis.
     */
    static final class ResetLimiter {
        private static final Duration MIN_GAP = Duration.ofMinutes(1);
        private static final Duration WINDOW = Duration.ofHours(1);
        private static final int MAX_PER_WINDOW = 5;

        private final Map<String, List<Instant>> attempts = new HashMap<>();

        synchronized boolean allow(String key, Instant now) {
            Instant windowStart = now.minus(WINDOW);
            // Solo quedan los pedidos de la última hora: el mapa no crece más que eso.
            attempts.values().removeIf(times -> {
                times.removeIf(t -> !t.isAfter(windowStart));
                return times.isEmpty();
            });
            List<Instant> recent = attempts.computeIfAbsent(key, k -> new ArrayList<>());
            if (recent.size() >= MAX_PER_WINDOW || (!recent.isEmpty() && recent.getLast().isAfter(now.minus(MIN_GAP)))) {
                return false;
            }
            recent.add(now);
            return true;
        }

        synchronized int trackedKeys() {
            return attempts.size();
        }
    }
}
