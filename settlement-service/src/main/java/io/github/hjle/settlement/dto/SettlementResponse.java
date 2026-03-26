package io.github.hjle.settlement.dto;

import io.github.hjle.settlement.SettlementStatus;
import io.github.hjle.settlement.domain.SettlementEntity;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SettlementResponse {

    private Long id;
    private String userId;
    private Long orderId;
    private Long totalAmount;
    private Long feeAmount;
    private Long settlementAmount;
    private SettlementStatus status;
    private LocalDateTime settlementDate;
    private LocalDateTime createdAt;

    public static SettlementResponse from(SettlementEntity entity) {
        return SettlementResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .orderId(entity.getOrderId())
                .totalAmount(entity.getTotalAmount())
                .feeAmount(entity.getFeeAmount())
                .settlementAmount(entity.getSettlementAmount())
                .status(entity.getStatus())
                .settlementDate(entity.getSettlementDate())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
