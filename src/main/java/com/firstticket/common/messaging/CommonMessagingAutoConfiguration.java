package com.firstticket.common.messaging;

import com.firstticket.common.messaging.annotation.IdempotentAspect;
import com.firstticket.common.messaging.event.Events;
import com.firstticket.common.messaging.event.OutboxEventListener;
import com.firstticket.common.messaging.event.OutboxTransactionHandler;
import com.firstticket.common.messaging.inbox.InboxRepository;
import com.firstticket.common.messaging.outbox.OutboxRepository;
import com.firstticket.common.messaging.scheduler.MessagingCleanupScheduler;
import com.firstticket.common.messaging.scheduler.OutboxRelayScheduler;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class CommonMessagingAutoConfiguration {

    @Bean
    public Events events() {
        return new Events();
    }

    @Bean
    public OutboxTransactionHandler outboxTransactionHandler(
        OutboxRepository outboxRepository,
        KafkaTemplate<String, String> kafkaTemplate) {
        return new OutboxTransactionHandler(outboxRepository, kafkaTemplate);
    }

    @Bean
    public OutboxEventListener outboxEventListener(
        OutboxRepository outboxRepository,
        KafkaTemplate<String, String> kafkaTemplate,
        OutboxTransactionHandler outboxTransactionHandler
    ) {
        return new OutboxEventListener(outboxRepository, kafkaTemplate, outboxTransactionHandler);
    }

    @Bean
    @ConditionalOnProperty(value = "common.messaging.scheduler.enabled", havingValue = "true", matchIfMissing = true)
    public OutboxRelayScheduler outboxRelayScheduler(
        OutboxRepository outboxRepository,
        KafkaTemplate<String, String> kafkaTemplate,
        OutboxTransactionHandler outboxTransactionHandler) {
        return new OutboxRelayScheduler(outboxRepository, kafkaTemplate, outboxTransactionHandler);
    }

    @Bean
    public IdempotentAspect idempotentAspect(InboxRepository inboxRepository) {
        return new IdempotentAspect(inboxRepository);
    }

    @Bean
    public MessagingCleanupScheduler messagingCleanupScheduler(JPAQueryFactory queryFactory) {
        return new MessagingCleanupScheduler(queryFactory);
    }
}
