package com.chefcontrol.api.profile;

import com.chefcontrol.api.profile.dto.ProfileResponse;
import com.chefcontrol.api.profile.dto.UpdateProfileRequest;
import com.chefcontrol.application.service.UserManagementService;
import com.chefcontrol.domain.security.ChefControlPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Mi perfil: el usuario de la sesión. Sin @PreAuthorize: cada uno maneja sus datos, como /auth/google/link. */
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserManagementService userManagementService;

    @GetMapping
    public ResponseEntity<ProfileResponse> getOwnProfile(@AuthenticationPrincipal ChefControlPrincipal principal) {
        return ResponseEntity.ok(ProfileResponse.from(userManagementService.getOwnProfile(principal.userId())));
    }

    @PutMapping
    public ResponseEntity<ProfileResponse> updateOwnProfile(@Valid @RequestBody UpdateProfileRequest request,
                                                            @AuthenticationPrincipal ChefControlPrincipal principal) {
        return ResponseEntity.ok(ProfileResponse.from(
                userManagementService.updateOwnProfile(principal.userId(), request.name(), request.phone())));
    }
}
