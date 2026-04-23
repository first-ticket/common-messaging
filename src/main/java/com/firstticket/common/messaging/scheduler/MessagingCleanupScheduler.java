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
public class MessagingCleanupScheduler {

    private final JPAQueryFactory queryFactory;

    @Transactional
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupOutbox() {
        long deleted = queryFactory
            .delete(QOutbox.outbox)
            .where(QOutbox.outbox.status.eq(OutboxStatus.PUBLISHED)
                .and(QOutbox.outbox.publishedAt.before(LocalDateTime.now().minusWeeks(1L))))
            .execute();
        log.info("[Outbox] {}건 삭제 완료", deleted);
    }

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
