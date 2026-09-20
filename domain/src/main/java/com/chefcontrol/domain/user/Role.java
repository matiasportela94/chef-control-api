package com.chefcontrol.domain.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Rol de una cuenta — persistido y editable. Reemplaza al viejo enum fijo de 4 roles
 * globales: ahora cada cuenta tiene los suyos (los restaurantes de esa cuenta los comparten)
 * y el dueño de la cuenta puede crear roles nuevos o editar los que ya existen.
 *
 * La única excepción es {@code isSystem}: ese único rol por cuenta (llamado SUPERADMIN al
 * crearse la cuenta) no se puede editar ni borrar, y siempre tiene TODOS los permisos —
 * incluidos los que se agreguen a futuro — sin guardar filas en role_permissions para eso
 * (ver {@link #effectivePermissions()}). Es la red de seguridad contra quedar bloqueado de
 * la propia cuenta si alguien edita mal un rol.
 */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Role {

    private UUID id;
    private UUID accountId;
    private String name;
    private boolean isSystem;
    private Set<Permission> permissions;
    private Instant createdAt;

    public Set<Permission> effectivePermissions() {
        return isSystem ? EnumSet.allOf(Permission.class) : permissions;
    }

    public boolean isEditable() {
        return !isSystem;
    }
}
