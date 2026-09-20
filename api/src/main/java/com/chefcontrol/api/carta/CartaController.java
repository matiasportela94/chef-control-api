package com.chefcontrol.api.carta;

import com.chefcontrol.api.carta.dto.CartaItemsRequest;
import com.chefcontrol.api.carta.dto.CartaResponse;
import com.chefcontrol.api.carta.dto.CreateCartaRequest;
import com.chefcontrol.api.carta.dto.UpdateCartaRequest;
import com.chefcontrol.api.menu.dto.MenuItemResponse;
import com.chefcontrol.api.shared.PagedResponse;
import com.chefcontrol.application.service.CartaService;
import com.chefcontrol.domain.shared.PageRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Las cartas usan los permisos PERM_MENU_* a propósito: son parte del menú y viven en la misma
 * pantalla. Si alguna vez hace falta que alguien edite platos pero no cartas, ahí se separan.
 */
@RestController
@RequestMapping("/cartas")
@RequiredArgsConstructor
public class CartaController {

    private final CartaService cartaService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_MENU_VIEW')")
    public ResponseEntity<List<CartaResponse>> listCartas() {
        return ResponseEntity.ok(cartaService.listCartas().stream().map(CartaResponse::from).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MENU_VIEW')")
    public ResponseEntity<CartaResponse> getCarta(@PathVariable UUID id) {
        return ResponseEntity.ok(CartaResponse.from(cartaService.getCarta(id)));
    }

    @GetMapping("/{id}/menu-items")
    @PreAuthorize("hasAuthority('PERM_MENU_VIEW')")
    public ResponseEntity<PagedResponse<MenuItemResponse>> listCartaMenuItems(
                                                                              @PathVariable UUID id,
                                                                              @RequestParam(defaultValue = "0") int page,
                                                                              @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(PagedResponse.of(
                cartaService.listCartaMenuItems(id, PageRequest.of(page, size)).map(MenuItemResponse::from)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_MENU_CREATE')")
    public ResponseEntity<CartaResponse> createCarta(@Valid @RequestBody CreateCartaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CartaResponse.from(cartaService.createCarta(request.name(), request.menuItemIds())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MENU_UPDATE')")
    public ResponseEntity<CartaResponse> updateCarta(@PathVariable UUID id,
                                                     @Valid @RequestBody UpdateCartaRequest request) {
        return ResponseEntity.ok(CartaResponse.from(
                cartaService.updateCarta(id, request.name(), request.active())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MENU_DELETE')")
    public ResponseEntity<Void> deleteCarta(@PathVariable UUID id) {
        cartaService.deleteCarta(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAuthority('PERM_MENU_UPDATE')")
    public ResponseEntity<CartaResponse> addCartaItems(@PathVariable UUID id,
                                                       @Valid @RequestBody CartaItemsRequest request) {
        return ResponseEntity.ok(CartaResponse.from(cartaService.addItems(id, request.menuItemIds())));
    }

    @PostMapping("/{id}/remove-items")
    @PreAuthorize("hasAuthority('PERM_MENU_UPDATE')")
    public ResponseEntity<CartaResponse> removeCartaItems(@PathVariable UUID id,
                                                          @Valid @RequestBody CartaItemsRequest request) {
        return ResponseEntity.ok(CartaResponse.from(cartaService.removeItems(id, request.menuItemIds())));
    }
}
