package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.repository.RestaurantRepository;
import com.chefcontrol.domain.repository.RoleRepository;
import com.chefcontrol.domain.repository.UserRestaurantRepository;
import com.chefcontrol.domain.user.Permission;
import com.chefcontrol.domain.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CRUD de roles — pertenecen a la cuenta, la comparten todos sus restaurantes. El rol de
 * sistema (SUPERADMIN) nunca se puede editar ni borrar acá: es la red de seguridad contra
 * quedar bloqueado de la propia cuenta. Fuera de eso, cualquier rol (incluidos los que venían
 * seedeados) es completamente editable — quién puede llamar a estos métodos lo decide
 * PERM_ROLES_* en el controller, no hay chequeo de rol extra acá.
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRestaurantRepository userRestaurantRepository;
    private final AuditService auditService;

    public List<Role> listRoles() {
        return roleRepository.findAllByAccountId(currentAccountId());
    }

    public Role getRole(UUID id) {
        return roleRepository.findByIdAndAccountId(id, currentAccountId())
                .orElseThrow(() -> AppException.notFound(ErrorCode.ROLE_NOT_FOUND, "Role not found"));
    }

    @Transactional
    public Role createRole(String name, Set<Permission> permissions) {
        UUID accountId = currentAccountId();
        if (roleRepository.existsByAccountIdAndNameIgnoreCase(accountId, name)) {
            throw AppException.conflict(ErrorCode.DUPLICATE_ROLE_NAME, "A role named '" + name + "' already exists");
        }
        Role role = Role.builder()
                .accountId(accountId)
                .name(name)
                .isSystem(false)
                .permissions(new HashSet<>(permissions))
                .build();
        role = roleRepository.save(role);
        auditService.log(AuditAction.ROLE_CREATED, "Role", role.getId(),
                Map.of("name", name, "permissionCount", permissions.size()));
        return role;
    }

    @Transactional
    public Role updateRole(UUID id, String name, Set<Permission> permissions) {
        Role role = getRole(id);
        if (role.isSystem()) {
            throw AppException.forbidden(ErrorCode.SYSTEM_ROLE_IMMUTABLE, "The system role can't be edited");
        }
        if (!role.getName().equalsIgnoreCase(name)
                && roleRepository.existsByAccountIdAndNameIgnoreCase(role.getAccountId(), name)) {
            throw AppException.conflict(ErrorCode.DUPLICATE_ROLE_NAME, "A role named '" + name + "' already exists");
        }
        role.setName(name);
        role.setPermissions(new HashSet<>(permissions));
        role = roleRepository.save(role);
        auditService.log(AuditAction.ROLE_UPDATED, "Role", role.getId(),
                Map.of("name", name, "permissionCount", permissions.size()));
        return role;
    }

    @Transactional
    public void deleteRole(UUID id) {
        Role role = getRole(id);
        if (role.isSystem()) {
            throw AppException.forbidden(ErrorCode.SYSTEM_ROLE_IMMUTABLE, "The system role can't be deleted");
        }
        if (userRestaurantRepository.existsByRoleIdAndIsActiveTrue(id)) {
            throw AppException.conflict(ErrorCode.ROLE_IN_USE, "Can't delete a role that still has users assigned to it");
        }
        roleRepository.delete(id);
        auditService.log(AuditAction.ROLE_DELETED, "Role", id, Map.of("name", role.getName()));
    }

    private UUID currentAccountId() {
        UUID restaurantId = TenantContext.require();
        return restaurantRepository.findByIdAndIsActiveTrue(restaurantId)
                .orElseThrow(() -> new IllegalStateException("Restaurant not found"))
                .getAccountId();
    }
}
