package com.chefcontrol.application.port;

import java.util.UUID;

public interface CurrentUserProvider {
    UUID currentUserId();
    String currentRole();
}
