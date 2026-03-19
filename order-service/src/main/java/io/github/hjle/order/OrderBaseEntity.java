package io.github.hjle.order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderBaseEntity {
    private Long id;
    private String userId;
    private String email;
    private String productId;
    private String productName;
    private Integer quantity;
    private Integer unitPrice;
    private Integer totalPrice;
    private OrderStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime canceledAt;
}