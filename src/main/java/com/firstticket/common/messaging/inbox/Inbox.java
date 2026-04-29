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
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Inbox는 append-only 엔티티입니다.
 * 한 번 저장된 후에는 절대 재저장되지 않아야 합니다.
 * 재저장 경로가 생길 경우, isNew()를 @PostLoad와 함께 실제 상태를 반영하도록 변경해야 합니다.
 */
@Entity
@Table(name = "P_INBOX")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Inbox implements Persistable<UUID> {

    @Id
    @Column(name = "message_id")
    private UUID id;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public static Inbox create(UUID id) {
        return new Inbox(id, LocalDateTime.now());
    }

    @Override
    public boolean isNew() {
        return true;
    }
}
