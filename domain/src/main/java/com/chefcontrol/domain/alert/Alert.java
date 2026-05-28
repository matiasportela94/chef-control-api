package com.chefcontrol.domain.alert;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class Alert {

    private UUID id;
    private UUID restaurantId;
    private UUID productId;
    private AlertType type;
    private AlertSeverity severity;
    private String message;
    private boolean isRead = false;
    private Instant resolvedAt;
    private Instant createdAt;

    public void resolve() {
        this.resolvedAt = Instant.now();
    }

    public void markRead() {
        this.isRead = true;
    }

    public boolean isResolved() {
        return resolvedAt != null;
    }
}
