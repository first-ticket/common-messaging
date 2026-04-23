package com.firstticket.common.messaging.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {

    List<Outbox> findByStatus(OutboxStatus status);

    Optional<Outbox> findByCorrelationIdAndEventType(String correlationId, String eventType);

    boolean existsByCorrelationIdAndEventType(String correlationId, String eventType);
}
