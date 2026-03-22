package com.payment.client;

import com.payment.client.dto.ProductInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "product-service",
        url = "${product-service.url:http://localhost:8082}",
        configuration = FeignClientConfig.class,
        fallbackFactory = ProductFeignClientFallbackFactory.class
)
public interface ProductFeignClient {

    @GetMapping("/api/products/{id}")
    ProductInfo getProduct(@PathVariable("id") Long productId);
}
