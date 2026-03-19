package com.payment.dto;

import com.payment.domain.Payment;
import com.payment.domain.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentResponse {

    private Long id;
    private Long memberId;
    private Long productId;
    private String receiptId;
    private PaymentStatus status;
    private Long amount;
    private LocalDateTime createdAt;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .memberId(payment.getMemberId())
                .productId(payment.getProductId())
                .receiptId(payment.getReceiptId())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
