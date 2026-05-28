package com.chefcontrol.application.port;

public interface NotificationService {

    void sendEmail(String toEmail, String subject, String body);

    void sendAlert(String toEmail, AlertNotification alert);

    record AlertNotification(
        String restaurantName,
        String type,
        String severity,
        String message
    ) {}
}
