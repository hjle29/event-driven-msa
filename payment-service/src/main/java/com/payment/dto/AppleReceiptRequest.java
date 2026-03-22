package com.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AppleReceiptRequest {

    @NotBlank(message = "receiptData must not be blank")
    private String receiptData;

    @NotNull(message = "productId must not be null")
    private Long productId;

    @NotNull(message = "memberId must not be null")
    private Long memberId;

    // amount는 클라이언트에서 받지 않는다.
    // 가격은 반드시 product-service에서 조회해야 한다.
}
