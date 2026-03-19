package io.github.hjle.settlement;

import com.hjle.common.event.OrderCancelledEvent;
import com.hjle.common.exception.BusinessException;
import io.github.hjle.settlement.dto.SettlementEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCancelledEventConsumerTest {

    @Mock SettlementRepository settlementRepository;

    OrderCancelledEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderCancelledEventConsumer(settlementRepository);
    }

    OrderCancelledEvent event(long orderId) {
        return OrderCancelledEvent.builder()
                .orderId(orderId).userId("u1").canceledAt(LocalDateTime.now()).build();
    }

    @Test
    void cancels_pending_settlement() {
        SettlementEntity settlement = SettlementEntity.builder()
                .orderId(1L).userId("u1")
                .totalAmount(1000L).feeAmount(100L).settlementAmount(900L)
                .status(SettlementStatus.PENDING).build();
        when(settlementRepository.findByOrderId(1L)).thenReturn(Optional.of(settlement));

        consumer.handleOrderCancelled(event(1L));

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CANCELLED);
        verify(settlementRepository).save(settlement);
    }

    @Test
    void throws_when_settlement_already_completed() {
        SettlementEntity settlement = SettlementEntity.builder()
                .orderId(1L).userId("u1")
                .totalAmount(1000L).feeAmount(100L).settlementAmount(900L)
                .status(SettlementStatus.COMPLETED).build();
        when(settlementRepository.findByOrderId(1L)).thenReturn(Optional.of(settlement));

        assertThatThrownBy(() -> consumer.handleOrderCancelled(event(1L)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void no_op_when_settlement_not_found() {
        when(settlementRepository.findByOrderId(99L)).thenReturn(Optional.empty());

        consumer.handleOrderCancelled(event(99L));

        verify(settlementRepository, never()).save(any());
    }
}
