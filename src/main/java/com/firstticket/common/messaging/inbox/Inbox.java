package com.firstticket.common.messaging.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "P_INBOX")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Inbox {

    @Id
    @Column(name = "message_id")
    private UUID id;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public static Inbox create(UUID id) {
        return new Inbox(id, LocalDateTime.now());
    }
}
