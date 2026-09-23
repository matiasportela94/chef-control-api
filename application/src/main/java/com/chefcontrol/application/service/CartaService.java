package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.Carta;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.repository.CartaRepository;
import com.chefcontrol.domain.repository.MenuItemRepository;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRUD de cartas y alta/baja de platos en cada una. Quién puede llamar a esto lo decide
 * PERM_MENU_* en el controller: una carta es parte del menú, no un recurso con permisos propios.
 *
 * Un plato solo se agrega a una carta si existe en el catálogo del restaurante y está activo —
 * una carta no puede ofrecer algo dado de baja.
 */
@Service
@RequiredArgsConstructor
public class CartaService {

    private final CartaRepository cartaRepository;
    private final MenuItemRepository menuItemRepository;
    private final AuditService auditService;

    public List<Carta> listCartas() {
        return cartaRepository.findAllByRestaurantId(TenantContext.require());
    }

    public Carta getCarta(UUID id) {
        return cartaRepository.findByIdAndRestaurantId(id, TenantContext.require())
                .orElseThrow(() -> AppException.notFound(ErrorCode.CARTA_NOT_FOUND, "Carta not found"));
    }

    /** Los platos de una carta, paginados — mismo shape que GET /menu-items. */
    public Page<MenuItem> listCartaMenuItems(UUID cartaId, PageRequest pageRequest) {
        Carta carta = getCarta(cartaId);
        if (carta.getMenuItemIds().isEmpty()) {
            return Page.empty(pageRequest.page(), pageRequest.size());
        }
        return menuItemRepository.findByRestaurantIdAndActiveAndIdIn(
                carta.getRestaurantId(), true, carta.getMenuItemIds(), pageRequest);
    }

    @Transactional
    public Carta createCarta(String name, List<UUID> menuItemIds) {
        UUID restaurantId = TenantContext.require();
        requireAvailableName(restaurantId, name);

        Carta carta = Carta.builder()
                .restaurantId(restaurantId)
                .name(name)
                .active(true)
                .build();
        for (MenuItem item : requireSellableMenuItems(menuItemIds)) {
            carta.addItem(item.getId());
        }

        carta = cartaRepository.save(carta);
        auditService.log(AuditAction.CARTA_CREATED, "Carta", carta.getId(),
                Map.of("name", name, "itemCount", carta.itemCount()));
        return carta;
    }

    @Transactional
    public Carta updateCarta(UUID id, String name, Boolean active) {
        Carta carta = getCarta(id);
        if (name != null && !name.equalsIgnoreCase(carta.getName())) {
            requireAvailableName(carta.getRestaurantId(), name);
        }
        if (name != null) carta.setName(name);
        if (active != null) {
            if (active) carta.activate(); else carta.deactivate();
        }

        carta = cartaRepository.save(carta);
        auditService.log(AuditAction.CARTA_UPDATED, "Carta", carta.getId(),
                Map.of("name", carta.getName(), "active", carta.isActive()));
        return carta;
    }

    /**
     * Borra la carta, no los platos: el catálogo queda intacto y los mismos platos pueden
     * seguir en otras cartas.
     */
    @Transactional
    public void deleteCarta(UUID id) {
        Carta carta = getCarta(id);
        cartaRepository.delete(carta.getId());
        auditService.log(AuditAction.CARTA_DELETED, "Carta", carta.getId(),
                Map.of("name", carta.getName(), "itemCount", carta.itemCount()));
    }

    @Transactional
    public Carta addItems(UUID cartaId, List<UUID> menuItemIds) {
        Carta carta = getCarta(cartaId);
        List<MenuItem> items = requireSellableMenuItems(menuItemIds); // valida todos antes de tocar la carta

        List<UUID> added = new ArrayList<>();
        for (MenuItem item : items) {
            if (carta.addItem(item.getId())) added.add(item.getId());
        }
        if (added.isEmpty()) return carta; // ya estaban todos: no se guarda ni se audita

        carta = cartaRepository.save(carta);
        auditService.log(AuditAction.CARTA_ITEMS_ADDED, "Carta", carta.getId(),
                Map.of("name", carta.getName(), "count", added.size(), "menuItemIds", added));
        return carta;
    }

    @Transactional
    public Carta removeItems(UUID cartaId, List<UUID> menuItemIds) {
        Carta carta = getCarta(cartaId);

        List<UUID> removed = new ArrayList<>();
        for (UUID menuItemId : menuItemIds) {
            if (carta.removeItem(menuItemId)) removed.add(menuItemId);
        }
        if (removed.isEmpty()) return carta; // no estaba ninguno: no-op

        carta = cartaRepository.save(carta);
        auditService.log(AuditAction.CARTA_ITEMS_REMOVED, "Carta", carta.getId(),
                Map.of("name", carta.getName(), "count", removed.size(), "menuItemIds", removed));
        return carta;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void requireAvailableName(UUID restaurantId, String name) {
        if (cartaRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, name)) {
            throw AppException.conflict(ErrorCode.DUPLICATE_CARTA_NAME,
                    "A carta named '" + name + "' already exists");
        }
    }

    private List<MenuItem> requireSellableMenuItems(List<UUID> menuItemIds) {
        UUID restaurantId = TenantContext.require();
        return menuItemIds.stream().map(id -> {
            MenuItem item = menuItemRepository.findByIdAndRestaurantId(id, restaurantId)
                    .orElseThrow(() -> AppException.notFound(ErrorCode.MENU_ITEM_NOT_FOUND,
                            "Menu item not found: " + id));
            if (!item.isActive()) {
                throw AppException.badRequest(ErrorCode.MENU_ITEM_INACTIVE,
                        "Menu item '" + item.getName() + "' is inactive and can't be added to a carta");
            }
            return item;
        }).toList();
    }
}
