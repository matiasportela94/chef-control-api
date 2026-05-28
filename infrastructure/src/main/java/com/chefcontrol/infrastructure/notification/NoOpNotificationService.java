package com.chefcontrol.infrastructure.notification;

import com.chefcontrol.application.port.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Placeholder until an alert/notification channel (push, email digest, etc.) is wired.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(value = NotificationService.class, ignored = NoOpNotificationService.class)
public class NoOpNotificationService implements NotificationService {

    @Override
    public void sendEmail(String toEmail, String subject, String body) {
        log.debug("[NOTIFICATION no-op] email → {} | {}", toEmail, subject);
    }

    @Override
    public void sendAlert(String toEmail, AlertNotification alert) {
        log.debug("[NOTIFICATION no-op] alert → {} | {} {}", toEmail, alert.severity(), alert.message());
    }
}
