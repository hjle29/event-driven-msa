package io.github.hjle.settlement;

import com.hjle.common.exception.BusinessException;
import com.hjle.common.exception.ErrorCode;
import io.github.hjle.settlement.dto.OrderResponse;
import io.github.hjle.settlement.dto.SettlementEntity;
import io.github.hjle.settlement.dto.SettlementResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final OrderServiceClient orderServiceClient;
    private final SettlementRepository settlementRepository;

    @Transactional
    public void runSettlement() {
        List<OrderResponse> deliveredOrders = orderServiceClient.getOrdersByStatus("DELIVERED");

        for (OrderResponse order : deliveredOrders) {
            if (settlementRepository.existsByOrderId(order.getId())) {
                log.info("Settlement already exists for orderId={}. Skipping.", order.getId());
                continue;
            }

            if (order.getTotalPrice() == null) {
                log.warn("Order {} has null totalPrice. Skipping.", order.getId());
                continue;
            }

            long totalAmount = order.getTotalPrice().longValue();
            long feeAmount = (long) (totalAmount * 0.1);
            long settlementAmount = totalAmount - feeAmount;

            SettlementEntity settlement = SettlementEntity.builder()
                    .userId(order.getUserId())
                    .orderId(order.getId())
                    .totalAmount(totalAmount)
                    .feeAmount(feeAmount)
                    .settlementAmount(settlementAmount)
                    .status(SettlementStatus.READY)
                    .build();

            settlementRepository.save(settlement);
        }
    }

    @Transactional(readOnly = true)
    public SettlementResponse getSettlementByOrderId(Long orderId) {
        SettlementEntity entity = settlementRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        return SettlementResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> getSettlementsByUserId(String userId) {
        return settlementRepository.findByUserId(userId)
                .stream()
                .map(SettlementResponse::from)
                .toList();
    }

    @Transactional
    public SettlementResponse completeSettlement(Long orderId) {
        SettlementEntity entity = settlementRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        if (entity.getStatus() != SettlementStatus.READY) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ALREADY_COMPLETED);
        }

        entity.completeSettlement();
        return SettlementResponse.from(entity);
    }
}
