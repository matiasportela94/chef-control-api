package com.chefcontrol.domain.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class RoleEntity {

    private UUID id;
    private RoleName name;
}
