package com.firstticket.common.messaging.scheduler;

import com.firstticket.common.messaging.inbox.QInbox;
import com.firstticket.common.messaging.outbox.OutboxStatus;
import com.firstticket.common.messaging.outbox.QOutbox;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@RequiredArgsConstructor
public class InboxCleanupScheduler {

    private final JPAQueryFactory queryFactory;

    @Transactional
    @Scheduled(cron = "0 0 4 * * *")
    public void cleanupInbox() {
        long deleted = queryFactory
            .delete(QInbox.inbox)
            .where(QInbox.inbox.processedAt.before(LocalDateTime.now().minusWeeks(1L)))
            .execute();
        log.info("[Inbox] {}건 삭제 완료", deleted);
    }
}
