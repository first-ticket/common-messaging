package com.firstticket.common.messaging.event;

import com.firstticket.common.json.JsonUtil;
import com.firstticket.common.messaging.outbox.Outbox;
import com.firstticket.common.messaging.outbox.OutboxRepository;
import com.firstticket.common.messaging.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
public class OutboxEventListener {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxTransactionHandler outboxTransactionHandler;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRED)
    public void saveOutbox(OutboxEvent event) {

        if (outboxRepository.existsByCorrelationIdAndEventType(event.correlationId(), event.eventType())) {
            log.warn("[Outbox] 중복 이벤트 무시. eventType={}, correlationId={}", event.eventType(), event.correlationId());
            return;
        }

        String payload = JsonUtil.toJson(event.payload());
        outboxRepository.saveAndFlush(Outbox.create(
            event.correlationId(),
            event.aggregateType(),
            event.aggregateId(),
            event.eventType(),
            payload
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publish(OutboxEvent event) {
        outboxRepository.findByCorrelationIdAndEventType(event.correlationId(), event.eventType())
            .filter(outbox -> outbox.getStatus() == OutboxStatus.PENDING)
            .ifPresent(outbox -> {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                    outbox.getEventType(),
                    outbox.getAggregateId().toString(),
                    outbox.getPayload()
                );
                record.headers().add("message_id", outbox.getId().toString().getBytes());

                try {
                    kafkaTemplate.send(record)
                        .whenComplete((result, e) -> {
                            if (e == null) outboxTransactionHandler.success(event.correlationId(), event.eventType());
                            else outboxTransactionHandler.failure(event.correlationId(), event.eventType(), outbox.getPayload());
                        });
                } catch (Exception e) {
                    outboxTransactionHandler.failure(event.correlationId(), event.eventType(), outbox.getPayload());
                }

            });
    }

}
