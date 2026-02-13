package io.github.hjle.order;

import io.github.hjle.order.dto.MemberResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/order")
public class OrderController {
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final MemberServiceClient memberServiceClient; // 방금 만든 클라이언트 주입

    @GetMapping("/{userId}/with-member")
    public Map<String, Object> getOrderWithMember(@PathVariable String userId) {

        // 1. Member 서비스에 API 호출해서 사용자 정보 가져오기
        MemberResponse memberInfo = memberServiceClient.getMemberByUserId(userId);

        // 2. Order 서비스 DB에서 주문 목록 조회
        List<OrderBaseEntity> orders = orderRepository.findByUserId(userId);
        if (CollectionUtils.isEmpty(orders)) {
            new IllegalArgumentException("해당 사용자가 존재하지 않습니다. userId=" + userId);
        }

        // 3. 데이터 합쳐서 반환
        Map<String, Object> result = new HashMap<>();
        result.put("orders", orders);
        result.put("member", memberInfo);

        return result;
    }
}