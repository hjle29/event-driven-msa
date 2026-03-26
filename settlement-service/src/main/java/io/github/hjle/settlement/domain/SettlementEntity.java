package io.github.hjle.settlement.domain;

import io.github.hjle.settlement.SettlementStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "settlements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SettlementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private Long totalAmount;

    @Column(nullable = false)
    private Long feeAmount;

    @Column(nullable = false)
    private Long settlementAmount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status = SettlementStatus.READY;

    private LocalDateTime settlementDate;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public void completeSettlement() {
        this.status = SettlementStatus.COMPLETED;
        this.settlementDate = LocalDateTime.now();
    }

    public void cancel() {
        this.status = SettlementStatus.CANCELLED;
    }
}
