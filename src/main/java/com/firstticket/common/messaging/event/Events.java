package com.firstticket.common.messaging.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

public class Events {

    private static ApplicationEventPublisher publisher;

    @Autowired
    public void init(ApplicationEventPublisher publisher) {
        Events.publisher = publisher;
    }

    public static void publish(String correlationId, String aggregateType, UUID aggregateId, String eventType, Object payload) {
        publisher.publishEvent(new OutboxEvent(correlationId, aggregateType, aggregateId, eventType, payload));
    }
}
