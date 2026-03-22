package io.github.hjle.order.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:5000}")
    public void relay() {
        List<OutboxEntity> pending = outboxRepository.findRetryableWithLock(maxRetries);
        for (OutboxEntity outbox : pending) {
            try {
                stringKafkaTemplate.send(outbox.getTopic(), outbox.getKey(), outbox.getPayload()).get();
                outbox.markSent();
                log.info("[OutboxRelay] Published. topic={}, key={}", outbox.getTopic(), outbox.getKey());
            } catch (Exception ex) {
                log.error("[OutboxRelay] Failed to publish. topic={}, key={}, retryCount={}",
                        outbox.getTopic(), outbox.getKey(), outbox.getRetryCount(), ex);
                outbox.markFailedOrDead(maxRetries);
                if (outbox.getStatus() == OutboxStatus.DEAD) {
                    log.error("[OutboxRelay] DEAD — manual intervention required. topic={}, key={}",
                            outbox.getTopic(), outbox.getKey());
                }
            }
            outboxRepository.save(outbox);
        }
    }
}
