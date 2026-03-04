package io.github.hjle.settlement;

import com.hjle.common.dto.response.ApiResponse;
import io.github.hjle.settlement.dto.SettlementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/settlement")
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping("/{orderId}")
    public ApiResponse<SettlementResponse> getSettlementByOrderId(@PathVariable Long orderId) {
        return ApiResponse.success(settlementService.getSettlementByOrderId(orderId));
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<SettlementResponse>> getSettlementsByUserId(@PathVariable String userId) {
        return ApiResponse.success(settlementService.getSettlementsByUserId(userId));
    }

    @PostMapping("/{orderId}/complete")
    public ApiResponse<SettlementResponse> completeSettlement(@PathVariable Long orderId) {
        return ApiResponse.success(settlementService.completeSettlement(orderId));
    }
}
