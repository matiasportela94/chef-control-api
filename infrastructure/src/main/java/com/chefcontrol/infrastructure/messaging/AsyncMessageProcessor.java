package com.chefcontrol.infrastructure.messaging;

import com.chefcontrol.domain.message.IncomingMessage;
import com.chefcontrol.application.port.MessageProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class AsyncMessageProcessor implements MessageProcessor {

    @Async("messageExecutor")
    @Override
    public void enqueue(IncomingMessage message) {
        log.debug("Processing message from {}", message.fromPhone());
        // TODO Fase 2: delegar a MessageProcessingService
    }
}
