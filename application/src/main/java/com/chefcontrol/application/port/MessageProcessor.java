package com.chefcontrol.application.port;

import com.chefcontrol.domain.message.IncomingMessage;

public interface MessageProcessor {

    void enqueue(IncomingMessage message);
}
