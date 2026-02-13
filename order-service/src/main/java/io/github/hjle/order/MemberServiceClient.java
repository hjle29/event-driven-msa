package io.github.hjle.order;

import io.github.hjle.order.dto.MemberResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "member-service")
public interface MemberServiceClient {
    @GetMapping("/member/{userId}")
    MemberResponse getMemberByUserId(@PathVariable("userId") String userId);
}