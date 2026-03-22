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

    @KafkaListener(
            topics = {"order-created.DLT", "order-cancelled.DLT"},
            groupId = "${spring.kafka.consumer.group-id}-dlq",
            containerFactory = "stringListenerContainerFactory"
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
