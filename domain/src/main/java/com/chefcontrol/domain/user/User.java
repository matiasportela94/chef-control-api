package com.chefcontrol.domain.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class User {

    private UUID id;
    private String email;
    private String passwordHash;
    private String name;
    private String phone;
    /** Claim "sub" de Google si entra con Google; null si solo tiene contraseña. */
    private String googleSubject;
    private boolean isActive = true;
    private Instant createdAt;

    /**
     * El email se guarda y se busca sin mayúsculas ni espacios: sin esto, invitar a "Juan@Gmail.com" cuando existía
     * "juan@gmail.com" creaba un segundo usuario. Lo usan este setter (toda escritura) y las búsquedas del repositorio.
     * V40 lo exige en la base con un CHECK.
     */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public void setEmail(String email) {
        this.email = normalizeEmail(email);
    }
}
