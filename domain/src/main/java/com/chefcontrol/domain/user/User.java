package com.chefcontrol.domain.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
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
}
