package com.chefcontrol.api.role;

import com.chefcontrol.api.role.dto.CreateRoleRequest;
import com.chefcontrol.api.role.dto.RoleResponse;
import com.chefcontrol.api.role.dto.UpdateRoleRequest;
import com.chefcontrol.application.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_ROLES_VIEW')")
    public ResponseEntity<List<RoleResponse>> listRoles() {
        return ResponseEntity.ok(roleService.listRoles().stream().map(RoleResponse::from).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_ROLES_VIEW')")
    public ResponseEntity<RoleResponse> getRole(@PathVariable UUID id) {
        return ResponseEntity.ok(RoleResponse.from(roleService.getRole(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_ROLES_CREATE')")
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        var role = roleService.createRole(request.name(), request.permissions());
        return ResponseEntity.status(HttpStatus.CREATED).body(RoleResponse.from(role));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_ROLES_UPDATE')")
    public ResponseEntity<RoleResponse> updateRole(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        var role = roleService.updateRole(id, request.name(), request.permissions());
        return ResponseEntity.ok(RoleResponse.from(role));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_ROLES_DELETE')")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }
}
