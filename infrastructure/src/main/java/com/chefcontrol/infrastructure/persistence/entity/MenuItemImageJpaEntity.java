package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.menu.MenuItemImage;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "menu_item_images")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class MenuItemImageJpaEntity {

    @Id
    @Column(name = "menu_item_id")
    private UUID menuItemId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    /**
     * Sin @Lob a proposito: con PostgreSQL, @Lob sobre byte[] hace que Hibernate espere una
     * columna oid (large object) y la validacion de esquema falla contra bytea. byte[] pelado
     * mapea a bytea, que es lo que crea V19.
     */
    @Column(nullable = false)
    private byte[] bytes;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static MenuItemImageJpaEntity from(MenuItemImage domain) {
        return MenuItemImageJpaEntity.builder()
                .menuItemId(domain.getMenuItemId())
                .contentType(domain.getContentType())
                .bytes(domain.getBytes())
                .sizeBytes(domain.getSizeBytes())
                .updatedAt(domain.getUpdatedAt() != null ? domain.getUpdatedAt() : Instant.now())
                .build();
    }

    public MenuItemImage toDomain() {
        return MenuItemImage.builder()
                .menuItemId(menuItemId)
                .contentType(contentType)
                .bytes(bytes)
                .sizeBytes(sizeBytes)
                .updatedAt(updatedAt)
                .build();
    }
}
