package com.chefcontrol.domain.context;

/**
 * IP del request HTTP actual, disponible para código que corre fuera del hilo del
 * request (p.ej. auditoría vía {@code @Async}). Mismo patrón que {@link TenantContext}.
 */
public class RequestIpContext {

    private static final ThreadLocal<String> CURRENT_IP = new ThreadLocal<>();

    public static void set(String ip) {
        CURRENT_IP.set(ip);
    }

    public static String get() {
        return CURRENT_IP.get();
    }

    public static void clear() {
        CURRENT_IP.remove();
    }
}
