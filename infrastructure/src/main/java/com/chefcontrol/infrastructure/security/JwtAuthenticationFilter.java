package com.chefcontrol.infrastructure.security;

import com.chefcontrol.domain.context.RequestIpContext;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.security.ChefControlPrincipal;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        RequestIpContext.set(clientIp(request));
        try {
            String token = extractToken(request);
            if (token != null && jwtTokenProvider.validateToken(token)) {
                Claims claims = jwtTokenProvider.getClaims(token);

                String activeRestaurantIdStr = claims.get("activeRestaurantId", String.class);
                UUID activeRestaurantId = activeRestaurantIdStr != null
                        ? UUID.fromString(activeRestaurantIdStr) : null;

                if (activeRestaurantId != null) {
                    TenantContext.set(activeRestaurantId);
                }

                String role = claims.get("role", String.class);
                @SuppressWarnings("unchecked")
                List<String> permissions = claims.get("permissions", List.class);
                if (permissions == null) permissions = List.of(); // tokens viejos, emitidos antes del claim

                var principal = new ChefControlPrincipal(
                        UUID.fromString(claims.get("userId", String.class)),
                        claims.getSubject(),
                        activeRestaurantId,
                        role,
                        permissions);

                List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority("PERM_" + p)));

                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception e) {
            log.debug("Could not set user authentication: {}", e.getMessage());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            RequestIpContext.clear();
        }
    }

    /** Prioriza X-Forwarded-For (Railway/Vercel están detrás de proxy) sobre la IP directa del socket. */
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Extracts the JWT from the request.
     * Priority: Authorization header (Bearer) → httpOnly cookie.
     * The header takes precedence to support Swagger and API clients.
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(c -> "auth_token".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }
}
