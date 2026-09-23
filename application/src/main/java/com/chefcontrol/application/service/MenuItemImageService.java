package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.image.ImageFormat;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.menu.MenuItemImage;
import com.chefcontrol.domain.repository.MenuItemImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * La foto de un plato: opcional, una por plato.
 *
 * El frontend redimensiona antes de subir, pero eso es una cortesía del cliente, no una
 * garantía: cualquiera puede pegarle a la API directo. Acá se valida de nuevo el tamaño y,
 * sobre todo, el formato real por magic bytes — el Content-Type lo escribe quien sube.
 */
@Service
@RequiredArgsConstructor
public class MenuItemImageService {

    /** 2MB de techo. El navegador manda ~150KB; esto es para las llamadas directas. */
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    private final MenuItemImageRepository menuItemImageRepository;
    private final MenuItemService menuItemService;
    private final AuditService auditService;

    public Optional<MenuItemImage> getImage(UUID menuItemId) {
        menuItemService.getMenuItem(menuItemId); // valida que el plato sea de este restaurante
        return menuItemImageRepository.findByMenuItemId(menuItemId);
    }

    public Map<UUID, Instant> imageVersions(Set<UUID> menuItemIds) {
        return menuItemImageRepository.findUpdatedAtByMenuItemIds(menuItemIds);
    }

    @Transactional
    public MenuItemImage putImage(UUID menuItemId, byte[] data) {
        MenuItem item = menuItemService.getMenuItem(menuItemId);

        if (data == null || data.length == 0) {
            throw AppException.badRequest(ErrorCode.IMAGE_EMPTY, "The image is empty");
        }
        if (data.length > MAX_BYTES) {
            throw AppException.badRequest(ErrorCode.IMAGE_TOO_LARGE,
                    "The image is larger than " + (MAX_BYTES / 1024 / 1024) + "MB");
        }
        ImageFormat format = ImageFormat.detect(data)
                .orElseThrow(() -> AppException.badRequest(ErrorCode.IMAGE_FORMAT_NOT_SUPPORTED,
                        "Only JPEG, PNG, WEBP and GIF images are supported"));

        MenuItemImage image = MenuItemImage.builder()
                .menuItemId(item.getId())
                .contentType(format.contentType())
                .bytes(data)
                .sizeBytes(data.length)
                .updatedAt(Instant.now())
                .build();

        image = menuItemImageRepository.save(image);
        auditService.log(AuditAction.MENU_ITEM_IMAGE_UPDATED, "MenuItem", item.getId(),
                Map.of("name", item.getName(), "sizeBytes", data.length, "format", format.name()));
        return image;
    }

    @Transactional
    public void deleteImage(UUID menuItemId) {
        MenuItem item = menuItemService.getMenuItem(menuItemId);
        menuItemImageRepository.deleteByMenuItemId(item.getId());
        auditService.log(AuditAction.MENU_ITEM_IMAGE_DELETED, "MenuItem", item.getId(),
                Map.of("name", item.getName()));
    }
}
