package com.chefcontrol.api.auth;

import com.chefcontrol.api.auth.dto.ForgotPasswordRequest;
import com.chefcontrol.api.auth.dto.GoogleSignInRequest;
import com.chefcontrol.api.auth.dto.LinkGoogleAccountRequest;
import com.chefcontrol.api.auth.dto.LoginRequest;
import com.chefcontrol.api.auth.dto.LoginResponse;
import com.chefcontrol.api.auth.dto.RegisterRequest;
import com.chefcontrol.api.auth.dto.ResetPasswordRequest;
import com.chefcontrol.api.auth.dto.SwitchRestaurantRequest;
import com.chefcontrol.api.auth.dto.UnlinkGoogleAccountRequest;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.service.GoogleSignInService;
import com.chefcontrol.application.service.PasswordResetService;
import com.chefcontrol.application.service.PermissionResolutionService;
import com.chefcontrol.application.service.RestaurantRegistrationService;
import com.chefcontrol.application.service.RestaurantRegistrationService.RegisterCommand;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.repository.UserRepository;
import com.chefcontrol.domain.repository.AccountRepository;
import com.chefcontrol.domain.repository.UserRestaurantRepository;
import com.chefcontrol.domain.security.ChefControlPrincipal;
import com.chefcontrol.domain.shared.time.ChefControlTime;
import com.chefcontrol.domain.user.User;
import com.chefcontrol.domain.user.UserRestaurant;
import com.chefcontrol.infrastructure.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final UserRestaurantRepository userRestaurantRepository;
    private final AccountRepository accountRepository;
    private final PasswordResetService passwordResetService;
    private final RestaurantRegistrationService registrationService;
    private final PermissionResolutionService permissionResolutionService;
    private final GoogleSignInService googleSignInService;
    private final AuditService auditService;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:Strict}")
    private String cookieSameSite;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request,
                                                  HttpServletResponse response) {
        var cmd = new RegisterCommand(
                request.restaurantName(), request.ownerName(),
                request.ownerEmail(), request.ownerPassword(),
                request.ownerPhone(), request.timezone(), null);

        var result = registrationService.register(cmd);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(openSession(result.owner(), result.restaurant().getId(), result.memberships(), response));
    }

    /**
     * Entrar (o registrarse) con Google. Usuario conocido → 200 con la sesión abierta. Usuario nuevo con
     * {@code restaurantName} → 201, alta como en /register. Nuevo sin él → 404 GOOGLE_ACCOUNT_NOT_REGISTERED,
     * y el front pide el nombre y reintenta con el mismo token.
     */
    @PostMapping("/google")
    public ResponseEntity<LoginResponse> signInWithGoogle(@Valid @RequestBody GoogleSignInRequest request,
                                                          HttpServletResponse response) {
        var result = googleSignInService.signIn(request.idToken(), request.restaurantName(), request.timezone());
        var registered = result.registered();
        if (registered != null) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(openSession(registered.owner(), registered.restaurant().getId(), registered.memberships(), response));
        }
        return ResponseEntity.ok(logIn(result.user(), "google", response));
    }

    /** Vincular Google desde el perfil. Sin @PreAuthorize: cada usuario maneja su propio login, como DELETE /account. */
    @PostMapping("/google/link")
    public ResponseEntity<Void> linkGoogleAccount(@Valid @RequestBody LinkGoogleAccountRequest request,
                                                  @AuthenticationPrincipal ChefControlPrincipal principal) {
        googleSignInService.linkGoogleAccount(principal.userId(), request.idToken());
        return ResponseEntity.noContent().build();
    }

    /** POST y no DELETE porque lleva cuerpo: el token de Google que confirma y la contraseña nueva. */
    @PostMapping("/google/unlink")
    public ResponseEntity<Void> unlinkGoogleAccount(@Valid @RequestBody UnlinkGoogleAccountRequest request,
                                                    @AuthenticationPrincipal ChefControlPrincipal principal) {
        googleSignInService.unlinkGoogleAccount(principal.userId(), request.idToken(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletResponse response) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (BadCredentialsException e) {
            auditService.log(AuditAction.USER_LOGIN_FAILED, "User", null,
                    Map.of("email", request.email()));
            throw AppException.forbidden(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }

        User user = userRepository.findByEmailAndIsActiveTrue(request.email())
                .orElseThrow(() -> AppException.notFound(ErrorCode.USER_NOT_FOUND, "User not found"));

        return ResponseEntity.ok(logIn(user, "password", response));
    }

    /** Sesión en el primer restaurante activo del usuario (ya autenticado por contraseña o por Google). */
    private LoginResponse logIn(User user, String method, HttpServletResponse response) {
        List<UserRestaurant> memberships = userRestaurantRepository.findActiveByUserId(user.getId());
        if (memberships.isEmpty()) {
            throw AppException.forbidden(ErrorCode.NO_ACTIVE_MEMBERSHIPS, "No active restaurant memberships");
        }

        UUID activeRestaurantId = memberships.getFirst().getRestaurantId();
        LoginResponse loginResponse = openSession(user, activeRestaurantId, memberships, response);

        auditService.log(AuditAction.USER_LOGIN, "User", user.getId(),
                Map.of("restaurantId", activeRestaurantId, "role", loginResponse.role(), "method", method));
        return loginResponse;
    }

    private LoginResponse openSession(User user, UUID restaurantId, List<UserRestaurant> memberships,
                                      HttpServletResponse response) {
        setAuthCookie(response, jwtTokenProvider.generateToken(user, restaurantId, memberships));
        return buildLoginResponse(user, restaurantId, memberships);
    }

    @PostMapping("/switch-restaurant")
    public ResponseEntity<LoginResponse> switchRestaurant(
            @Valid @RequestBody SwitchRestaurantRequest request,
            @AuthenticationPrincipal ChefControlPrincipal principal,
            HttpServletResponse response) {

        User user = userRepository.findByEmailAndIsActiveTrue(principal.email())
                .orElseThrow(() -> AppException.notFound(ErrorCode.USER_NOT_FOUND, "User not found"));

        List<UserRestaurant> memberships = userRestaurantRepository.findActiveByUserId(user.getId());

        boolean hasAccess = memberships.stream()
                .anyMatch(ur -> ur.getRestaurantId().equals(request.restaurantId()));

        if (!hasAccess) {
            throw AppException.forbidden(ErrorCode.RESTAURANT_ACCESS_DENIED, "No access to requested restaurant");
        }

        String token = jwtTokenProvider.generateToken(user, request.restaurantId(), memberships);

        auditService.log(AuditAction.USER_RESTAURANT_SWITCHED, "Restaurant", request.restaurantId(),
                Map.of("from", principal.activeRestaurantId(), "to", request.restaurantId()));

        setAuthCookie(response, token);
        return ResponseEntity.ok(buildLoginResponse(user, request.restaurantId(), memberships));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        clearAuthCookie(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestPasswordReset(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    // ── Cookie helpers ──────────────────────────────────────────────────────

    private void setAuthCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("auth_token", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(jwtExpirationMs / 1000)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearAuthCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("auth_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // ── Response builder ────────────────────────────────────────────────────

    private LoginResponse buildLoginResponse(User user, UUID activeRestaurantId,
                                             List<UserRestaurant> memberships) {
        UserRestaurant active = memberships.stream()
                .filter(ur -> ur.getRestaurantId().equals(activeRestaurantId))
                .findFirst()
                .orElseThrow(() -> AppException.notFound(ErrorCode.RESTAURANT_NOT_FOUND, "Active restaurant not found"));

        // Los nombres de cuenta se resuelven de una y se reusan: un usuario tiene una o dos
        // cuentas, no cien, así que un mapa alcanza y evita una consulta por restaurante.
        Map<UUID, String> accountNames = memberships.stream()
                .map(UserRestaurant::getRestaurantAccountId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(HashMap::new,
                        (m, id) -> accountRepository.findById(id).ifPresent(a -> m.put(id, a.getName())),
                        HashMap::putAll);

        List<LoginResponse.RestaurantSummary> restaurants = memberships.stream()
                .map(ur -> new LoginResponse.RestaurantSummary(
                        ur.getRestaurantId(),
                        ur.getRestaurantName(),
                        ur.getRoleName(),
                        ur.getRestaurantAccountId(),
                        accountNames.get(ur.getRestaurantAccountId())))
                .toList();

        long expiresAt = ChefControlTime.nowInstant().plusMillis(jwtExpirationMs).toEpochMilli();

        List<String> permissions = permissionResolutionService
                .resolveEffectivePermissions(user.getId(), activeRestaurantId, active.getRoleId())
                .stream().map(Enum::name).toList();

        List<String> features = accountRepository.findById(active.getRestaurantAccountId())
                .map(a -> a.effectivePlan(ChefControlTime.nowInstant()).getFeatures().stream().map(Enum::name).toList())
                .orElse(List.of());

        return new LoginResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getGoogleSubject() != null,
                activeRestaurantId,
                active.getRestaurantName(),
                active.getRoleName(),
                permissions,
                features,
                expiresAt,
                restaurants);
    }
}
