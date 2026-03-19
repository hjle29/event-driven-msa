package com.hjle.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private Long orderId;
    private String userId;
    private String email;
    private String productId;
    private String productName;
    private Integer quantity;
    private Integer totalPrice;
    private LocalDateTime createdAt;
}