package com.payment.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
@Component
public class OutboxRelayScheduler {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> stringKafkaTemplate;
    private final int maxRetries;

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
        List<OutboxEntity> rows = outboxRepository.findRetryableWithLock(maxRetries);
        for (OutboxEntity outbox : rows) {
            try {
                stringKafkaTemplate.send(outbox.getTopic(), outbox.getKey(), outbox.getPayload()).get();
                outbox.markSent();
                log.info("Outbox relay sent: id={}, topic={}", outbox.getId(), outbox.getTopic());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Outbox relay interrupted: id={}", outbox.getId(), e);
                outbox.markFailedOrDead(maxRetries);
            } catch (ExecutionException e) {
                log.error("Outbox relay failed: id={}, retryCount={}", outbox.getId(), outbox.getRetryCount(), e);
                outbox.markFailedOrDead(maxRetries);
                if (outbox.getStatus() == OutboxStatus.DEAD) {
                    log.error("Outbox entry DEAD — manual intervention required: id={}, topic={}", outbox.getId(), outbox.getTopic());
                }
            }
        }
    }
}
