package com.firstticket.common.messaging.annotation;

import com.firstticket.common.messaging.inbox.Inbox;
import com.firstticket.common.messaging.inbox.InboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Aspect
@RequiredArgsConstructor
public class IdempotentAspect {

    private final InboxRepository inboxRepository;

    @Around("@annotation(IdempotentConsumer)")
    @Transactional(rollbackFor = Exception.class)
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID messageId = extractMessageId(joinPoint.getArgs());

        if (messageId == null) {
            log.warn("[Inbox] messageId를 찾을 수 없습니다. 멱등성 체크 없이 실행합니다.");
            return joinPoint.proceed();
        }

        try {
            inboxRepository.saveAndFlush(Inbox.create(messageId));
        } catch (DataIntegrityViolationException e) {
            log.warn("[Inbox] 중복 메시지 무시. messageId={}", messageId);
            return null;
        }

        try {
            Object result = joinPoint.proceed();
            log.debug("[Inbox] 메시지 처리 완료. messageId={}", messageId);
            return result;
        } catch (Throwable e) {
            log.error("[Inbox] 메시지 처리 실패. messageId={}", messageId, e);
            throw e;
        }
    }

    private UUID extractMessageId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof ConsumerRecord<?, ?> record) {
                Header header = record.headers().lastHeader("message_id");
                if (header == null) return null;
                try {
                    return UUID.fromString(new String(header.value()));
                } catch (IllegalArgumentException e) {
                    log.warn("[Inbox] message_id 헤더가 UUID 형식이 아닙니다. value={}", new String(header.value()));
                    return null;
                }
            }
        }
        return null;
    }
}
