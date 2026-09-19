package com.chefcontrol.infrastructure.security;

import com.chefcontrol.application.service.PermissionResolutionService;
import com.chefcontrol.domain.user.RoleName;
import com.chefcontrol.domain.user.User;
import com.chefcontrol.domain.user.UserRestaurant;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.chefcontrol.domain.shared.time.ChefControlTime;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final PermissionResolutionService permissionResolutionService;

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user, UUID activeRestaurantId, List<UserRestaurant> memberships) {
        List<String> restaurantIds = memberships.stream()
                .map(ur -> ur.getRestaurantId().toString())
                .toList();

        RoleName role = memberships.stream()
                .filter(ur -> ur.getRestaurantId().equals(activeRestaurantId))
                .map(UserRestaurant::getRoleName)
                .findFirst()
                .orElse(RoleName.READONLY);

        List<String> permissions = permissionResolutionService
                .resolveEffectivePermissions(user.getId(), activeRestaurantId, role)
                .stream().map(Enum::name).toList();

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId().toString())
                .claim("name", user.getName())
                .claim("restaurantIds", restaurantIds)
                .claim("activeRestaurantId", activeRestaurantId.toString())
                .claim("role", role.name())
                .claim("permissions", permissions)
                .issuedAt(Date.from(ChefControlTime.nowInstant()))
                .expiration(Date.from(ChefControlTime.nowInstant().plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }
}
