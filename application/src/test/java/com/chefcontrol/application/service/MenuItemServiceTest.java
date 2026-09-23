package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.repository.PriceHistoryRepository;
import com.chefcontrol.domain.repository.RecipeVersionRepository;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.repository.CartaRepository;
import com.chefcontrol.domain.repository.MenuItemRepository;
import com.chefcontrol.domain.repository.MenuSectionRepository;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.RecipeRepository;
import com.chefcontrol.domain.repository.UnitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ponytail: cubre solo la lógica no trivial de la baja masiva de platos —
 * validar todos los ids antes de tocar cualquiera, y un único registro de
 * auditoría por operación en vez de uno por ítem.
 */
@ExtendWith(MockitoExtension.class)
class MenuItemServiceTest {

    @Mock MenuItemRepository menuItemRepository;
    @Mock CartaRepository cartaRepository;
    @Mock MenuSectionRepository menuSectionRepository;
    @Mock RecipeRepository recipeRepository;
    @Mock ProductRepository productRepository;
    @Mock UnitRepository unitRepository;
    @Mock AuditService auditService;
    @Mock PriceHistoryRepository priceHistoryRepository;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock RecipeVersionRepository recipeVersionRepository;

    private final UUID restaurantId = UUID.randomUUID();

    private MenuItemService service() {
        return new MenuItemService(menuItemRepository, cartaRepository, menuSectionRepository,
                recipeRepository, productRepository, unitRepository, auditService,
                priceHistoryRepository, recipeVersionRepository, currentUserProvider);
    }

    private MenuItem item(UUID id, boolean active) {
        MenuItem m = new MenuItem();
        m.setId(id);
        m.setRestaurantId(restaurantId);
        m.setName("Milanesa");
        m.setActive(active);
        return m;
    }

    @BeforeEach
    void setTenant() {
        TenantContext.set(restaurantId);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void bulkDeactivate_oneMissingId_deactivatesNoneAndThrows() {
        UUID id1 = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();

        when(menuItemRepository.findByIdAndRestaurantId(id1, restaurantId)).thenReturn(Optional.of(item(id1, true)));
        when(menuItemRepository.findByIdAndRestaurantId(missingId, restaurantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deactivateMenuItems(List.of(id1, missingId)))
                .isInstanceOf(AppException.class);

        verify(menuItemRepository, never()).save(any());
        verify(auditService, never()).log(any(), any(), any(), any());
    }

    @Test
    void bulkDeactivate_allValid_deactivatesAllAndLogsOnce() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(menuItemRepository.findByIdAndRestaurantId(id1, restaurantId)).thenReturn(Optional.of(item(id1, true)));
        when(menuItemRepository.findByIdAndRestaurantId(id2, restaurantId)).thenReturn(Optional.of(item(id2, true)));
        when(menuItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().deactivateMenuItems(List.of(id1, id2));

        ArgumentCaptor<MenuItem> savedCaptor = ArgumentCaptor.forClass(MenuItem.class);
        verify(menuItemRepository, times(2)).save(savedCaptor.capture());
        assertThat(savedCaptor.getAllValues()).allMatch(m -> !m.isActive());

        verify(auditService, times(1)).log(eq(AuditAction.MENU_ITEM_BULK_DEACTIVATED), eq("MenuItem"), isNull(),
                eq(Map.of("count", 2, "menuItemIds", List.of(id1, id2))));
    }

    @Test
    void deactivate_alsoRemovesTheDishFromEveryCarta() {
        UUID id = UUID.randomUUID();
        when(menuItemRepository.findByIdAndRestaurantId(id, restaurantId)).thenReturn(Optional.of(item(id, true)));
        when(menuItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().deactivateMenuItem(id);

        verify(cartaRepository).removeMenuItemFromAllCartas(id);
    }

    @Test
    void activate_reactivatesAndLogs() {
        UUID id = UUID.randomUUID();
        MenuItem inactive = item(id, false);
        when(menuItemRepository.findByIdAndRestaurantId(id, restaurantId)).thenReturn(Optional.of(inactive));
        when(menuItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MenuItem result = service().activateMenuItem(id);

        assertThat(result.isActive()).isTrue();
        verify(auditService).log(eq(AuditAction.MENU_ITEM_REACTIVATED), eq("MenuItem"), eq(id), any());
    }
}
