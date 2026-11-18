package com.chefcontrol.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Borra la conversación del chat de más de 30 días ({@code app.chat.history-retention-days}). La memoria no depende de
 * esto (lee solo la de hoy): es para no guardar los mensajes para siempre. Asume una sola instancia, como las demás
 * tareas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHistoryPurgeJob {

    private final ChatHistoryService chatHistoryService;

    @Value("${app.chat.history-retention-days:30}")
    private int retentionDays;

    @Scheduled(cron = "0 0 4 * * *", zone = "America/Argentina/Buenos_Aires")
    public void purgeChatHistory() {
        int deleted = chatHistoryService.purgeChatHistory(retentionDays);
        log.info("[Chat] purged {} chat messages older than {} days", deleted, retentionDays);
    }
}
