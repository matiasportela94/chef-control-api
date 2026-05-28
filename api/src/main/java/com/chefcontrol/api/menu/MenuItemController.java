package com.chefcontrol.api.menu;

import com.chefcontrol.api.menu.dto.*;
import com.chefcontrol.api.shared.PagedResponse;
import com.chefcontrol.application.service.MenuItemService;
import com.chefcontrol.application.service.MenuItemService.*;
import com.chefcontrol.domain.shared.PageRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/menu-items")
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService menuItemService;

    @GetMapping
    public ResponseEntity<PagedResponse<MenuItemResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(PagedResponse.of(
                menuItemService.listMenuItems(PageRequest.of(page, size)).map(MenuItemResponse::from)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MenuItemResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(MenuItemResponse.from(menuItemService.getMenuItem(id)));
    }

    @PostMapping
    public ResponseEntity<MenuItemResponse> create(@Valid @RequestBody CreateMenuItemRequest request) {
        var cmd = new CreateMenuItemCommand(
                request.name(), request.description(), request.price(), request.category());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MenuItemResponse.from(menuItemService.createMenuItem(cmd)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MenuItemResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMenuItemRequest request) {
        var cmd = new UpdateMenuItemCommand(
                request.name(), request.description(), request.price(), request.category());
        return ResponseEntity.ok(MenuItemResponse.from(menuItemService.updateMenuItem(id, cmd)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        menuItemService.deactivateMenuItem(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/recipe")
    public ResponseEntity<RecipeResponse> getRecipe(@PathVariable UUID id) {
        return menuItemService.getRecipe(id)
                .map(recipe -> ResponseEntity.ok(RecipeResponse.from(recipe)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/recipe")
    public ResponseEntity<RecipeResponse> setRecipe(
            @PathVariable UUID id,
            @Valid @RequestBody SetRecipeRequest request) {
        var cmd = new SetRecipeCommand(
                request.servings(),
                request.items().stream()
                        .map(i -> new RecipeItemCommand(i.productId(), i.unitId(), i.quantity()))
                        .toList());
        return ResponseEntity.ok(RecipeResponse.from(menuItemService.setRecipe(id, cmd)));
    }

    @DeleteMapping("/{id}/recipe")
    public ResponseEntity<Void> deleteRecipe(@PathVariable UUID id) {
        menuItemService.deleteRecipe(id);
        return ResponseEntity.noContent().build();
    }
}
