package com.chefcontrol.api.shared;

import com.chefcontrol.domain.user.Permission;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.EnumSet;
import java.util.Set;

/** Las authorities PERM_X del JWT, para los controllers que deciden permisos según el contenido del pedido. */
public final class Authorities {

    private Authorities() {}

    public static Set<Permission> permissionsOf(Authentication authentication) {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        for (Permission p : Permission.values()) {
            if (hasAuthority(authentication, "PERM_" + p.name())) permissions.add(p);
        }
        return permissions;
    }

    public static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(authority::equals);
    }
}
