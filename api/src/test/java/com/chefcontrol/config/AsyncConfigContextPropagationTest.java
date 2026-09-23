package com.chefcontrol.config;

import com.chefcontrol.domain.context.RequestIpContext;
import com.chefcontrol.domain.context.TenantContext;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ponytail: prueba puntual del bug real detrás de "ipAddress/actor nunca se popula" —
 * el pool de @Async no hereda ThreadLocal por default. Si se saca el TaskDecorator de
 * AsyncConfig, este test falla porque el hilo worker ve el contexto vacío.
 */
class AsyncConfigContextPropagationTest {

    @Test
    void decoratedRunnable_seesCallingThreadsContextOnAnotherThread() throws InterruptedException {
        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);
        RequestIpContext.set("203.0.113.7");

        Runnable decorated = new AsyncConfig.ContextPropagatingTaskDecorator().decorate(() -> {
            seenTenant.set(TenantContext.get());
            seenIp.set(RequestIpContext.get());
        });

        // Simula que el hilo del request ya siguió su curso y limpió su propio contexto
        // (como hace JwtAuthenticationFilter en su finally) antes de que corra la tarea async.
        TenantContext.clear();
        RequestIpContext.clear();

        Thread worker = new Thread(decorated);
        worker.start();
        worker.join();

        assertThat(seenTenant.get()).isEqualTo(tenantId);
        assertThat(seenIp.get()).isEqualTo("203.0.113.7");
    }

    private final AtomicReference<UUID> seenTenant = new AtomicReference<>();
    private final AtomicReference<String> seenIp = new AtomicReference<>();
}
