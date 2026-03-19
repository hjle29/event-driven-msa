package io.github.hjle.order.dto.response;

import io.github.hjle.order.OrderStatus;
import io.github.hjle.order.dto.OrderEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private Long orderId;
    private String userId;
    private String productId;
    private String productName;
    private Integer quantity;
    private Integer unitPrice;
    private Integer totalPrice;
    private OrderStatus status;
    private LocalDateTime createdAt;

    public static OrderResponse from(OrderEntity entity) {
        return OrderResponse.builder()
                .orderId(entity.getId())
                .userId(entity.getUserId())
                .productId(entity.getProductId())
                .productName(entity.getProductName())
                .quantity(entity.getQuantity())
                .unitPrice(entity.getUnitPrice())
                .totalPrice(entity.getTotalPrice())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
