package com.chefcontrol.domain.user;

import com.chefcontrol.domain.restaurant.TaxCondition;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class UserRestaurant {

    private UUID userId;
    private UUID restaurantId;

    // Denormalized from role join
    private UUID roleId;
    private String roleName;
    private boolean roleIsSystem;

    // Denormalized from restaurant join
    private String restaurantName;
    /** La cuenta dueña del restaurante: permite agrupar el switcher cuando alguien cruza cuentas. */
    private UUID restaurantAccountId;
    private String restaurantSlug;
    private String restaurantTimezone;
    private TaxCondition restaurantTaxCondition;

    // Denormalized from user join (populated when querying by restaurant)
    private String userEmail;
    private String userName;
    /** El personal de la persona (users.phone). No se muestra fuera de su cuenta: /users muestra {@link #phone}. */
    private String userPhone;

    /** El teléfono de la persona en este local, lo carga el admin en /users. Null = usa el personal. */
    private String phone;

    private boolean isActive = true;
    private Instant createdAt;

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
