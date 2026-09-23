package com.chefcontrol.api.menu;

import com.chefcontrol.api.menu.dto.CreateMenuSectionRequest;
import com.chefcontrol.api.menu.dto.MenuSectionResponse;
import com.chefcontrol.api.menu.dto.ReorderMenuSectionsRequest;
import com.chefcontrol.api.menu.dto.UpdateMenuSectionRequest;
import com.chefcontrol.application.service.MenuSectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Pasos del menú. Usan los permisos PERM_MENU_* igual que las cartas: es la misma pantalla.
 */
@RestController
@RequestMapping("/menu-sections")
@RequiredArgsConstructor
public class MenuSectionController {

    private final MenuSectionService menuSectionService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_MENU_VIEW')")
    public ResponseEntity<List<MenuSectionResponse>> listMenuSections() {
        return ResponseEntity.ok(menuSectionService.listSections().stream()
                .map(MenuSectionResponse::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_MENU_CREATE')")
    public ResponseEntity<MenuSectionResponse> createMenuSection(
            @Valid @RequestBody CreateMenuSectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(MenuSectionResponse.from(
                menuSectionService.createSection(request.name(), request.color(), request.icon())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MENU_UPDATE')")
    public ResponseEntity<MenuSectionResponse> updateMenuSection(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMenuSectionRequest request) {
        return ResponseEntity.ok(MenuSectionResponse.from(
                menuSectionService.updateSection(id, request.name(), request.color(), request.icon())));
    }

    @PutMapping("/order")
    @PreAuthorize("hasAuthority('PERM_MENU_UPDATE')")
    public ResponseEntity<List<MenuSectionResponse>> reorderMenuSections(
            @Valid @RequestBody ReorderMenuSectionsRequest request) {
        return ResponseEntity.ok(menuSectionService.reorder(request.sectionIds()).stream()
                .map(MenuSectionResponse::from).toList());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MENU_DELETE')")
    public ResponseEntity<Void> deleteMenuSection(@PathVariable UUID id) {
        menuSectionService.deleteSection(id);
        return ResponseEntity.noContent().build();
    }
}
