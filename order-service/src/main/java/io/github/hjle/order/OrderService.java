package io.github.hjle.order;

import com.hjle.common.event.OrderCreatedEvent;
import com.hjle.common.exception.BusinessException;
import com.hjle.common.exception.ErrorCode;
import io.github.hjle.order.dto.MemberResponse;
import io.github.hjle.order.dto.OrderEntity;
import io.github.hjle.order.dto.request.OrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MemberServiceClient memberServiceClient;

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

        kafkaTemplate.send("order-created", String.valueOf(savedOrder.getId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCreatedEvent. orderId={}", savedOrder.getId(), ex);
                    }
                });
        log.info("OrderCreatedEvent published. orderId={}", savedOrder.getId());

        return savedOrder;
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
}
