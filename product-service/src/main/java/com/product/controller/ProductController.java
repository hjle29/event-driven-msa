package com.product.controller;

import com.hjle.common.dto.response.ApiResponse;
import com.product.dto.ProductCreateRequest;
import com.product.dto.ProductResponse;
import com.product.dto.ProductStatusRequest;
import com.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getProducts(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ProductResponse> products = productService.getProducts(pageable);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long id) {
        ProductResponse product = productService.getProduct(id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @RequestBody @Valid ProductCreateRequest request) {
        ProductResponse product = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(product));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ProductResponse>> changeStatus(
            @PathVariable Long id,
            @RequestBody @Valid ProductStatusRequest request) {
        ProductResponse product = productService.changeStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success(product));
    }
}
