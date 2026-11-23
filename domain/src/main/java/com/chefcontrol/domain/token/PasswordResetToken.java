package com.chefcontrol.domain.token;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class PasswordResetToken {

    private UUID id;
    private UUID userId;
    /** SHA-256 en hex del token del link: la base nunca guarda el token, así un volcado no da links que sirvan. */
    private String tokenHash;
    private TokenType type;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant createdAt = Instant.now();

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }

    public static String hash(UUID token) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // toda JVM trae SHA-256
        }
    }
}
