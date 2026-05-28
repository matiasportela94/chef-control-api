package com.chefcontrol.infrastructure.security;

import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.security.ChefControlPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SecurityCurrentUserProvider implements CurrentUserProvider {

    @Override
    public UUID currentUserId() {
        return principal().userId();
    }

    @Override
    public String currentRole() {
        return principal().role();
    }

    private ChefControlPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof ChefControlPrincipal principal) {
            return principal;
        }
        throw new IllegalStateException("No authenticated principal in security context");
    }
}
