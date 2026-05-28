package com.chefcontrol.application.port;

/**
 * Port for transactional emails. Today backed by a no-op stub;
 * wire a SendGrid (or SMTP) implementation when ready.
 */
public interface EmailService {

    void sendPasswordResetLink(String toEmail, String userName, String resetLink);

    void sendSetPasswordLink(String toEmail, String userName, String restaurantName, String setPasswordLink);
}
