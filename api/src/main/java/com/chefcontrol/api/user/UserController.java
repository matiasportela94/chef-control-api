package com.chefcontrol.api.user;

import com.chefcontrol.api.user.dto.CreateUserRequest;
import com.chefcontrol.api.user.dto.CreateUserResponse;
import com.chefcontrol.api.user.dto.UpdateUserRequest;
import com.chefcontrol.api.user.dto.UserResponse;
import com.chefcontrol.application.service.UserManagementService;
import com.chefcontrol.application.service.UserManagementService.CreateUserCommand;
import com.chefcontrol.application.service.UserManagementService.UpdateUserCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserManagementService userManagementService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(
                userManagementService.listUsers().stream().map(UserResponse::from).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(UserResponse.from(userManagementService.getUser(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<CreateUserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        var command = new CreateUserCommand(
                request.name(),
                request.email(),
                request.phone(),
                request.role());
        var result = userManagementService.createUser(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new CreateUserResponse(UserResponse.from(result.membership())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        var command = new UpdateUserCommand(request.name(), request.phone(), request.role());
        return ResponseEntity.ok(UserResponse.from(userManagementService.updateUser(id, command)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<Void> deactivateUser(@PathVariable UUID id) {
        userManagementService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }
}
