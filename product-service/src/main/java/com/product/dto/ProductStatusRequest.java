package com.product.dto;

import com.product.domain.ProductStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProductStatusRequest {

    @NotNull(message = "상태 값은 필수입니다.")
    private ProductStatus status;
}
