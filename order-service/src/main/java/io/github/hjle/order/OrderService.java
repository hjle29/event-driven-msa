package io.github.hjle.order;

import com.hjle.common.event.OrderCreatedEvent;
import io.github.hjle.order.dto.OrderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public OrderEntity createOrder(String userId, String email, String productName, Integer quantity, Integer totalPrice) {
        // 1. 주문 생성
        OrderEntity order = OrderEntity.builder()
                .userId(userId)
                .email(email)
                .productName(productName)
                .quantity(quantity)
                .totalPrice(totalPrice)
                .createdAt(LocalDateTime.now())
                .build();

        OrderEntity savedOrder = orderRepository.save(order);

        // TODO: 재고 차감 로직
        // inventoryService.decreaseStock(productName, quantity);

        // TODO: Settlement Service로 정산 데이터 전달
        // settlementService.createSettlement(savedOrder);

        // 2. Kafka 이벤트 발행
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(savedOrder.getId())
                .userId(savedOrder.getUserId())
                .email(savedOrder.getEmail())
                .createdAt(savedOrder.getCreatedAt())
                .build();

        kafkaTemplate.send("order-created", event);

        return savedOrder;
    }
}