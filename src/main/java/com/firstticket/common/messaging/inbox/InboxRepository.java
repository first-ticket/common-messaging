package com.firstticket.common.messaging.inbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InboxRepository extends JpaRepository<Inbox, UUID> {
}
