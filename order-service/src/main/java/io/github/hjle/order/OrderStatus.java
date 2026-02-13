package io.github.hjle.order;

import lombok.Getter;

@Getter
public enum OrderStatus {
    ORDERED("주문완료"),
    PAYMENT_COMPLETED("결제완료"),
    SHIPPING("배송중"),
    DELIVERED("배송완료"),
    CANCELLED("주문취소"),
    REFUNDED("환불완료");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }
}