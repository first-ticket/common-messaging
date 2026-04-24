package com.firstticket.common.messaging.scheduler;

import com.firstticket.common.messaging.event.OutboxTransactionHandler;
import com.firstticket.common.messaging.outbox.Outbox;
import com.firstticket.common.messaging.outbox.OutboxRepository;
import com.firstticket.common.messaging.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxTransactionHandler outboxTransactionHandler;

    private static final int MAX_RETRY_COUNT = 3;

    @Scheduled(fixedDelayString = "${common.messaging.scheduler.delay:10000}")
    public void relay() {
        List<Outbox> targets = outboxRepository.findByStatusInAndRetryCountLessThan(
            List.of(OutboxStatus.PENDING, OutboxStatus.FAILED),
            MAX_RETRY_COUNT
        );

        if (targets.isEmpty()) return;

        log.info("[OutboxScheduler] 재전송 대상 {}건 처리 시작", targets.size());

        for (Outbox outbox : targets) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                    outbox.getEventType(),
                    outbox.getAggregateId().toString(),
                    outbox.getPayload()
                );
                record.headers().add("message_id", outbox.getId().toString().getBytes());

                kafkaTemplate.send(record)
                    .whenComplete((result, e) -> {
                        if (e == null) outboxTransactionHandler.success(outbox.getCorrelationId(), outbox.getEventType());
                        else outboxTransactionHandler.failure(outbox.getCorrelationId(), outbox.getEventType(), outbox.getPayload());
                    });
            } catch (Exception e) {
                log.error("[OutboxScheduler] 재전송 중 예외 발생. correlationId={}", outbox.getCorrelationId(), e);
                outboxTransactionHandler.failure(outbox.getCorrelationId(), outbox.getEventType(), outbox.getPayload());
            }
        }
    }
}
