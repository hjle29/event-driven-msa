package com.payment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {

    private Long paymentId;
    private Long memberId;
    private Long productId;
    private Long amount;
    private LocalDateTime completedAt;
}
