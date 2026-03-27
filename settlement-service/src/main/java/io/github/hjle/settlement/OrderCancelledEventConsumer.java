package io.github.hjle.settlement;

import com.hjle.common.event.OrderCancelledEvent;
import com.hjle.common.exception.BusinessException;
import com.hjle.common.exception.ErrorCode;
import io.github.hjle.settlement.domain.SettlementEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
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
    public void handleOrderCancelled(OrderCancelledEvent event, Acknowledgment acknowledgment) {
        try {
            log.info("[OrderCancelledEventConsumer] Received: orderId={}", event.getOrderId());

            Optional<SettlementEntity> maybeSettlement = settlementRepository.findByOrderId(event.getOrderId());

            if (maybeSettlement.isEmpty()) {
                log.warn("[OrderCancelledEventConsumer] No settlement found for orderId={}. " +
                        "May have arrived before order-created was processed.", event.getOrderId());
                acknowledgment.acknowledge();
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
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Kafka consume failed. topic=order-cancelled, orderId={}", event.getOrderId(), e);
            throw e;
        }
    }
}
