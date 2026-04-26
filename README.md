# com.first-ticket.common-messaging

Kafka 기반 메시지 발행/소비를 위한 공통 메시징 모듈입니다.  
Outbox/Inbox 패턴으로 메시지 유실과 중복 처리를 방지합니다.

---

## 📝 버전

| 버전 | 변경 내용 |
|------|-----------|
| `0.0.1-SNAPSHOT` | • Outbox/Inbox 패턴 구현<br>• 멱등성 처리 (`@IdempotentConsumer`)<br>• 재시도 스케줄러 (`OutboxRelayScheduler`)<br>• 데이터 정리 스케줄러 (`MessagingCleanupScheduler`) |

---

## 📦 의존성 추가

> 배포 방법 및 의존성 추가는 [common README](https://github.com/first-ticket/common)를 참고해주세요.

```groovy
implementation 'com.first-ticket:common-messaging:0.0.1-SNAPSHOT'
```

---

## 🗂️ 패키지 구조

```
com.firstticket.common.messaging
├── CommonMessagingAutoConfiguration.java  ← 자동 빈 등록
├── event
│   ├── Events.java                        ← 이벤트 발행 정적 유틸
│   ├── OutboxEvent.java                   ← 발행 이벤트 record
│   ├── OutboxEventListener.java           ← Outbox 저장 + Kafka 발행
│   └── OutboxTransactionHandler.java      ← 발행 성공/실패 상태 업데이트
├── outbox
│   ├── Outbox.java                        ← Outbox JPA 엔티티
│   ├── OutboxRepository.java              ← Outbox Repository
│   └── OutboxStatus.java                  ← PENDING / PUBLISHED / FAILED
├── inbox
│   ├── Inbox.java                         ← Inbox JPA 엔티티
│   └── InboxRepository.java               ← Inbox Repository
├── annotation
│   ├── IdempotentConsumer.java            ← 멱등성 어노테이션
│   └── IdempotentAspect.java              ← AOP 중복 수신 처리
└── scheduler
    ├── OutboxRelayScheduler.java          ← PENDING/FAILED 재시도 (10초)
    └── MessagingCleanupScheduler.java     ← 오래된 데이터 삭제 (매일)
```

---

## ⚙️ 설정

`application.yml`에 Kafka 설정을 추가합니다.

```yaml
spring:
    kafka:
        # Kafka 브로커 주소 (로컬: localhost:29092, 도커: kafka:9092)
        bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}

        producer:
            key-serializer: org.apache.kafka.common.serialization.StringSerializer
            value-serializer: org.apache.kafka.common.serialization.StringSerializer
            properties:
                # 프로듀서 레벨 중복 발행 방지
                enable.idempotence: true
                # 전송 총 제한 시간 2분 (이 시간 내에서 재시도 반복)
                delivery.timeout.ms: 120000
                # delivery.timeout.ms 내에서 사실상 무한 재시도
                retries: 2147483647
                # 순서 보장 + 성능 최적화 (idempotence 활성화 시 최대 5)
                max.in.flight.requests.per.connection: 5
                # 브로커 연결 대기 최대 시간 10초
                max.block.ms: 10000

        consumer:
            # 기본 컨슈머 그룹 ID (서비스명 사용)
            group-id: ${spring.application.name}
            # 새로운 그룹이 시작할 때 가장 처음 오프셋부터 읽기
            auto-offset-reset: earliest
            # 수동 커밋 사용 (ack-mode: manual_immediate)
            enable-auto-commit: false
            key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
            value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
            properties:
                # 컨슈머가 살아있는지 확인하는 세션 타임아웃 30초
                session.timeout.ms: 30000
                # 메시지 처리가 이 시간을 초과하면 그룹에서 제외 (5분)
                max.poll.interval.ms: 300000

        listener:
            # acknowledge() 호출 즉시 오프셋 커밋
            ack-mode: manual_immediate

logging:
    level:
        org.apache.kafka.clients.NetworkClient: ERROR
```

> ⚠️ `.env`에 `KAFKA_BOOTSTRAP_SERVERS`를 반드시 설정해야 합니다.
> ```
> # 로컬 개발 환경
> KAFKA_BOOTSTRAP_SERVERS=localhost:29092
> 
> # 도커 환경
> KAFKA_BOOTSTRAP_SERVERS=kafka:9092
> ```

`group-id`는 yml에서 설정하거나 `@KafkaListener`에서 직접 지정할 수 있습니다.

---

## 📤 이벤트 발행

`@Transactional` 메서드 안에서 `Events.publish()`를 호출합니다.  
도메인 트랜잭션이 커밋되기 전에 Outbox에 저장되고, 커밋 후 Kafka로 발행됩니다.

`correlationId`는 동일한 처리 흐름에서 발행되는 이벤트를 식별하는 상관 ID입니다.  
보통 요청 단위로 생성한 UUID 문자열을 사용합니다. (예: `UUID.randomUUID().toString()`)  
Zipkin 등 분산 트레이싱 도구와 연동 시 트레이스 ID를 그대로 사용할 수 있습니다.

```java
@Transactional
public void createSample(SampleRequest request) {
    sampleRepository.save(sample);

    Events.publish(
        UUID.randomUUID().toString(), // correlationId: 요청 단위 상관 ID (추후 Trace-Id로 교체 예정)
        "SAMPLE",                     // aggregateType
        sample.getId(),               // aggregateId (UUID)
        "sample.created",             // eventType (Kafka 토픽명)
        sampleCreatedPayload          // payload (Object → JSON 직렬화)
    );
}
```

---

## 📥 이벤트 수신

`@IdempotentConsumer`를 붙이면 Inbox 기반 중복 수신이 자동으로 처리됩니다.  
파라미터는 반드시 `ConsumerRecord<String, String>`을 포함해야 합니다.

```java
@IdempotentConsumer
@KafkaListener(topics = "sample.created")
public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
    SampleCreatedPayload payload = JsonUtil.fromJson(record.value(), SampleCreatedPayload.class);
    // 비즈니스 로직
    ack.acknowledge();
}
```

---

## ⚠️ 에러 처리 흐름

```
이벤트 발행
    │
    ├─ Outbox 저장 실패  → 트랜잭션 롤백 (500 반환)
    │
    └─ Kafka 발행 실패  → FAILED 상태로 변경
           │
           └─ 스케줄러 재시도 (10초마다)
                  │
                  └─ MAX_RETRY_COUNT(3) 초과 → DLT 토픽으로 이동
                                               {eventType}.DLT
```

---

## ⚙️ 아웃박스 스케줄러 설정

아웃박스 스케줄러의 활성화 여부와 실행 주기를 외부 설정으로 제어할 수 있습니다.

| 설정 키 | 기본값     | 설명 |
|---------|---------|------|
| `common.messaging.scheduler.enabled` | `true`  | 스케줄러 활성화 여부 |
| `common.messaging.scheduler.delay` | `10000` | 스케줄러 실행 주기 (ms) |

```yaml
# 로컬 개발환경에서 스케줄러 비활성화 예시
common:
  messaging:
    scheduler:
      enabled: false

# 스케줄러 활성화는 하고 실행 주기만 설정하는 경우
common:
  messaging:
    scheduler:
      enabled: true
      delay: 60000 # 1분

```

> - `enabled: false` 시 스케줄러 빈이 등록되지 않아 SELECT 쿼리가 실행되지 않습니다.
> - `enabled: true` 시 `delay` 값(ms)으로 실행 주기를 조절할 수 있습니다.

---

## 🗑️ 데이터 정리

오래된 Outbox/Inbox 데이터는 스케줄러가 자동으로 삭제합니다.

| 대상 | 조건 | 실행 시각 |
|------|------|---------|
| Outbox | PUBLISHED 상태 + 7일 경과 | 매일 새벽 3시 |
| Inbox | 처리 완료 + 7일 경과 | 매일 새벽 4시 |
