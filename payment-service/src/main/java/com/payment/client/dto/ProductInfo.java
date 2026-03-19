package com.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * product-service의 ApiResponse<ProductResponse>에서 data 필드만 매핑.
 * FeignDecoder가 unwrap 처리.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductInfo {

    private Long id;
    private String name;
    private Long price;
    private String status;

    public boolean isAvailable() {
        return "AVAILABLE".equals(status);
    }
}
