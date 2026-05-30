package com.chefcontrol.api.auth;

import com.chefcontrol.api.auth.dto.ForgotPasswordRequest;
import com.chefcontrol.api.auth.dto.LoginRequest;
import com.chefcontrol.api.auth.dto.LoginResponse;
import com.chefcontrol.api.auth.dto.RegisterRequest;
import com.chefcontrol.api.auth.dto.ResetPasswordRequest;
import com.chefcontrol.api.auth.dto.SwitchRestaurantRequest;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.service.PasswordResetService;
import com.chefcontrol.application.service.RestaurantRegistrationService;
import com.chefcontrol.application.service.RestaurantRegistrationService.RegisterCommand;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.repository.UserRepository;
import com.chefcontrol.domain.repository.UserRestaurantRepository;
import com.chefcontrol.domain.security.ChefControlPrincipal;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final UserRestaurantRepository userRestaurantRepository;
    private final PasswordResetService passwordResetService;
    private final RestaurantRegistrationService registrationService;
    private final AuditService auditService;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request,
                                                  HttpServletResponse response) {
        var cmd = new RegisterCommand(
                request.restaurantName(), request.ownerName(),
                request.ownerEmail(), request.ownerPassword(),
                request.ownerPhone(), request.timezone());

        var result = registrationService.register(cmd);
        String token = jwtTokenProvider.generateToken(result.owner(), result.restaurant().getId(), result.memberships());
        setAuthCookie(response, token);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buildLoginResponse(result.owner(), result.restaurant().getId(), result.memberships()));
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

        List<UserRestaurant> memberships = userRestaurantRepository.findActiveByUserId(user.getId());
        if (memberships.isEmpty()) {
            throw AppException.forbidden(ErrorCode.NO_ACTIVE_MEMBERSHIPS, "No active restaurant memberships");
        }

        UUID activeRestaurantId = memberships.getFirst().getRestaurantId();
        String token = jwtTokenProvider.generateToken(user, activeRestaurantId, memberships);
        LoginResponse loginResponse = buildLoginResponse(user, activeRestaurantId, memberships);

        auditService.log(AuditAction.USER_LOGIN, "User", user.getId(),
                Map.of("restaurantId", activeRestaurantId, "role", loginResponse.role()));

        setAuthCookie(response, token);
        return ResponseEntity.ok(loginResponse);
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
                .sameSite("Strict")
                .path("/")
                .maxAge(jwtExpirationMs / 1000)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearAuthCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("auth_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
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

        List<LoginResponse.RestaurantSummary> restaurants = memberships.stream()
                .map(ur -> new LoginResponse.RestaurantSummary(
                        ur.getRestaurantId(),
                        ur.getRestaurantName(),
                        ur.getRoleName().name()))
                .toList();

        return new LoginResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                activeRestaurantId,
                active.getRestaurantName(),
                active.getRoleName().name(),
                restaurants);
    }
}
