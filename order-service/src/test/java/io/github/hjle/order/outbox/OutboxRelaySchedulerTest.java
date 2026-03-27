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
