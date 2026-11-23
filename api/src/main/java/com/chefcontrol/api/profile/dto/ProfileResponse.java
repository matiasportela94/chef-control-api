package com.chefcontrol.api.profile.dto;

import com.chefcontrol.domain.user.User;

/** @param googleLinked si tiene Google vinculado; el front lo lee de acá y no de la sesión, que queda vieja */
public record ProfileResponse(String name, String email, String phone, boolean googleLinked) {

    public static ProfileResponse from(User user) {
        return new ProfileResponse(user.getName(), user.getEmail(), user.getPhone(), user.getGoogleSubject() != null);
    }
}
