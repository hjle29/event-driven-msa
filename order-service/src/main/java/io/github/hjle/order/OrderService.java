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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
