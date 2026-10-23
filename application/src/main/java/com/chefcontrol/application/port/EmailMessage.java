package com.chefcontrol.application.port;

/**
 * Provider-agnostic email. {@code text} is the plain-text alternative for clients that don't
 * render HTML; both parts are always sent.
 */
public record EmailMessage(String to, String subject, String html, String text) {
}
