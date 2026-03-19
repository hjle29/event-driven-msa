package com.payment.client;

import com.hjle.common.exception.BusinessException;
import com.hjle.common.exception.ErrorCode;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

/**
 * product-service로부터 받은 에러 응답을 BusinessException으로 변환.
 * 고트래픽 환경에서 upstream 에러를 내부 에러 체계로 통일하는 것이 중요하다.
 */
@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {
        log.error("Feign call failed: method={}, status={}", methodKey, response.status());

        return switch (response.status()) {
            case 404 -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
            case 400 -> new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE);
            default -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Product service error: " + response.status());
        };
    }
}
