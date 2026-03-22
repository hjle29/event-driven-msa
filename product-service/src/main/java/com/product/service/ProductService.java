package com.product.service;

import com.hjle.common.exception.BusinessException;
import com.hjle.common.exception.ErrorCode;
import com.product.domain.Product;
import com.product.domain.ProductStatus;
import com.product.dto.ProductCreateRequest;
import com.product.dto.ProductResponse;
import com.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(Pageable pageable) {
        return productRepository.findAllByStatus(ProductStatus.AVAILABLE, pageable)
                .map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .build();
        Product saved = productRepository.save(product);
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse changeStatus(Long id, ProductStatus status) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        product.changeStatus(status);
        return ProductResponse.from(product);
    }
}
