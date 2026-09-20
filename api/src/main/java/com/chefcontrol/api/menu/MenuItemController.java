package com.chefcontrol.api.menu;

import com.chefcontrol.api.foodcost.dto.MenuItemFoodCostResponse;
import com.chefcontrol.api.foodcost.dto.RecipeCostResponse;
import com.chefcontrol.api.menu.dto.*;
import com.chefcontrol.api.shared.PagedResponse;
import com.chefcontrol.application.service.FoodCostService;
import com.chefcontrol.application.service.MenuItemImageService;
import com.chefcontrol.application.service.MenuItemService;
import com.chefcontrol.application.service.MenuItemService.*;
import com.chefcontrol.domain.shared.PageRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/menu-items")
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService menuItemService;
    private final FoodCostService foodCostService;
    private final MenuItemImageService menuItemImageService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_MENU_VIEW')")
    public ResponseEntity<PagedResponse<MenuItemResponse>> listMenuItems(
                                                                         @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "50") int size,
                                                                         @RequestParam(defaultValue = "true") boolean active) {
        var items = menuItemService.listMenuItems(active, PageRequest.of(page, size));
        var versions = menuItemImageService.imageVersions(
                items.content().stream().map(i -> i.getId()).collect(Collectors.toSet()));
        return ResponseEntity.ok(PagedResponse.of(
                items.map(i -> MenuItemResponse.from(i, versions.get(i.getId())))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MENU_VIEW')")
    public ResponseEntity<MenuItemResponse> getMenuItem(@PathVariable UUID id) {
        var versions = menuItemImageService.imageVersions(Set.of(id));
        return ResponseEntity.ok(MenuItemResponse.from(menuItemService.getMenuItem(id), versions.get(id)));
    }

    // ── Foto del plato ───────────────────────────────────────────────────────

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_MENU_UPDATE')")
    public ResponseEntity<Void> uploadMenuItemImage(@PathVariable UUID id,
                                                    @RequestParam("file") MultipartFile file) throws IOException {
        menuItemImageService.putImage(id, file.getBytes());
        return ResponseEntity.noContent().build();
    }

    /**
     * Cache de un año e immutable: la URL lleva ?v=imageUpdatedAt, así que cuando la foto cambia
     * cambia la URL. Sin eso, immutable dejaría la foto vieja pegada en el navegador.
     */
    @GetMapping("/{id}/image")
    @PreAuthorize("hasAuthority('PERM_MENU_VIEW')")
    public ResponseEntity<byte[]> getMenuItemImage(@PathVariable UUID id) {
        var image = menuItemImageService.getImage(id);
        if (image.isEmpty()) return ResponseEntity.notFound().build();

        var img = image.get();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.getContentType()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().immutable())
                .eTag(String.valueOf(img.getUpdatedAt().toEpochMilli()))
                .body(img.getBytes());
    }

    @DeleteMapping("/{id}/image")
    @PreAuthorize("hasAuthority('PERM_MENU_UPDATE')")
    public ResponseEntity<Void> deleteMenuItemImage(@PathVariable UUID id) {
        menuItemImageService.deleteImage(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_MENU_CREATE')")
    public ResponseEntity<MenuItemResponse> createMenuItem(@Valid @RequestBody CreateMenuItemRequest request) {
        var cmd = new CreateMenuItemCommand(
                request.name(), request.description(), request.price(), request.sectionId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MenuItemResponse.from(menuItemService.createMenuItem(cmd)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MENU_UPDATE')")
    public ResponseEntity<MenuItemResponse> updateMenuItem(
                                                           @PathVariable UUID id,
                                                           @Valid @RequestBody UpdateMenuItemRequest request) {
        var cmd = new UpdateMenuItemCommand(
                request.name(), request.description(), request.price(), request.sectionId());
        return ResponseEntity.ok(MenuItemResponse.from(menuItemService.updateMenuItem(id, cmd)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MENU_DELETE')")
    public ResponseEntity<Void> deactivateMenuItem(@PathVariable UUID id) {
        menuItemService.deactivateMenuItem(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk-deactivate")
    @PreAuthorize("hasAuthority('PERM_MENU_DELETE')")
    public ResponseEntity<Void> bulkDeactivateMenuItems(@Valid @RequestBody BulkDeactivateMenuItemsRequest request) {
        menuItemService.deactivateMenuItems(request.ids());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PERM_MENU_UPDATE')")
    public ResponseEntity<MenuItemResponse> activateMenuItem(@PathVariable UUID id) {
        return ResponseEntity.ok(MenuItemResponse.from(menuItemService.activateMenuItem(id)));
    }

    @GetMapping("/{id}/recipe")
    @PreAuthorize("hasAuthority('PERM_MENU_VIEW')")
    public ResponseEntity<RecipeResponse> getRecipe(@PathVariable UUID id) {
        return menuItemService.getRecipe(id)
                .map(recipe -> ResponseEntity.ok(RecipeResponse.from(recipe)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/recipe")
    @PreAuthorize("hasAuthority('PERM_MENU_UPDATE')")
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
    @PreAuthorize("hasAuthority('PERM_MENU_DELETE')")
    public ResponseEntity<Void> deleteRecipe(@PathVariable UUID id) {
        menuItemService.deleteRecipe(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/recipe/cost")
    @PreAuthorize("hasAuthority('PERM_MENU_VIEW')")
    public ResponseEntity<RecipeCostResponse> getRecipeCost(@PathVariable UUID id) {
        return ResponseEntity.ok(RecipeCostResponse.from(foodCostService.calculateRecipeCost(id)));
    }

    @GetMapping("/{id}/food-cost")
    @PreAuthorize("hasAuthority('PERM_FOOD_COST_VIEW')")
    public ResponseEntity<MenuItemFoodCostResponse> getFoodCost(
                                                                @PathVariable UUID id,
                                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(MenuItemFoodCostResponse.from(foodCostService.calculateMenuItemFoodCost(id, from, to)));
    }
}
