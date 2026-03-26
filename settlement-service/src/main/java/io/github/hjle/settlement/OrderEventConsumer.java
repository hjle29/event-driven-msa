package io.github.hjle.settlement;

import com.hjle.common.event.OrderCreatedEvent;
import io.github.hjle.settlement.domain.SettlementEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final SettlementRepository settlementRepository;

    @Transactional
    @KafkaListener(
            topics = "order-created",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCreated(OrderCreatedEvent event, Acknowledgment acknowledgment) {
        try {
            log.info("[OrderEventConsumer] Received OrderCreatedEvent: orderId={}, userId={}",
                    event.getOrderId(), event.getUserId());

            if (settlementRepository.existsByOrderId(event.getOrderId())) {
                log.warn("[OrderEventConsumer] Duplicate event for orderId={}. Skipping.", event.getOrderId());
                acknowledgment.acknowledge();
                return;
            }

            SettlementEntity settlement = SettlementEntity.builder()
                    .orderId(event.getOrderId())
                    .userId(event.getUserId())
                    .totalAmount(0L)
                    .feeAmount(0L)
                    .settlementAmount(0L)
                    .status(SettlementStatus.PENDING)
                    .build();

            settlementRepository.save(settlement);
            log.info("[OrderEventConsumer] Settlement record created for orderId={}", event.getOrderId());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Kafka consume failed. topic=order-created, orderId={}", event.getOrderId(), e);
            throw e;
        }
    }
}
