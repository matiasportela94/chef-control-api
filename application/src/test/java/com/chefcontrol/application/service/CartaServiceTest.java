package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.Carta;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.repository.CartaRepository;
import com.chefcontrol.domain.repository.MenuItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ponytail: cubre solo lo que puede romperse de verdad — validar todos los platos antes de
 * tocar la carta, no escribir cuando el alta/baja no cambia nada, y no dejar agregar platos
 * dados de baja del catálogo.
 */
@ExtendWith(MockitoExtension.class)
class CartaServiceTest {

    @Mock CartaRepository cartaRepository;
    @Mock MenuItemRepository menuItemRepository;
    @Mock AuditService auditService;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID cartaId = UUID.randomUUID();

    private CartaService service() {
        return new CartaService(cartaRepository, menuItemRepository, auditService);
    }

    private MenuItem menuItem(UUID id, boolean active) {
        MenuItem m = new MenuItem();
        m.setId(id);
        m.setRestaurantId(restaurantId);
        m.setName("Milanesa");
        m.setActive(active);
        return m;
    }

    private Carta carta(UUID... itemIds) {
        Carta c = Carta.builder()
                .id(cartaId)
                .restaurantId(restaurantId)
                .name("Carta actual")
                .active(true)
                .build();
        for (UUID id : itemIds) c.addItem(id);
        return c;
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
    void addItems_oneMissingId_addsNoneAndThrows() {
        UUID ok = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        when(cartaRepository.findByIdAndRestaurantId(cartaId, restaurantId)).thenReturn(Optional.of(carta()));
        when(menuItemRepository.findByIdAndRestaurantId(ok, restaurantId)).thenReturn(Optional.of(menuItem(ok, true)));
        when(menuItemRepository.findByIdAndRestaurantId(missing, restaurantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().addItems(cartaId, List.of(ok, missing)))
                .isInstanceOf(AppException.class);

        verify(cartaRepository, never()).save(any());
        verify(auditService, never()).log(any(), any(), any(), any());
    }

    @Test
    void addItems_inactiveMenuItem_throws() {
        UUID inactive = UUID.randomUUID();
        when(cartaRepository.findByIdAndRestaurantId(cartaId, restaurantId)).thenReturn(Optional.of(carta()));
        when(menuItemRepository.findByIdAndRestaurantId(inactive, restaurantId))
                .thenReturn(Optional.of(menuItem(inactive, false)));

        assertThatThrownBy(() -> service().addItems(cartaId, List.of(inactive)))
                .isInstanceOf(AppException.class);

        verify(cartaRepository, never()).save(any());
    }

    @Test
    void addItems_alreadyInCarta_isNoOp() {
        UUID existing = UUID.randomUUID();
        when(cartaRepository.findByIdAndRestaurantId(cartaId, restaurantId))
                .thenReturn(Optional.of(carta(existing)));
        when(menuItemRepository.findByIdAndRestaurantId(existing, restaurantId))
                .thenReturn(Optional.of(menuItem(existing, true)));

        Carta result = service().addItems(cartaId, List.of(existing));

        assertThat(result.itemCount()).isEqualTo(1);
        verify(cartaRepository, never()).save(any());
        verify(auditService, never()).log(any(), any(), any(), any());
    }

    @Test
    void addItems_newItem_savesAndAudits() {
        UUID fresh = UUID.randomUUID();
        Carta stored = carta();
        when(cartaRepository.findByIdAndRestaurantId(cartaId, restaurantId)).thenReturn(Optional.of(stored));
        when(menuItemRepository.findByIdAndRestaurantId(fresh, restaurantId))
                .thenReturn(Optional.of(menuItem(fresh, true)));
        when(cartaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Carta result = service().addItems(cartaId, List.of(fresh));

        assertThat(result.contains(fresh)).isTrue();
        verify(cartaRepository).save(any());
        verify(auditService).log(eq(AuditAction.CARTA_ITEMS_ADDED), eq("Carta"), eq(cartaId), any());
    }

    @Test
    void removeItems_notInCarta_isNoOp() {
        when(cartaRepository.findByIdAndRestaurantId(cartaId, restaurantId)).thenReturn(Optional.of(carta()));

        service().removeItems(cartaId, List.of(UUID.randomUUID()));

        verify(cartaRepository, never()).save(any());
        verify(auditService, never()).log(any(), any(), any(), any());
    }

    @Test
    void createCarta_duplicateName_throws() {
        when(cartaRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, "Carta actual")).thenReturn(true);

        assertThatThrownBy(() -> service().createCarta("Carta actual", List.of()))
                .isInstanceOf(AppException.class);

        verify(cartaRepository, never()).save(any());
    }
}
