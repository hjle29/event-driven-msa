package io.github.hjle.order;

import com.hjle.common.dto.response.ApiResponse;
import io.github.hjle.order.dto.OrderEntity;
import io.github.hjle.order.dto.request.OrderRequest;
import io.github.hjle.order.dto.response.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        OrderEntity savedOrder = orderService.createOrder(request);
        return ApiResponse.success(OrderResponse.from(savedOrder));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable Long orderId) {
        OrderEntity order = orderService.getOrderById(orderId);
        return ApiResponse.success(OrderResponse.from(order));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(@PathVariable Long orderId) {
        OrderEntity order = orderService.cancelOrder(orderId);
        return ApiResponse.success(OrderResponse.from(order));
    }

    @GetMapping("/{userId}/with-member")
    public ApiResponse<Map<String, Object>> getOrderWithMember(@PathVariable String userId) {
        return ApiResponse.success(orderService.getOrdersWithMember(userId));
    }
}
