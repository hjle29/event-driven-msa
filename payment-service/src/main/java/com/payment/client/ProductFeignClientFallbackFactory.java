package com.payment.client;

import com.hjle.common.exception.BusinessException;
import com.hjle.common.exception.ErrorCode;
import com.payment.client.dto.ProductInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Circuit Breaker OPEN 상태 또는 타임아웃 시 호출되는 Fallback.
 * 결제는 상품 정보 없이 절대 진행하면 안 되므로 예외를 던진다.
 * (가격을 신뢰할 수 없는 상태에서 결제를 허용하면 금전적 손실 발생)
 */
@Slf4j
@Component
public class ProductFeignClientFallbackFactory implements FallbackFactory<ProductFeignClient> {

    @Override
    public ProductFeignClient create(Throwable cause) {
        return productId -> {
            log.error("Product service unavailable. productId={}, cause={}", productId, cause.getMessage());
            throw new BusinessException("Product service is currently unavailable. Please try again later.",
                    ErrorCode.INTERNAL_SERVER_ERROR);
        };
    }
}
