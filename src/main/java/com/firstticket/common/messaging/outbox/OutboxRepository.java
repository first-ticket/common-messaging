package com.firstticket.common.messaging.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {

    Optional<Outbox> findByCorrelationIdAndEventType(String correlationId, String eventType);

    boolean existsByCorrelationIdAndEventType(String correlationId, String eventType);

    List<Outbox> findByStatusInAndRetryCountLessThan(List<OutboxStatus> statuses, int retryCount);
}
