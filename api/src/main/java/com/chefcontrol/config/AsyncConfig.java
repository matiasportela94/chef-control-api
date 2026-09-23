package com.chefcontrol.config;

import com.chefcontrol.domain.context.RequestIpContext;
import com.chefcontrol.domain.context.TenantContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "messageExecutor")
    public Executor messageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("msg-processor-");
        executor.setRejectedExecutionHandler(new CallerRunsPolicy());
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * El pool de {@code @Async} corre en hilos propios: por default NO heredan el
     * {@link SecurityContextHolder} ni los ThreadLocal de dominio ({@link TenantContext},
     * {@link RequestIpContext}) del hilo que encoló la tarea. Sin este decorator,
     * JpaAuditService.persist() corre con actor/tenant/ip siempre en null.
     */
    static class ContextPropagatingTaskDecorator implements TaskDecorator {
        @Override
        @NonNull
        public Runnable decorate(@NonNull Runnable runnable) {
            SecurityContext securityContext = SecurityContextHolder.getContext();
            UUID tenantId = TenantContext.get();
            String ip = RequestIpContext.get();
            return () -> {
                try {
                    SecurityContextHolder.setContext(securityContext);
                    if (tenantId != null) TenantContext.set(tenantId);
                    if (ip != null) RequestIpContext.set(ip);
                    runnable.run();
                } finally {
                    SecurityContextHolder.clearContext();
                    TenantContext.clear();
                    RequestIpContext.clear();
                }
            };
        }
    }
}
