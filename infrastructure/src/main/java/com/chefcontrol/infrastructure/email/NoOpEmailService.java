package com.chefcontrol.infrastructure.email;

import com.chefcontrol.application.port.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Placeholder until a real provider (SendGrid, SMTP, etc.) is wired.
 * Replace by adding a bean of type EmailService in a dedicated config class;
 * this stub will be ignored automatically via @ConditionalOnMissingBean.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(value = EmailService.class, ignored = NoOpEmailService.class)
public class NoOpEmailService implements EmailService {

    @Override
    public void sendPasswordResetLink(String toEmail, String userName, String resetLink) {
        log.warn("[EMAIL no-op] Password reset link for {} — link: {}", toEmail, resetLink);
    }

    @Override
    public void sendSetPasswordLink(String toEmail, String userName, String restaurantName, String setPasswordLink) {
        log.warn("[EMAIL no-op] Set-password link for {} ({}) — link: {}", toEmail, restaurantName, setPasswordLink);
    }
}
