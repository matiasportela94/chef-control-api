package com.chefcontrol.application.port;

import java.util.List;

public interface WhatsAppService {

    void sendText(String toPhone, String message);

    void sendWelcome(String toPhone, String userName, String restaurantName);

    void sendUnregisteredMessage(String toPhone);

    void sendRestaurantPicker(String toPhone, List<RestaurantOption> options);

    void sendSetPasswordLink(String toPhone, String userName, String restaurantName, String setPasswordLink);

    void sendPasswordResetLink(String toPhone, String userName, String resetLink);

    record RestaurantOption(String id, String name) {}
}
