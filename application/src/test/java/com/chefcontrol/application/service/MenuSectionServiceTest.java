package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuSection;
import com.chefcontrol.domain.repository.MenuItemRepository;
import com.chefcontrol.domain.repository.MenuSectionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * ponytail: solo lo que puede romperse — que reordenar valide todo antes de escribir nada,
 * que no escriba lo que no cambió, y que no se pueda borrar un paso que todavía tiene platos.
 */
@ExtendWith(MockitoExtension.class)
class MenuSectionServiceTest {

    @Mock MenuSectionRepository menuSectionRepository;
    @Mock MenuItemRepository menuItemRepository;
    @Mock AuditService auditService;

    private final UUID restaurantId = UUID.randomUUID();

    private MenuSectionService service() {
        return new MenuSectionService(menuSectionRepository, menuItemRepository, auditService);
    }

    private MenuSection section(UUID id, String name, int order) {
        return MenuSection.builder()
                .id(id).restaurantId(restaurantId).name(name).sortOrder(order).build();
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
    void reorder_oneIdFromAnotherRestaurant_writesNothing() {
        UUID ok      = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        when(menuSectionRepository.findByIdAndRestaurantId(ok, restaurantId))
                .thenReturn(Optional.of(section(ok, "Entradas", 0)));
        when(menuSectionRepository.findByIdAndRestaurantId(foreign, restaurantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().reorder(List.of(ok, foreign)))
                .isInstanceOf(AppException.class);

        verify(menuSectionRepository, never()).save(any());
    }

    @Test
    void reorder_onlySavesWhatMoved() {
        UUID first  = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        // "first" ya está en la posición 0; solo "second" cambia de lugar
        when(menuSectionRepository.findByIdAndRestaurantId(first, restaurantId))
                .thenReturn(Optional.of(section(first, "Entradas", 0)));
        when(menuSectionRepository.findByIdAndRestaurantId(second, restaurantId))
                .thenReturn(Optional.of(section(second, "Postres", 5)));
        when(menuSectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().reorder(List.of(first, second));

        ArgumentCaptor<MenuSection> saved = ArgumentCaptor.forClass(MenuSection.class);
        verify(menuSectionRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(second);
        assertThat(saved.getValue().getSortOrder()).isEqualTo(1);
    }

    @Test
    void delete_sectionWithDishes_throws() {
        UUID id = UUID.randomUUID();
        when(menuSectionRepository.findByIdAndRestaurantId(id, restaurantId))
                .thenReturn(Optional.of(section(id, "Principales", 1)));
        when(menuItemRepository.existsBySectionId(id)).thenReturn(true);

        assertThatThrownBy(() -> service().deleteSection(id)).isInstanceOf(AppException.class);

        verify(menuSectionRepository, never()).delete(any());
    }

    @Test
    void create_duplicateName_throws() {
        when(menuSectionRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, "Postres"))
                .thenReturn(true);

        assertThatThrownBy(() -> service().createSection("Postres", "#000000"))
                .isInstanceOf(AppException.class);

        verify(menuSectionRepository, never()).save(any());
    }

    @Test
    void create_goesLastInTheOrder() {
        when(menuSectionRepository.findAllByRestaurantId(restaurantId)).thenReturn(List.of(
                section(UUID.randomUUID(), "Entradas", 0),
                section(UUID.randomUUID(), "Principales", 1)));
        when(menuSectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MenuSection created = service().createSection("Postres", "#000000");

        assertThat(created.getSortOrder()).isEqualTo(2);
    }
}
