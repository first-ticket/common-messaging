package com.firstticket.common.messaging.event;

import com.firstticket.common.messaging.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public class OutboxTransactionHandler {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final int MAX_RETRY_COUNT = 3;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(String correlationId, String eventType) {
        outboxRepository.findByCorrelationIdAndEventType(correlationId, eventType)
            .ifPresent(outbox -> {
                outbox.markPublished();
                log.info("[Outbox] 발행 완료. eventType={}, correlationId={}", eventType, correlationId);
            });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(String correlationId, String eventType, String payload) {
        outboxRepository.findByCorrelationIdAndEventType(correlationId, eventType)
            .ifPresent(outbox -> {
                outbox.markFailed();
                outboxRepository.saveAndFlush(outbox);

                if (outbox.getRetryCount() >= MAX_RETRY_COUNT) {
                    log.error("[Outbox] 최대 재시도 초과. DLT로 이동. eventType={}, correlationId={}", eventType, correlationId);
                    sendToDlt(correlationId, eventType, payload);
                } else {
                    log.warn("[Outbox] 발행 실패. ({}/{}). eventType={}, correlationId={}", outbox.getRetryCount(), MAX_RETRY_COUNT, eventType, correlationId);
                }
            });
    }

    private void sendToDlt(String correlationId, String eventType, String payload) {
        String dltTopic = eventType + ".DLT";
        try {
            kafkaTemplate.send(dltTopic, correlationId, payload)
                .whenComplete((result, e) -> {
                    if (e != null) log.error("[Outbox] DLT 전송 실패. eventType={}, correlationId={}", eventType, correlationId, e);
                    else log.info("[Outbox] DLT 전송 완료. eventType={}, correlationId={}", eventType, correlationId);
                });
        } catch (Exception e) {
            log.error("[Outbox] DLT 전송 중 예외 발생. eventType={}, correlationId={}", eventType, correlationId, e);
        }
    }
}
