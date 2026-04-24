package com.firstticket.common.messaging.event;

import java.util.UUID;

public record OutboxEvent(
    String correlationId,
    String aggregateType,
    UUID aggregateId,
    String eventType,
    Object payload
) {}
