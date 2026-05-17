package com.firstticket.common.messaging;

import com.firstticket.common.messaging.annotation.IdempotentAspect;
import com.firstticket.common.messaging.event.Events;
import com.firstticket.common.messaging.event.OutboxEventListener;
import com.firstticket.common.messaging.event.OutboxTransactionHandler;
import com.firstticket.common.messaging.inbox.InboxRepository;
import com.firstticket.common.messaging.outbox.OutboxRepository;
import com.firstticket.common.messaging.scheduler.InboxCleanupScheduler;
import com.firstticket.common.messaging.scheduler.OutboxCleanupScheduler;
import com.firstticket.common.messaging.scheduler.OutboxRelayScheduler;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperties;
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

    // =====================================================
    // Outbox (messaging.outbox.enabled: true)
    // =====================================================

    @Bean
    @ConditionalOnProperty(name = "messaging.outbox.enabled", havingValue = "true", matchIfMissing = false)
    public OutboxTransactionHandler outboxTransactionHandler(
        OutboxRepository outboxRepository,
        KafkaTemplate<String, String> kafkaTemplate) {
        return new OutboxTransactionHandler(outboxRepository, kafkaTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "messaging.outbox.enabled", havingValue = "true", matchIfMissing = false)
    public OutboxEventListener outboxEventListener(
        OutboxRepository outboxRepository,
        KafkaTemplate<String, String> kafkaTemplate,
        OutboxTransactionHandler outboxTransactionHandler
    ) {
        return new OutboxEventListener(outboxRepository, kafkaTemplate, outboxTransactionHandler);
    }

    // outbox.enabled: true AND outbox.scheduler.enabled: true (기본값) 시 활성화
    @Bean
    @ConditionalOnProperties({
        @ConditionalOnProperty(name = "messaging.outbox.enabled", havingValue = "true", matchIfMissing = false),
        @ConditionalOnProperty(name = "messaging.outbox.scheduler.enabled", havingValue = "true", matchIfMissing = true)
    })
    public OutboxRelayScheduler outboxRelayScheduler(
        OutboxRepository outboxRepository,
        KafkaTemplate<String, String> kafkaTemplate,
        OutboxTransactionHandler outboxTransactionHandler) {
        return new OutboxRelayScheduler(outboxRepository, kafkaTemplate, outboxTransactionHandler);
    }

    @Bean
    @ConditionalOnProperty(name = "messaging.outbox.enabled", havingValue = "true", matchIfMissing = false)
    public OutboxCleanupScheduler outboxCleanupScheduler(JPAQueryFactory queryFactory) {
        return new OutboxCleanupScheduler(queryFactory);
    }

    // =====================================================
    // Inbox (messaging.inbox.enabled: true 시 활성화)
    // =====================================================

    @Bean
    @ConditionalOnProperty(name = "messaging.inbox.enabled", havingValue = "true", matchIfMissing = false)
    public IdempotentAspect idempotentAspect(InboxRepository inboxRepository) {
        return new IdempotentAspect(inboxRepository);
    }

    @Bean
    @ConditionalOnProperty(name = "messaging.inbox.enabled", havingValue = "true", matchIfMissing = false)
    public InboxCleanupScheduler inboxCleanupScheduler(JPAQueryFactory queryFactory) {
        return new InboxCleanupScheduler(queryFactory);
    }
}
