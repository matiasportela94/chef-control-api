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
    private boolean isActive = true;
    private Instant createdAt;
}
