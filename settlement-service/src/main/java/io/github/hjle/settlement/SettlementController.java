package io.github.hjle.settlement;

import com.hjle.common.dto.response.ApiResponse;
import com.hjle.common.exception.BusinessException;
import com.hjle.common.exception.ErrorCode;
import io.github.hjle.settlement.dto.SettlementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/settlement")
public class SettlementController {

    private final SettlementService settlementService;

    @Value("${internal.secret}")
    private String internalSecret;

    @GetMapping("/{orderId}")
    public ApiResponse<SettlementResponse> getSettlementByOrderId(@PathVariable Long orderId) {
        return ApiResponse.success(settlementService.getSettlementByOrderId(orderId));
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<SettlementResponse>> getSettlementsByUserId(@PathVariable String userId) {
        return ApiResponse.success(settlementService.getSettlementsByUserId(userId));
    }

    @PostMapping("/{orderId}/complete")
    public ApiResponse<SettlementResponse> completeSettlement(
            @RequestHeader("X-Internal-Secret") String secret,
            @PathVariable Long orderId) {
        if (!internalSecret.equals(secret)) {
            throw new BusinessException(ErrorCode.HANDLE_ACCESS_DENIED);
        }
        return ApiResponse.success(settlementService.completeSettlement(orderId));
    }
}
