package io.github.hjle.settlement;

import com.hjle.common.exception.BusinessException;
import com.hjle.common.exception.ErrorCode;
import io.github.hjle.settlement.domain.SettlementEntity;
import io.github.hjle.settlement.dto.SettlementResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private OrderServiceClient orderServiceClient;

    @InjectMocks
    private SettlementService settlementService;

    // ────────────────────────────────────────────────
    // getSettlementByOrderId
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("orderId로 정산 조회 성공")
    void getSettlementByOrderId_found_returnsResponse() {
        SettlementEntity entity = buildEntity(1L, "user-1", SettlementStatus.READY);
        when(settlementRepository.findByOrderId(1L)).thenReturn(Optional.of(entity));

        SettlementResponse response = settlementService.getSettlementByOrderId(1L);

        assertThat(response.getOrderId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getStatus()).isEqualTo(SettlementStatus.READY);
    }

    @Test
    @DisplayName("orderId에 해당하는 정산이 없으면 BusinessException(SETTLEMENT_NOT_FOUND) 발생")
    void getSettlementByOrderId_notFound_throwsBusinessException() {
        when(settlementRepository.findByOrderId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.getSettlementByOrderId(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SETTLEMENT_NOT_FOUND));
    }

    // ────────────────────────────────────────────────
    // getSettlementsByUserId
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("userId로 정산 목록 조회 성공")
    void getSettlementsByUserId_found_returnsList() {
        SettlementEntity e1 = buildEntity(1L, "user-1", SettlementStatus.READY);
        SettlementEntity e2 = buildEntity(2L, "user-1", SettlementStatus.COMPLETED);
        when(settlementRepository.findByUserId("user-1")).thenReturn(List.of(e1, e2));

        List<SettlementResponse> result = settlementService.getSettlementsByUserId("user-1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getOrderId()).isEqualTo(1L);
        assertThat(result.get(1).getOrderId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("userId에 해당하는 정산이 없으면 빈 목록 반환")
    void getSettlementsByUserId_noResults_returnsEmptyList() {
        when(settlementRepository.findByUserId("unknown")).thenReturn(List.of());

        List<SettlementResponse> result = settlementService.getSettlementsByUserId("unknown");

        assertThat(result).isEmpty();
    }

    // ────────────────────────────────────────────────
    // completeSettlement
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("READY 상태 정산을 완료 처리하면 COMPLETED 상태로 변경된다")
    void completeSettlement_readyStatus_completesSuccessfully() {
        SettlementEntity entity = buildEntity(1L, "user-1", SettlementStatus.READY);
        when(settlementRepository.findByOrderId(1L)).thenReturn(Optional.of(entity));

        SettlementResponse response = settlementService.completeSettlement(1L);

        assertThat(response.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(response.getSettlementDate()).isNotNull();
    }

    @Test
    @DisplayName("orderId에 해당하는 정산이 없으면 completeSettlement에서 SETTLEMENT_NOT_FOUND 발생")
    void completeSettlement_notFound_throwsBusinessException() {
        when(settlementRepository.findByOrderId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.completeSettlement(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SETTLEMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("이미 COMPLETED 상태인 정산을 다시 완료 처리하면 SETTLEMENT_NOT_IN_READY_STATE 발생")
    void completeSettlement_alreadyCompleted_throwsBusinessException() {
        SettlementEntity entity = buildEntity(1L, "user-1", SettlementStatus.COMPLETED);
        when(settlementRepository.findByOrderId(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> settlementService.completeSettlement(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SETTLEMENT_NOT_IN_READY_STATE));
    }

    @Test
    @DisplayName("FAILED 상태인 정산을 완료 처리하면 SETTLEMENT_NOT_IN_READY_STATE 발생")
    void completeSettlement_failedStatus_throwsBusinessException() {
        SettlementEntity entity = buildEntity(1L, "user-1", SettlementStatus.FAILED);
        when(settlementRepository.findByOrderId(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> settlementService.completeSettlement(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SETTLEMENT_NOT_IN_READY_STATE));
    }

    // ────────────────────────────────────────────────
    // SettlementEntity domain logic
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("completeSettlement() 호출 시 status가 COMPLETED로, settlementDate가 설정된다")
    void settlementEntity_completeSettlement_setsStatusAndDate() {
        SettlementEntity entity = buildEntity(1L, "user-1", SettlementStatus.READY);

        entity.completeSettlement();

        assertThat(entity.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(entity.getSettlementDate()).isNotNull();
    }

    // ────────────────────────────────────────────────
    // SettlementResponse.from
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("SettlementResponse.from: status가 null이면 response의 status도 null이다")
    void settlementResponse_from_nullStatus_returnsNullStatus() {
        SettlementEntity entity = SettlementEntity.builder()
                .userId("user-1")
                .orderId(1L)
                .totalAmount(10000L)
                .feeAmount(1000L)
                .settlementAmount(9000L)
                .status(null)
                .build();

        SettlementResponse response = SettlementResponse.from(entity);

        assertThat(response.getStatus()).isNull();
    }

    @Test
    @DisplayName("SettlementResponse.from: status가 존재하면 name()이 반환된다")
    void settlementResponse_from_withStatus_returnsStatusName() {
        SettlementEntity entity = buildEntity(1L, "user-1", SettlementStatus.COMPLETED);

        SettlementResponse response = SettlementResponse.from(entity);

        assertThat(response.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
    }

    // ────────────────────────────────────────────────
    // Helper
    // ────────────────────────────────────────────────

    private SettlementEntity buildEntity(Long orderId, String userId, SettlementStatus status) {
        return SettlementEntity.builder()
                .userId(userId)
                .orderId(orderId)
                .totalAmount(10000L)
                .feeAmount(1000L)
                .settlementAmount(9000L)
                .status(status)
                .build();
    }
}
