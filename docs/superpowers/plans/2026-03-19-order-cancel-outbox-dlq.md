# Order Cancellation, Outbox Pattern & Settlement DLQ Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement order cancellation API, GET /order/{id}, transactional outbox pattern for reliable Kafka publishing, and settlement DLQ with cancellation voiding.

**Architecture:** Order-service writes events to an `outbox` DB table inside the same transaction as business data; a `@Scheduled` relay publishes them to Kafka every 5s using a `KafkaTemplate<String, String>`. Settlement-service gains retry/backoff with DLT parking, a `failed_event` DB table, and a second consumer that voids settlements when orders are cancelled.

**Tech Stack:** Spring Boot 3.3.4, Spring Kafka, Spring Data JPA, PostgreSQL, JUnit 5 + Mockito (`spring-boot-starter-test`)

---

## File Map

### common module
| Action | File |
|---|---|
| Modify | `common/src/main/java/com/hjle/common/event/OrderCancelledEvent.java` ← NEW |
| Modify | `common/src/main/java/com/hjle/common/exception/ErrorCode.java` |

### order-service
| Action | File |
|---|---|
| Modify | `order-service/src/main/java/io/github/hjle/order/dto/OrderEntity.java` |
| Modify | `order-service/src/main/java/io/github/hjle/order/dto/response/OrderResponse.java` |
| Modify | `order-service/src/main/java/io/github/hjle/order/OrderService.java` |
| Modify | `order-service/src/main/java/io/github/hjle/order/OrderController.java` |
| Modify | `order-service/src/main/java/io/github/hjle/order/OrderApplication.java` |
| Modify | `order-service/src/main/java/io/github/hjle/order/config/KafkaProducerConfig.java` |
| Create | `order-service/src/main/java/io/github/hjle/order/outbox/OutboxStatus.java` |
| Create | `order-service/src/main/java/io/github/hjle/order/outbox/OutboxEntity.java` |
| Create | `order-service/src/main/java/io/github/hjle/order/outbox/OutboxRepository.java` |
| Create | `order-service/src/main/java/io/github/hjle/order/outbox/OutboxRelayScheduler.java` |
| Create | `order-service/src/test/java/io/github/hjle/order/OrderServiceTest.java` |
| Create | `order-service/src/test/java/io/github/hjle/order/outbox/OutboxRelaySchedulerTest.java` |

### settlement-service
| Action | File |
|---|---|
| Modify | `settlement-service/src/main/java/io/github/hjle/settlement/SettlementStatus.java` |
| Modify | `settlement-service/src/main/java/io/github/hjle/settlement/dto/SettlementEntity.java` |
| Modify | `settlement-service/src/main/java/io/github/hjle/settlement/config/KafkaConsumerConfig.java` |
| Create | `settlement-service/src/main/java/io/github/hjle/settlement/OrderCancelledEventConsumer.java` |
| Create | `settlement-service/src/main/java/io/github/hjle/settlement/dlq/FailedEventEntity.java` |
| Create | `settlement-service/src/main/java/io/github/hjle/settlement/dlq/FailedEventRepository.java` |
| Create | `settlement-service/src/main/java/io/github/hjle/settlement/dlq/FailedEventHandler.java` |
| Create | `settlement-service/src/test/java/io/github/hjle/settlement/OrderCancelledEventConsumerTest.java` |

---

## Task 1: common — Add OrderCancelledEvent and new ErrorCodes

**Files:**
- Create: `common/src/main/java/com/hjle/common/event/OrderCancelledEvent.java`
- Modify: `common/src/main/java/com/hjle/common/exception/ErrorCode.java`

- [ ] **Step 1: Create OrderCancelledEvent**

```java
// common/src/main/java/com/hjle/common/event/OrderCancelledEvent.java
package com.hjle.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelledEvent {
    private Long orderId;
    private String userId;
    private LocalDateTime canceledAt;
}
```

- [ ] **Step 2: Add new ErrorCodes**

In `ErrorCode.java`, add under the `// Order` section:
```java
ORDER_CANCEL_FORBIDDEN(409, "O003", "Order cannot be cancelled"),

// Settlement
SETTLEMENT_ALREADY_COMPLETED(409, "S001", "Settlement is already completed and cannot be cancelled");
```

- [ ] **Step 3: Commit**

```bash
git add common/src/main/java/com/hjle/common/event/OrderCancelledEvent.java
git add common/src/main/java/com/hjle/common/exception/ErrorCode.java
git commit -m "feat(common): add OrderCancelledEvent and ORDER_CANCEL_FORBIDDEN, SETTLEMENT_ALREADY_COMPLETED error codes"
```

---

## Task 2: order-service — Add cancel() to OrderEntity and canceledAt to OrderResponse

**Files:**
- Modify: `order-service/src/main/java/io/github/hjle/order/dto/OrderEntity.java`
- Modify: `order-service/src/main/java/io/github/hjle/order/dto/response/OrderResponse.java`

- [ ] **Step 1: Add cancel() domain method to OrderEntity**

Add this method at the bottom of `OrderEntity.java` (the `canceledAt` field already exists at line 54):
```java
public void cancel() {
    this.status = OrderStatus.CANCELLED;
    this.canceledAt = LocalDateTime.now();
}
```

- [ ] **Step 2: Add canceledAt to OrderResponse**

Add field after `createdAt`:
```java
private LocalDateTime canceledAt;
```

Update the `from()` factory method — add after `.createdAt(entity.getCreatedAt())`:
```java
.canceledAt(entity.getCanceledAt())
```

- [ ] **Step 3: Commit**

```bash
git add order-service/src/main/java/io/github/hjle/order/dto/OrderEntity.java
git add order-service/src/main/java/io/github/hjle/order/dto/response/OrderResponse.java
git commit -m "feat(order): add cancel() domain method to OrderEntity and canceledAt to OrderResponse"
```

---

## Task 3: order-service — Outbox infrastructure (entity, repository, status enum)

**Files:**
- Create: `order-service/src/main/java/io/github/hjle/order/outbox/OutboxStatus.java`
- Create: `order-service/src/main/java/io/github/hjle/order/outbox/OutboxEntity.java`
- Create: `order-service/src/main/java/io/github/hjle/order/outbox/OutboxRepository.java`

- [ ] **Step 1: Create OutboxStatus enum**

```java
// order-service/src/main/java/io/github/hjle/order/outbox/OutboxStatus.java
package io.github.hjle.order.outbox;

public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED,
    DEAD
}
```

- [ ] **Step 2: Create OutboxEntity**

```java
// order-service/src/main/java/io/github/hjle/order/outbox/OutboxEntity.java
package io.github.hjle.order.outbox;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "outbox",
    indexes = @Index(name = "idx_outbox_status_created", columnList = "status, created_at")
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class OutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String key;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void markSent() {
        this.status = OutboxStatus.SENT;
    }

    public void markFailedOrDead(int maxRetries) {
        this.retryCount++;
        if (this.retryCount >= maxRetries) {
            this.status = OutboxStatus.DEAD;
        } else {
            this.status = OutboxStatus.FAILED;
        }
    }
}
```

- [ ] **Step 3: Create OutboxRepository**

```java
// order-service/src/main/java/io/github/hjle/order/outbox/OutboxRepository.java
package io.github.hjle.order.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("SELECT o FROM OutboxEntity o WHERE o.status IN ('PENDING', 'FAILED') AND o.retryCount < :maxRetries ORDER BY o.createdAt ASC")
    List<OutboxEntity> findRetryableWithLock(int maxRetries);
}
```

- [ ] **Step 4: Add KafkaTemplate<String, String> bean to KafkaProducerConfig**

Read the existing `KafkaProducerConfig.java` first, then add this bean. The existing `KafkaTemplate<String, Object>` uses `JsonSerializer` and must be kept for backward compatibility. The outbox relay needs a separate `StringSerializer`-backed template:

```java
@Bean
public KafkaTemplate<String, String> stringKafkaTemplate() {
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
}
```

Imports needed: `org.apache.kafka.common.serialization.StringSerializer`, `org.springframework.kafka.core.DefaultKafkaProducerFactory`.

- [ ] **Step 5: Commit**

```bash
git add order-service/src/main/java/io/github/hjle/order/outbox/
git add order-service/src/main/java/io/github/hjle/order/config/KafkaProducerConfig.java
git commit -m "feat(order): add outbox infrastructure — OutboxEntity, OutboxRepository, OutboxStatus, StringKafkaTemplate"
```

---

## Task 4: order-service — OutboxRelayScheduler

**Files:**
- Create: `order-service/src/main/java/io/github/hjle/order/outbox/OutboxRelayScheduler.java`
- Modify: `order-service/src/main/java/io/github/hjle/order/OrderApplication.java`
- Create: `order-service/src/test/java/io/github/hjle/order/outbox/OutboxRelaySchedulerTest.java`

- [ ] **Step 1: Write the failing test**

```java
// order-service/src/test/java/io/github/hjle/order/outbox/OutboxRelaySchedulerTest.java
package io.github.hjle.order.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

    @Mock OutboxRepository outboxRepository;
    @Mock KafkaTemplate<String, String> stringKafkaTemplate;

    OutboxRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OutboxRelayScheduler(outboxRepository, stringKafkaTemplate, 5);
    }

    @Test
    void relay_marks_sent_on_success() {
        OutboxEntity outbox = OutboxEntity.builder()
                .topic("order-created").key("1").payload("{\"orderId\":1}").build();
        when(outboxRepository.findRetryableWithLock(5)).thenReturn(List.of(outbox));

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(stringKafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        scheduler.relay();

        verify(outboxRepository).save(outbox);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    void relay_increments_retry_count_on_failure() {
        OutboxEntity outbox = OutboxEntity.builder()
                .topic("order-created").key("1").payload("{\"orderId\":1}").build();
        when(outboxRepository.findRetryableWithLock(5)).thenReturn(List.of(outbox));

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka down"));
        when(stringKafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        scheduler.relay();

        verify(outboxRepository).save(outbox);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
    }

    @Test
    void relay_marks_dead_after_max_retries() {
        OutboxEntity outbox = OutboxEntity.builder()
                .topic("order-created").key("1").payload("{\"orderId\":1}")
                .retryCount(4).status(OutboxStatus.FAILED).build();
        when(outboxRepository.findRetryableWithLock(5)).thenReturn(List.of(outbox));

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka down"));
        when(stringKafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        scheduler.relay();

        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.DEAD);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :order-service:test --tests "io.github.hjle.order.outbox.OutboxRelaySchedulerTest" 2>&1 | tail -20
```
Expected: FAIL — `OutboxRelayScheduler` does not exist yet.

- [ ] **Step 3: Implement OutboxRelayScheduler**

```java
// order-service/src/main/java/io/github/hjle/order/outbox/OutboxRelayScheduler.java
package io.github.hjle.order.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class OutboxRelayScheduler {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> stringKafkaTemplate;
    private final int maxRetries;

    // Explicit constructor required: @Value on field conflicts with @RequiredArgsConstructor
    public OutboxRelayScheduler(OutboxRepository outboxRepository,
                                KafkaTemplate<String, String> stringKafkaTemplate,
                                @Value("${outbox.relay.max-retries:5}") int maxRetries) {
        this.outboxRepository = outboxRepository;
        this.stringKafkaTemplate = stringKafkaTemplate;
        this.maxRetries = maxRetries;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:5000}")
    public void relay() {
        List<OutboxEntity> pending = outboxRepository.findRetryableWithLock(maxRetries);
        for (OutboxEntity outbox : pending) {
            stringKafkaTemplate.send(outbox.getTopic(), outbox.getKey(), outbox.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("[OutboxRelay] Failed to publish. topic={}, key={}, retryCount={}",
                                    outbox.getTopic(), outbox.getKey(), outbox.getRetryCount(), ex);
                            outbox.markFailedOrDead(maxRetries);
                            if (outbox.getStatus() == OutboxStatus.DEAD) {
                                log.error("[OutboxRelay] DEAD — manual intervention required. topic={}, key={}",
                                        outbox.getTopic(), outbox.getKey());
                            }
                        } else {
                            outbox.markSent();
                        }
                        outboxRepository.save(outbox);
                    });
        }
    }
}
```

- [ ] **Step 4: Add @EnableScheduling to OrderApplication**

```java
@EnableScheduling  // add this annotation
@SpringBootApplication
public class OrderApplication { ... }
```

Import: `org.springframework.scheduling.annotation.EnableScheduling`

- [ ] **Step 5: Run test to verify it passes**

```bash
./gradlew :order-service:test --tests "io.github.hjle.order.outbox.OutboxRelaySchedulerTest" 2>&1 | tail -20
```
Expected: 3 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add order-service/src/main/java/io/github/hjle/order/outbox/OutboxRelayScheduler.java
git add order-service/src/main/java/io/github/hjle/order/OrderApplication.java
git add order-service/src/test/java/io/github/hjle/order/outbox/OutboxRelaySchedulerTest.java
git commit -m "feat(order): add OutboxRelayScheduler with retry/dead-letter logic"
```

---

## Task 5: order-service — GET /order/{id} and POST /order/{id}/cancel + migrate createOrder

**Files:**
- Modify: `order-service/src/main/java/io/github/hjle/order/OrderService.java`
- Modify: `order-service/src/main/java/io/github/hjle/order/OrderController.java`
- Create: `order-service/src/test/java/io/github/hjle/order/OrderServiceTest.java`

Note: `ObjectMapper` is needed to serialize events to JSON for the outbox payload. Add it as a Spring bean injection (Spring Boot auto-configures `ObjectMapper`).

- [ ] **Step 1: Write the failing tests**

```java
// order-service/src/test/java/io/github/hjle/order/OrderServiceTest.java
package io.github.hjle.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjle.common.exception.BusinessException;
import com.hjle.common.exception.ErrorCode;
import io.github.hjle.order.dto.OrderEntity;
import io.github.hjle.order.dto.request.OrderRequest;
import io.github.hjle.order.outbox.OutboxEntity;
import io.github.hjle.order.outbox.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock OutboxRepository outboxRepository;
    @Mock MemberServiceClient memberServiceClient;

    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, outboxRepository, memberServiceClient, objectMapper);
    }

    @Test
    void getOrderById_returns_order() {
        OrderEntity order = OrderEntity.builder().userId("u1").productId("p1")
                .productName("item").quantity(1).unitPrice(1000).totalPrice(1000).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderEntity result = orderService.getOrderById(1L);

        assertThat(result).isEqualTo(order);
    }

    @Test
    void getOrderById_throws_when_not_found() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void cancelOrder_sets_status_cancelled_and_saves_outbox() {
        OrderEntity order = OrderEntity.builder().userId("u1").productId("p1")
                .productName("item").quantity(1).unitPrice(1000).totalPrice(1000).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        orderService.cancelOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(outboxRepository).save(any(OutboxEntity.class));
    }

    @Test
    void cancelOrder_throws_when_already_cancelled() {
        OrderEntity order = OrderEntity.builder().userId("u1").productId("p1")
                .productName("item").quantity(1).unitPrice(1000).totalPrice(1000).build();
        order.cancel();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void cancelOrder_throws_when_refunded() {
        // Use reflection to set REFUNDED status for this test
        OrderEntity order = spy(OrderEntity.builder().userId("u1").productId("p1")
                .productName("item").quantity(1).unitPrice(1000).totalPrice(1000).build());
        doReturn(OrderStatus.REFUNDED).when(order).getStatus();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(BusinessException.class);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :order-service:test --tests "io.github.hjle.order.OrderServiceTest" 2>&1 | tail -20
```
Expected: FAIL — methods don't exist yet.

- [ ] **Step 3: Refactor OrderService**

Replace `OrderService.java` with the following (keep `getOrdersWithMember` unchanged):

```java
package io.github.hjle.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjle.common.event.OrderCancelledEvent;
import com.hjle.common.event.OrderCreatedEvent;
import com.hjle.common.exception.BusinessException;
import com.hjle.common.exception.ErrorCode;
import io.github.hjle.order.dto.MemberResponse;
import io.github.hjle.order.dto.OrderEntity;
import io.github.hjle.order.dto.request.OrderRequest;
import io.github.hjle.order.outbox.OutboxEntity;
import io.github.hjle.order.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Set<OrderStatus> CANCEL_FORBIDDEN = Set.of(
            OrderStatus.REFUNDED, OrderStatus.CANCELLED);

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final MemberServiceClient memberServiceClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderEntity createOrder(OrderRequest request) {
        MemberResponse member = memberServiceClient.getMemberByUserId(request.getUserId());

        int totalPrice = request.getQuantity() * request.getUnitPrice();

        OrderEntity order = OrderEntity.builder()
                .userId(request.getUserId())
                .email(member.getEmail())
                .productId(request.getProductId())
                .productName(request.getProductName())
                .quantity(request.getQuantity())
                .unitPrice(request.getUnitPrice())
                .totalPrice(totalPrice)
                .build();

        OrderEntity savedOrder = orderRepository.save(order);

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(savedOrder.getId())
                .userId(savedOrder.getUserId())
                .email(savedOrder.getEmail())
                .productId(savedOrder.getProductId())
                .productName(savedOrder.getProductName())
                .quantity(savedOrder.getQuantity())
                .totalPrice(savedOrder.getTotalPrice())
                .createdAt(savedOrder.getCreatedAt())
                .build();

        outboxRepository.save(OutboxEntity.builder()
                .topic("order-created")
                .key(String.valueOf(savedOrder.getId()))
                .payload(toJson(event))
                .build());

        log.info("[OrderService] Order created and queued in outbox. orderId={}", savedOrder.getId());
        return savedOrder;
    }

    @Transactional(readOnly = true)
    public OrderEntity getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    @Transactional
    public OrderEntity cancelOrder(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (CANCEL_FORBIDDEN.contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_FORBIDDEN);
        }

        order.cancel();
        orderRepository.save(order);

        OrderCancelledEvent event = OrderCancelledEvent.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .canceledAt(order.getCanceledAt())
                .build();

        outboxRepository.save(OutboxEntity.builder()
                .topic("order-cancelled")
                .key(String.valueOf(order.getId()))
                .payload(toJson(event))
                .build());

        log.info("[OrderService] Order cancelled and queued in outbox. orderId={}", orderId);
        return order;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOrdersWithMember(String userId) {
        MemberResponse member = memberServiceClient.getMemberByUserId(userId);
        List<OrderEntity> orders = orderRepository.findByUserId(userId);
        if (CollectionUtils.isEmpty(orders)) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("orders", orders);
        result.put("member", member);
        return result;
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
```

- [ ] **Step 4: Add new endpoints to OrderController**

Add these two methods to `OrderController.java`:

```java
@GetMapping("/{orderId}")
public ApiResponse<OrderResponse> getOrder(@PathVariable Long orderId) {
    OrderEntity order = orderService.getOrderById(orderId);
    return ApiResponse.success(OrderResponse.from(order));
}

@PostMapping("/{orderId}/cancel")
public ApiResponse<OrderResponse> cancelOrder(@PathVariable Long orderId) {
    OrderEntity order = orderService.cancelOrder(orderId);
    return ApiResponse.success(OrderResponse.from(order));
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew :order-service:test --tests "io.github.hjle.order.OrderServiceTest" 2>&1 | tail -20
```
Expected: 5 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add order-service/src/main/java/io/github/hjle/order/OrderService.java
git add order-service/src/main/java/io/github/hjle/order/OrderController.java
git add order-service/src/test/java/io/github/hjle/order/OrderServiceTest.java
git commit -m "feat(order): add GET /order/{id}, POST /order/{id}/cancel, migrate createOrder to outbox"
```

---

## Task 6: settlement-service — SettlementStatus + SettlementEntity.cancel()

**Files:**
- Modify: `settlement-service/src/main/java/io/github/hjle/settlement/SettlementStatus.java`
- Modify: `settlement-service/src/main/java/io/github/hjle/settlement/dto/SettlementEntity.java`

- [ ] **Step 1: Add CANCELLED to SettlementStatus**

```java
public enum SettlementStatus {
    PENDING,
    READY,
    COMPLETED,
    FAILED,
    CANCELLED   // ← add this
}
```

- [ ] **Step 2: Add cancel() to SettlementEntity**

Add after `completeSettlement()`:
```java
public void cancel() {
    this.status = SettlementStatus.CANCELLED;
}
```

- [ ] **Step 3: Commit**

```bash
git add settlement-service/src/main/java/io/github/hjle/settlement/SettlementStatus.java
git add settlement-service/src/main/java/io/github/hjle/settlement/dto/SettlementEntity.java
git commit -m "feat(settlement): add CANCELLED status and cancel() domain method"
```

---

## Task 7: settlement-service — DLQ infrastructure + retry config

**Files:**
- Create: `settlement-service/src/main/java/io/github/hjle/settlement/dlq/FailedEventEntity.java`
- Create: `settlement-service/src/main/java/io/github/hjle/settlement/dlq/FailedEventRepository.java`
- Create: `settlement-service/src/main/java/io/github/hjle/settlement/dlq/FailedEventHandler.java`
- Modify: `settlement-service/src/main/java/io/github/hjle/settlement/config/KafkaConsumerConfig.java`

- [ ] **Step 1: Create FailedEventEntity**

```java
// settlement-service/src/main/java/io/github/hjle/settlement/dlq/FailedEventEntity.java
package io.github.hjle.settlement.dlq;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "failed_events")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FailedEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: Create FailedEventRepository**

```java
// settlement-service/src/main/java/io/github/hjle/settlement/dlq/FailedEventRepository.java
package io.github.hjle.settlement.dlq;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailedEventRepository extends JpaRepository<FailedEventEntity, Long> {
}
```

- [ ] **Step 3: Create FailedEventHandler**

```java
// settlement-service/src/main/java/io/github/hjle/settlement/dlq/FailedEventHandler.java
package io.github.hjle.settlement.dlq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailedEventHandler {

    private final FailedEventRepository failedEventRepository;

    // @DltHandler only works inside the same class as @KafkaListener.
    // Instead we use a dedicated @KafkaListener on the .DLT topics created by DeadLetterPublishingRecoverer.
    @KafkaListener(
            topics = {"order-created.DLT", "order-cancelled.DLT"},
            groupId = "${spring.kafka.consumer.group-id}-dlq"
    )
    public void handleDlt(ConsumerRecord<String, String> record) {
        log.error("[DLT] Failed event received. topic={}, key={}", record.topic(), record.key());

        failedEventRepository.save(FailedEventEntity.builder()
                .topic(record.topic())
                .payload(record.value() != null ? record.value() : "null")
                .errorMessage("Routed to DLT after max retries")
                .build());
    }
}
```

- [ ] **Step 4: Refactor KafkaConsumerConfig**

Replace `KafkaConsumerConfig.java` with the following. This adds:
- Retry with exponential backoff (1s, 2s, 4s → max 3 attempts)
- `DeadLetterPublishingRecoverer` for both topics
- A second `ConsumerFactory` + `ContainerFactory` for `OrderCancelledEvent`

```java
package io.github.hjle.settlement.config;

import com.hjle.common.event.OrderCancelledEvent;
import com.hjle.common.event.OrderCreatedEvent;
import io.github.hjle.settlement.dlq.FailedEventHandler;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // ── OrderCreatedEvent ──────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, OrderCreatedEvent> orderCreatedConsumerFactory() {
        JsonDeserializer<OrderCreatedEvent> deserializer = new JsonDeserializer<>(OrderCreatedEvent.class);
        deserializer.addTrustedPackages("com.hjle.common.event");
        deserializer.setUseTypeHeaders(false);
        return new DefaultKafkaConsumerFactory<>(consumerProps(), new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderCreatedConsumerFactory());
        factory.setCommonErrorHandler(defaultErrorHandler());
        return factory;
    }

    // ── OrderCancelledEvent ────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, OrderCancelledEvent> orderCancelledConsumerFactory() {
        JsonDeserializer<OrderCancelledEvent> deserializer = new JsonDeserializer<>(OrderCancelledEvent.class);
        deserializer.addTrustedPackages("com.hjle.common.event");
        deserializer.setUseTypeHeaders(false);
        return new DefaultKafkaConsumerFactory<>(consumerProps(), new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCancelledEvent> orderCancelledListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderCancelledEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderCancelledConsumerFactory());
        factory.setCommonErrorHandler(defaultErrorHandler());
        return factory;
    }

    // ── Shared ─────────────────────────────────────────────────────────────

    private Map<String, Object> consumerProps() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        return config;
    }

    // ── DLT producer (settlement-service has no KafkaTemplate by default) ─

    @Bean
    public KafkaTemplate<String, String> dltKafkaTemplate() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    private DefaultErrorHandler defaultErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxAttempts(3);  // 3 retries after initial = 4 total attempts
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate());
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
```

Add imports: `org.apache.kafka.clients.producer.ProducerConfig`, `org.apache.kafka.common.serialization.StringSerializer`, `org.springframework.kafka.core.DefaultKafkaProducerFactory`

- [ ] **Step 5: Commit**

```bash
git add settlement-service/src/main/java/io/github/hjle/settlement/dlq/
git add settlement-service/src/main/java/io/github/hjle/settlement/config/KafkaConsumerConfig.java
git commit -m "feat(settlement): add DLQ infrastructure — FailedEventEntity, FailedEventHandler, retry+backoff config"
```

---

## Task 8: settlement-service — OrderCancelledEventConsumer

**Files:**
- Create: `settlement-service/src/main/java/io/github/hjle/settlement/OrderCancelledEventConsumer.java`
- Create: `settlement-service/src/test/java/io/github/hjle/settlement/OrderCancelledEventConsumerTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// settlement-service/src/test/java/io/github/hjle/settlement/OrderCancelledEventConsumerTest.java
package io.github.hjle.settlement;

import com.hjle.common.event.OrderCancelledEvent;
import com.hjle.common.exception.BusinessException;
import io.github.hjle.settlement.dto.SettlementEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCancelledEventConsumerTest {

    @Mock SettlementRepository settlementRepository;

    OrderCancelledEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderCancelledEventConsumer(settlementRepository);
    }

    OrderCancelledEvent event(long orderId) {
        return OrderCancelledEvent.builder()
                .orderId(orderId).userId("u1").canceledAt(LocalDateTime.now()).build();
    }

    @Test
    void cancels_pending_settlement() {
        SettlementEntity settlement = SettlementEntity.builder()
                .orderId(1L).userId("u1")
                .totalAmount(1000L).feeAmount(100L).settlementAmount(900L)
                .status(SettlementStatus.PENDING).build();
        when(settlementRepository.findByOrderId(1L)).thenReturn(Optional.of(settlement));

        consumer.handleOrderCancelled(event(1L));

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CANCELLED);
        verify(settlementRepository).save(settlement);
    }

    @Test
    void throws_when_settlement_already_completed() {
        SettlementEntity settlement = SettlementEntity.builder()
                .orderId(1L).userId("u1")
                .totalAmount(1000L).feeAmount(100L).settlementAmount(900L)
                .status(SettlementStatus.COMPLETED).build();
        when(settlementRepository.findByOrderId(1L)).thenReturn(Optional.of(settlement));

        assertThatThrownBy(() -> consumer.handleOrderCancelled(event(1L)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void no_op_when_settlement_not_found() {
        when(settlementRepository.findByOrderId(99L)).thenReturn(Optional.empty());

        // should not throw
        consumer.handleOrderCancelled(event(99L));

        verify(settlementRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :settlement-service:test --tests "io.github.hjle.settlement.OrderCancelledEventConsumerTest" 2>&1 | tail -20
```
Expected: FAIL — `OrderCancelledEventConsumer` does not exist, `findByOrderId` not on repo.

- [ ] **Step 3: Verify findByOrderId exists in SettlementRepository**

`findByOrderId(Long orderId)` already exists in `SettlementRepository.java` — no change needed. Skip this step.

- [ ] **Step 4: Implement OrderCancelledEventConsumer**

```java
// settlement-service/src/main/java/io/github/hjle/settlement/OrderCancelledEventConsumer.java
package io.github.hjle.settlement;

import com.hjle.common.event.OrderCancelledEvent;
import com.hjle.common.exception.BusinessException;
import com.hjle.common.exception.ErrorCode;
import io.github.hjle.settlement.dto.SettlementEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledEventConsumer {

    private final SettlementRepository settlementRepository;

    @Transactional
    @KafkaListener(
            topics = "order-cancelled",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "orderCancelledListenerContainerFactory"
    )
    public void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("[OrderCancelledEventConsumer] Received: orderId={}", event.getOrderId());

        Optional<SettlementEntity> maybeSettlement = settlementRepository.findByOrderId(event.getOrderId());

        if (maybeSettlement.isEmpty()) {
            log.warn("[OrderCancelledEventConsumer] No settlement found for orderId={}. " +
                    "May have arrived before order-created was processed.", event.getOrderId());
            return;
        }

        SettlementEntity settlement = maybeSettlement.get();

        if (settlement.getStatus() == SettlementStatus.COMPLETED) {
            log.error("[OrderCancelledEventConsumer] Settlement already COMPLETED for orderId={}. " +
                    "Cannot cancel — ALERT: manual review required.", event.getOrderId());
            throw new BusinessException(ErrorCode.SETTLEMENT_ALREADY_COMPLETED);
        }

        settlement.cancel();
        settlementRepository.save(settlement);
        log.info("[OrderCancelledEventConsumer] Settlement cancelled for orderId={}", event.getOrderId());
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew :settlement-service:test --tests "io.github.hjle.settlement.OrderCancelledEventConsumerTest" 2>&1 | tail -20
```
Expected: 3 tests PASS.

- [ ] **Step 6: Run all tests**

```bash
./gradlew :order-service:test :settlement-service:test 2>&1 | tail -30
```
Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add settlement-service/src/main/java/io/github/hjle/settlement/OrderCancelledEventConsumer.java
git add settlement-service/src/main/java/io/github/hjle/settlement/SettlementRepository.java
git add settlement-service/src/test/java/io/github/hjle/settlement/OrderCancelledEventConsumerTest.java
git commit -m "feat(settlement): add OrderCancelledEventConsumer with idempotency and COMPLETED guard"
```

---

## Final Verification

- [ ] **Run all service tests**

```bash
./gradlew :common:build :order-service:test :settlement-service:test 2>&1 | tail -40
```
Expected: All tests PASS, BUILD SUCCESSFUL.

- [ ] **Final commit if needed**

```bash
git status
git add -p   # stage any remaining changes
git commit -m "chore: finalize order cancellation, outbox, and settlement DLQ implementation"
```
