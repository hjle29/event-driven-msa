# Transactional Outbox Pattern

적용 서비스: `payment-service`
관련 이슈: #10

---

## 핵심 문제: 왜 이 패턴이 필요한가

결제 완료 후 Kafka에 직접 발행하면 다음 두 가지 상황이 발생합니다.

```
DB 저장 성공 → Kafka 발행 실패 → 이벤트 유실, 정산 누락
DB 저장 성공 → Kafka 발행 성공 → 앱 크래시 → DB 롤백? → 이벤트는 이미 발행됨
```

둘 다 데이터 불일치. 이를 방지하기 위해 **Outbox 패턴**을 사용합니다.

---

## 구현 구조 (4개 파일)

### 1단계 — 비즈니스 트랜잭션 (`PaymentService.java:47~73`)

```java
@Transactional
public PaymentResponse verifyApple(...) {
    Payment savedPayment = paymentRepository.save(payment);  // ① Payment DB 저장
    saveOutbox(savedPayment);                                 // ② Outbox DB 저장
    return PaymentResponse.from(savedPayment);
}
```

`@Transactional` 하나로 Payment 저장 + Outbox 저장을 **같은 트랜잭션**에 묶습니다.
Kafka는 이 시점에 전혀 건드리지 않습니다. DB 커밋이 실패하면 둘 다 롤백됩니다.

**`saveOutbox()` 메서드 (`PaymentService.java:112~131`)**

```java
PaymentCompletedEvent event = PaymentCompletedEvent.builder()...build();
String payload = objectMapper.writeValueAsString(event);   // JSON 직렬화
outboxRepository.save(OutboxEntity.builder()
        .topic(paymentCompletedTopic)
        .key(String.valueOf(payment.getId()))
        .payload(payload)   // 이벤트를 TEXT 컬럼에 저장
        .build());
```

Kafka 메시지를 보내는 게 아니라 **`payment_outbox` 테이블에 행을 삽입**합니다.

---

### 2단계 — Outbox 테이블 구조 (`OutboxEntity.java`)

```java
@Table(name = "payment_outbox",
       indexes = @Index(columnList = "status, createdAt"))  // 스케줄러 쿼리 최적화
```

| 필드 | 역할 |
|------|------|
| `topic` | 발행할 Kafka 토픽 |
| `key` | Kafka 메시지 키 (paymentId) |
| `payload` | JSON 직렬화된 이벤트 본문 |
| `status` | `PENDING` → `SENT` / `FAILED` → `DEAD` |
| `retryCount` | 실패 누적 횟수 |

`markFailedOrDead()` 메서드가 재시도 횟수 초과 시 `DEAD`로 격리합니다.

---

### 3단계 — 비동기 발행자 (`OutboxRelayScheduler.java`)

```java
@Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:5000}")
@Transactional
public void relay() {
    List<OutboxEntity> rows = outboxRepository.findRetryableWithLock(maxRetries);
    for (OutboxEntity outbox : rows) {
        stringKafkaTemplate.send(...).get();   // 동기 발행 (결과 확인)
        outbox.markSent();
    }
}
```

5초마다 `PENDING`/`FAILED` 상태 행을 꺼내 Kafka에 발행합니다.
`.get()`으로 **동기 대기**하기 때문에 발행 성공 여부를 즉시 확인하고,
실패하면 `markFailedOrDead()`를 호출합니다.

---

### 4단계 — 비관적 락 (`OutboxRepository.java`)

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
@Query("SELECT o FROM OutboxEntity o WHERE o.status IN ('PENDING', 'FAILED') ...")
List<OutboxEntity> findRetryableWithLock(int maxRetries);
```

`PESSIMISTIC_WRITE` + `lock.timeout = 0` (즉시 실패) 조합.
스케줄러가 여러 인스턴스에서 동시 실행될 때 **같은 행을 중복 발행하지 않도록**
행 레벨 락으로 막습니다.

---

## 방지하는 예외 케이스

| 상황 | 방지 방법 |
|------|-----------|
| 결제 저장 후 Kafka 발행 실패 | Outbox가 `PENDING` 유지 → 스케줄러가 재발행 |
| 앱 크래시 (결제 저장 직후) | DB 트랜잭션 롤백 → Outbox도 같이 롤백 → 이벤트 없음 |
| 스케줄러 다중 인스턴스 동시 실행 | 비관적 락으로 중복 처리 방지 |
| Kafka 브로커 일시 장애 | `FAILED` 상태로 최대 5회 재시도 |
| 반복 실패 (브로커 완전 장애) | `DEAD` 상태 격리 + 알람 로그 → 수동 개입 |

---

## 알려진 한계

`OutboxRelayScheduler`는 `@Scheduled`만 사용하고 있어 **분산 스케줄러 락(`ShedLock`)이 적용되지 않았습니다.**
`build.gradle.kts`에 ShedLock 의존성은 있으나 `@SchedulerLock`이 누락된 상태입니다.
다중 인스턴스 배포 시 비관적 락으로 중복 처리는 막지만, 불필요한 경합이 발생할 수 있습니다.
