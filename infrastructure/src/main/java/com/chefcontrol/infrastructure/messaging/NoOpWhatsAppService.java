package com.chefcontrol.infrastructure.messaging;

import com.chefcontrol.application.port.WhatsAppService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Placeholder until the Meta WhatsApp Business API adapter is implemented.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(value = WhatsAppService.class, ignored = NoOpWhatsAppService.class)
public class NoOpWhatsAppService implements WhatsAppService {

    @Override
    public void sendText(String toPhone, String message) {
        log.debug("[WA no-op] → {} : {}", toPhone, message);
    }

    @Override
    public void sendWelcome(String toPhone, String userName, String restaurantName) {
        log.debug("[WA no-op] welcome → {} ({}, {})", toPhone, userName, restaurantName);
    }

    @Override
    public void sendUnregisteredMessage(String toPhone) {
        log.debug("[WA no-op] unregistered → {}", toPhone);
    }

    @Override
    public void sendRestaurantPicker(String toPhone, List<RestaurantOption> options) {
        log.debug("[WA no-op] restaurant-picker → {} options:{}", toPhone, options.size());
    }

    @Override
    public void sendSetPasswordLink(String toPhone, String userName, String restaurantName, String setPasswordLink) {
        log.warn("[WA no-op] Set-password link for {} ({}) — link: {}", toPhone, restaurantName, setPasswordLink);
    }

    @Override
    public void sendPasswordResetLink(String toPhone, String userName, String resetLink) {
        log.warn("[WA no-op] Password reset link for {} — link: {}", toPhone, resetLink);
    }
}
