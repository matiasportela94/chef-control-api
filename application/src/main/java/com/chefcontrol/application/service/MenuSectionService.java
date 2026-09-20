package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuSection;
import com.chefcontrol.domain.repository.MenuItemRepository;
import com.chefcontrol.domain.repository.MenuSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Los pasos del menú del restaurante. Quién puede tocarlos lo decide PERM_MENU_* en el
 * controller: son parte del menú, no un recurso aparte.
 */
@Service
@RequiredArgsConstructor
public class MenuSectionService {

    private final MenuSectionRepository menuSectionRepository;
    private final MenuItemRepository menuItemRepository;
    private final AuditService auditService;

    public List<MenuSection> listSections() {
        return menuSectionRepository.findAllByRestaurantId(TenantContext.require());
    }

    public MenuSection getSection(UUID id) {
        return menuSectionRepository.findByIdAndRestaurantId(id, TenantContext.require())
                .orElseThrow(() -> AppException.notFound(ErrorCode.MENU_SECTION_NOT_FOUND, "Menu section not found"));
    }

    @Transactional
    public MenuSection createSection(String name, String color) {
        UUID restaurantId = TenantContext.require();
        requireAvailableName(restaurantId, name);

        // Al final de la lista: el orden lo decide el usuario después, no el alfabeto.
        int nextOrder = menuSectionRepository.findAllByRestaurantId(restaurantId).stream()
                .mapToInt(MenuSection::getSortOrder)
                .max().orElse(-1) + 1;

        MenuSection section = MenuSection.builder()
                .restaurantId(restaurantId)
                .name(name)
                .color(color)
                .sortOrder(nextOrder)
                .build();

        section = menuSectionRepository.save(section);
        auditService.log(AuditAction.MENU_SECTION_CREATED, "MenuSection", section.getId(),
                Map.of("name", name));
        return section;
    }

    @Transactional
    public MenuSection updateSection(UUID id, String name, String color) {
        MenuSection section = getSection(id);
        if (name != null && !name.equalsIgnoreCase(section.getName())) {
            requireAvailableName(section.getRestaurantId(), name);
        }
        if (name != null)  section.setName(name);
        if (color != null) section.setColor(color);

        section = menuSectionRepository.save(section);
        auditService.log(AuditAction.MENU_SECTION_UPDATED, "MenuSection", section.getId(),
                Map.of("name", section.getName()));
        return section;
    }

    /**
     * Reordena en bloque: la posición de cada sección es su índice en la lista recibida.
     * Se valida que todas sean del restaurante antes de escribir ninguna.
     */
    @Transactional
    public List<MenuSection> reorder(List<UUID> orderedIds) {
        List<MenuSection> sections = orderedIds.stream().map(this::getSection).toList();

        for (int i = 0; i < sections.size(); i++) {
            MenuSection section = sections.get(i);
            if (section.getSortOrder() == i) continue; // no escribe lo que no cambió
            section.setSortOrder(i);
            menuSectionRepository.save(section);
        }

        auditService.log(AuditAction.MENU_SECTIONS_REORDERED, "MenuSection", null,
                Map.of("order", orderedIds));
        return listSections();
    }

    /**
     * Borrar una sección en uso se bloquea: si no, quedan platos sin paso y la carta deja de
     * poder agruparlos. Primero se mueven los platos a otra sección.
     */
    @Transactional
    public void deleteSection(UUID id) {
        MenuSection section = getSection(id);
        if (menuItemRepository.existsBySectionId(id)) {
            throw AppException.conflict(ErrorCode.MENU_SECTION_HAS_ITEMS,
                    "Can't delete a section that still has dishes in it");
        }
        menuSectionRepository.delete(id);
        auditService.log(AuditAction.MENU_SECTION_DELETED, "MenuSection", id,
                Map.of("name", section.getName()));
    }

    private void requireAvailableName(UUID restaurantId, String name) {
        if (menuSectionRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, name)) {
            throw AppException.conflict(ErrorCode.DUPLICATE_MENU_SECTION_NAME,
                    "A section named '" + name + "' already exists");
        }
    }
}
