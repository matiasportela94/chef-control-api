package com.chefcontrol.domain.message;

import lombok.Builder;

@Builder
public record IncomingMessage(
    String fromPhone,
    String channel,
    String contentType,
    String content,
    String mediaUrl
) {}
