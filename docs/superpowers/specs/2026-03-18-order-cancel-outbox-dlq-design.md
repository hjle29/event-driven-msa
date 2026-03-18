# Design Spec: Order Cancellation, Status Query, Outbox Pattern, Settlement DLQ

**Date:** 2026-03-18
**Branch:** feature/member-auth (to be branched from master)
**Services affected:** order-service, settlement-service, common
**GitHub issues:** #17, #18, #19, #20

---

## 1. Goals

| Feature | Description |
|---|---|
| `GET /order/{id}` | Return current order state by ID |
| `POST /order/{id}/cancel` | Cancel an order; emit `OrderCancelledEvent` via Outbox |
| Outbox pattern | Migrate `createOrder()` + new `cancelOrder()` to transactional outbox; remove direct `kafkaTemplate.send()` |
| Settlement DLQ | Retry failed Kafka consumption with backoff; park in DLT topic + `failed_event` DB record |
| Settlement cancellation | Void matching settlement record when `order-cancelled` is consumed |

---

## 2. Non-Goals

- Refund processing (future — triggered by `order-cancelled` downstream)
- Debezium CDC (outbox relay uses `@Scheduled` polling; CDC is a future upgrade path)
- Pagination on order queries (separate concern)
- Authorization/ownership check on cancel (future — requires JWT propagation, tracked separately)
- Outbox table archival/cleanup (future)

---

## 3. Architecture

### 3.1 order-service — Package Structure

```
order/
├── OrderController.java              ← add GET /order/{id}, POST /order/{id}/cancel
├── OrderService.java                 ← add getOrderById(), cancelOrder(); migrate createOrder() to outbox
├── OrderRepository.java              ← no change
├── OrderEntity.java                  ← add cancel() domain method
├── OrderStatus.java                  ← no change (CANCELLED already exists)
├── dto/OrderEntity.java              ← existing location (io.github.hjle.order.dto) — do NOT move
├── dto/response/OrderResponse.java   ← add canceledAt field
└── outbox/
    ├── OutboxStatus.java             ← NEW enum: PENDING, SENT, FAILED, DEAD
    ├── OutboxEntity.java             ← NEW
    ├── OutboxRepository.java         ← NEW
    └── OutboxRelayScheduler.java     ← NEW
```

> **Note:** `@EnableScheduling` must be added to `OrderApplication.java` for `@Scheduled` to activate.

### 3.2 settlement-service — Package Structure

```
settlement/
├── OrderEventConsumer.java           ← add retry/backoff wiring (existing)
├── OrderCancelledEventConsumer.java  ← NEW
├── SettlementStatus.java             ← add CANCELLED value
├── dto/SettlementEntity.java         ← add cancel() method
├── config/KafkaConsumerConfig.java   ← add OrderCancelledEvent factory + DLT recoverer for both topics
└── dlq/
    ├── FailedEventEntity.java        ← NEW
    ├── FailedEventRepository.java    ← NEW
    └── FailedEventHandler.java       ← NEW (@DltHandler for both order-created.DLT and order-cancelled.DLT)
```

### 3.3 common module

```
common/event/
├── OrderCreatedEvent.java       ← no change
└── OrderCancelledEvent.java     ← NEW
common/exception/
└── ErrorCode.java               ← add ORDER_CANCEL_FORBIDDEN (409, "O003")
                                    add SETTLEMENT_ALREADY_COMPLETED (409, "S001")
```

---

## 4. Data Models

### OutboxEntity (`order-service`)

| Field | Type | Notes |
|---|---|---|
| id | Long | PK, auto-increment |
| topic | String | e.g. `order-created`, `order-cancelled` |
| key | String | orderId as string (Kafka message key) |
| payload | String (TEXT) | JSON-serialized event (stored as raw JSON string, not re-serialized by relay) |
| status | OutboxStatus | `@Enumerated(STRING)`, `@Builder.Default` = `PENDING` |
| retryCount | Integer | `@Builder.Default` = `0`; incremented on each failed relay attempt |
| createdAt | LocalDateTime | `@PrePersist` |

**DB index required:** composite index on `(status, created_at)` for the scheduler query.

**OutboxStatus enum:**
```
PENDING  — not yet published
SENT     — successfully published to Kafka
FAILED   — last relay attempt failed; will retry if retryCount < maxRetries
DEAD     — exceeded maxRetries; will not be retried; requires manual intervention
```

**Max retries:** configurable via `outbox.relay.max-retries` (default: 5). When `retryCount >= maxRetries`, scheduler marks row as `DEAD` and logs `ERROR`.

### FailedEventEntity (`settlement-service`)

| Field | Type | Notes |
|---|---|---|
| id | Long | PK, auto-increment |
| topic | String | Source topic (e.g. `order-created`, `order-cancelled`) |
| payload | String (TEXT) | Raw message payload |
| errorMessage | String | Exception message |
| createdAt | LocalDateTime | `@PrePersist` |

### OrderCancelledEvent (`common`)

| Field | Type |
|---|---|
| orderId | Long |
| userId | String |
| canceledAt | LocalDateTime |

---

## 5. API Changes

### GET /order/{id}
- **Response:** `ApiResponse<OrderResponse>` (200)
- **Errors:** `ORDER_NOT_FOUND` (404)

### POST /order/{id}/cancel
- **Response:** `ApiResponse<OrderResponse>` (200) — includes `canceledAt` in response
- **Errors:**
  - `ORDER_NOT_FOUND` (404) — order does not exist
  - `ORDER_CANCEL_FORBIDDEN` (409) — status is `REFUNDED` or already `CANCELLED` (DELIVERED orders **can** be cancelled)

### OrderResponse additions
Add `canceledAt` (nullable `LocalDateTime`) to `OrderResponse` and its `from(OrderEntity)` factory method.

---

## 6. Data Flow

### Order Creation (migrated to Outbox)

```
POST /order
  → OrderService.createOrder()
      ├─ save OrderEntity (io.github.hjle.order.dto.OrderEntity)
      └─ save OutboxEntity(topic="order-created", key=orderId, payload=OrderCreatedEvent JSON)
          [single @Transactional — both writes atomic]

OutboxRelayScheduler (@Scheduled fixedDelay=5000ms, up to 5s latency by design)
  → findPendingOrRetryableWithLock()
      [SELECT ... WHERE status IN ('PENDING','FAILED') AND retry_count < max_retries
       FOR UPDATE SKIP LOCKED  ← prevents duplicate processing on multi-pod deployments]
  → for each row:
      kafkaTemplate.send(topic, key, payload)  ← send raw JSON string, no re-serialization
          .whenComplete:
              success → mark SENT
              failure → increment retryCount; if retryCount >= maxRetries → mark DEAD; else keep FAILED
```

### Order Cancellation

```
POST /order/{id}/cancel
  → OrderService.cancelOrder(id)
      ├─ findById → ORDER_NOT_FOUND if missing
      ├─ status in [REFUNDED, CANCELLED] → ORDER_CANCEL_FORBIDDEN  (DELIVERED is cancellable)
      ├─ order.cancel()  ← status=CANCELLED, canceledAt=now()  (note: single 'l', matches entity field)
      └─ save OutboxEntity(topic="order-cancelled", key=orderId, payload=OrderCancelledEvent JSON)
          [single @Transactional — both writes atomic]

OutboxRelayScheduler (same scheduler handles all topics)
  → kafkaTemplate.send("order-cancelled", ...)
```

### Settlement — order-created consumer (existing + DLQ)

```
topic: order-created
  → OrderEventConsumer.handleOrderCreated()
      ├─ success: idempotency check (existsByOrderId) → save SettlementEntity(PENDING)
      └─ failure: Spring retries 3x (backoff: 1s → 2s → 4s)
                    → DeadLetterPublishingRecoverer → order-created.DLT

topic: order-created.DLT
  → FailedEventHandler.handleDlt() [@DltHandler]
      ├─ save FailedEventEntity(topic="order-created", ...)
      └─ log.error
```

### Settlement — order-cancelled consumer (new)

```
topic: order-cancelled
  → OrderCancelledEventConsumer.handleOrderCancelled()
      ├─ find SettlementEntity by orderId
      ├─ if exists AND status not COMPLETED → settlement.cancel() → save
      ├─ if exists AND status == COMPLETED → log.error("Settlement already completed for orderId={}; cannot cancel — ALERT") + throw BusinessException(ErrorCode.SETTLEMENT_ALREADY_COMPLETED)
      └─ if not exists → log.warn("No settlement found for orderId={}; may arrive before order-created is processed")
          [idempotent — no exception thrown in either warn case]

      └─ failure: Spring retries 3x (backoff: 1s → 2s → 4s) — same DLT config as order-created
                    → FailedEventHandler → save FailedEventEntity(topic="order-cancelled", ...) + log.error
```

> **Out-of-order event note:** If `order-cancelled` arrives before `order-created` is processed (Kafka partition reordering or consumer lag), the settlement record will not exist yet and the cancellation is logged as a warning and skipped. This is an accepted known limitation at current scale. A future fix would use an event-sourcing or saga pattern.

### Order Status Query

```
GET /order/{id}
  → OrderService.getOrderById(id)
      ├─ findById → ORDER_NOT_FOUND if missing
      └─ return OrderResponse.from(order)  ← includes status, canceledAt
```

---

## 7. Domain Method Specifications

### `OrderEntity.cancel()`
```java
public void cancel() {
    this.status = OrderStatus.CANCELLED;
    this.canceledAt = LocalDateTime.now();  // field already exists in entity
}
```

### `SettlementEntity.cancel()`
```java
public void cancel() {
    this.status = SettlementStatus.CANCELLED;
    // no other field changes; SettlementEntity has no canceledAt field
}
```

---

## 8. Error Handling

| Scenario | Behaviour |
|---|---|
| Order not found | `BusinessException(ErrorCode.ORDER_NOT_FOUND)` → 404 |
| Cancel forbidden (REFUNDED / already CANCELLED) | `BusinessException(ErrorCode.ORDER_CANCEL_FORBIDDEN)` → 409 (DELIVERED is cancellable) |
| Outbox relay Kafka failure | Increment `retryCount`; retry on next poll; mark `DEAD` after max retries |
| Settlement `order-created` consumer failure | Retry 3x with 1s/2s/4s backoff; then DLT + `FailedEventEntity` |
| Settlement `order-cancelled` consumer failure | Same retry + DLT config as `order-created` consumer |
| `order-cancelled` arrives before `order-created` processed | `log.warn`, no exception — accepted known limitation |
| Cancelling already-completed settlement | `log.error` + `BusinessException(ErrorCode.SETTLEMENT_ALREADY_COMPLETED)` → triggers retry → DLT → `FailedEventEntity` |
| Duplicate `order-cancelled` event | Idempotent: second attempt hits the warn path, no state change |

---

## 9. Key Design Decisions

### 9.1 `SELECT FOR UPDATE SKIP LOCKED` on OutboxRepository
Multi-pod deployments require that only one pod processes each outbox row at a time. `SKIP LOCKED` ensures no blocking and no duplicate publishes. Without this, duplicate Kafka messages are guaranteed under load. (GitHub issue #17)

### 9.2 Outbox covers both `createOrder()` and `cancelOrder()`
Two publishing mechanisms (fire-and-forget + outbox) in the same service is a maintenance trap. Migrating `createOrder()` ensures a single, consistent, reliable publishing path. (GitHub issue #18)

### 9.3 Separate ConsumerFactory beans per event type
Spring Kafka typed deserializers are bound to a specific class. `OrderCreatedEvent` and `OrderCancelledEvent` need separate `ConsumerFactory` and `ConcurrentKafkaListenerContainerFactory` beans, named explicitly (`orderCreatedListenerContainerFactory`, `orderCancelledListenerContainerFactory`) to avoid autowiring conflicts. (GitHub issue #20)

### 9.4 Outbox relay uses a dedicated `KafkaTemplate<String, String>`
The existing `KafkaTemplate<String, Object>` in `KafkaProducerConfig` uses `JsonSerializer`. Using it to send the outbox `payload` (a raw JSON string) would double-encode it. The outbox relay must use a separate `KafkaTemplate<String, String>` bean backed by `StringSerializer` for both key and value.

### 9.5 Outbox polling latency
The 5-second polling interval introduces up to 5 seconds of end-to-end event latency. This is acceptable at current scale. Interval is configurable via `outbox.relay.fixed-delay-ms`.

### 9.5 DEAD outbox rows require manual replay
Rows marked `DEAD` (exceeded max retries) are not automatically replayed. This is intentional — automated infinite retry risks masking poison messages. Manual replay or a replay API is a future concern.

---

## 10. Testing

| Test | Type | What to verify |
|---|---|---|
| `OrderService.getOrderById()` | Unit | Returns order; throws ORDER_NOT_FOUND |
| `OrderService.cancelOrder()` | Unit | Happy path; DELIVERED/REFUNDED/CANCELLED blocked; not found |
| `OutboxRelayScheduler` | Integration | PENDING → SENT on success; FAILED + retryCount++ on error; DEAD after maxRetries |
| `OrderEventConsumer` retry | Integration | 3 retries then DLT; `FailedEventEntity` saved |
| `OrderCancelledEventConsumer` | Unit | Settlement cancelled; already-COMPLETED skipped; missing settlement logs warn |
| `OrderCancelledEventConsumer` DLQ | Integration | Failure → DLT → `FailedEventEntity` saved |

---

## 11. Related GitHub Issues

| Issue | Title |
|---|---|
| #17 | `OutboxRelayScheduler` duplicate publishes on multi-pod — SELECT FOR UPDATE SKIP LOCKED |
| #18 | Migrate `createOrder()` from fire-and-forget to Outbox pattern |
| #19 | Add `SettlementEntity.cancel()` and `SettlementStatus.CANCELLED` |
| #20 | Add `OrderCancelledEvent` ConsumerFactory to `KafkaConsumerConfig` |
