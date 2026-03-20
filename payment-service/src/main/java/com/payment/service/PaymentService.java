package com.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjle.common.exception.BusinessException;
import com.hjle.common.exception.ErrorCode;
import com.payment.client.ProductFeignClient;
import com.payment.client.dto.ProductInfo;
import com.payment.domain.Payment;
import com.payment.dto.AppleReceiptRequest;
import com.payment.dto.GoogleReceiptRequest;
import com.payment.dto.PaymentResponse;
import com.payment.event.PaymentCompletedEvent;
import com.payment.outbox.OutboxEntity;
import com.payment.outbox.OutboxRepository;
import com.payment.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ProductFeignClient productFeignClient;

    @Value("${kafka.topic.payment-completed}")
    private String paymentCompletedTopic;

    @CircuitBreaker(name = "iapVerification")
    @Retry(name = "iapVerification")
    @Transactional
    public PaymentResponse verifyApple(AppleReceiptRequest request) {
        ProductInfo product = validateProduct(request.getProductId());

        String receiptId = hashReceiptData(request.getReceiptData());
        checkDuplicate(receiptId);

        // TODO: 실제 Apple 서버 검증 (https://buy.itunes.apple.com/verifyReceipt)

        Payment payment = Payment.builder()
                .memberId(request.getMemberId())
                .productId(request.getProductId())
                .receiptId(receiptId)
                .provider("APPLE")
                .amount(product.getPrice())
                .build();

        payment.complete();
        Payment savedPayment = paymentRepository.save(payment);
        saveOutbox(savedPayment);

        log.info("Apple IAP verified: paymentId={}, memberId={}, productId={}, amount={}",
                savedPayment.getId(), savedPayment.getMemberId(),
                savedPayment.getProductId(), savedPayment.getAmount());

        return PaymentResponse.from(savedPayment);
    }

    @CircuitBreaker(name = "iapVerification")
    @Retry(name = "iapVerification")
    @Transactional
    public PaymentResponse verifyGoogle(GoogleReceiptRequest request) {
        ProductInfo product = validateProduct(request.getProductId());

        String receiptId = truncatePurchaseToken(request.getPurchaseToken());
        checkDuplicate(receiptId);

        // TODO: 실제 Google Play Developer API 검증

        Payment payment = Payment.builder()
                .memberId(request.getMemberId())
                .productId(request.getProductId())
                .receiptId(receiptId)
                .provider("GOOGLE")
                .amount(product.getPrice())
                .build();

        payment.complete();
        Payment savedPayment = paymentRepository.save(payment);
        saveOutbox(savedPayment);

        log.info("Google IAP verified: paymentId={}, memberId={}, productId={}, amount={}",
                savedPayment.getId(), savedPayment.getMemberId(),
                savedPayment.getProductId(), savedPayment.getAmount());

        return PaymentResponse.from(savedPayment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getHistory(Long memberId) {
        return paymentRepository.findByMemberId(memberId).stream()
                .map(PaymentResponse::from)
                .collect(Collectors.toList());
    }

    private void saveOutbox(Payment payment) {
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .paymentId(payment.getId())
                .memberId(payment.getMemberId())
                .productId(payment.getProductId())
                .amount(payment.getAmount())
                .completedAt(payment.getCompletedAt())
                .build();

        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxRepository.save(OutboxEntity.builder()
                    .topic(paymentCompletedTopic)
                    .key(String.valueOf(payment.getId()))
                    .payload(payload)
                    .build());
        } catch (JsonProcessingException e) {
            throw new BusinessException("Failed to serialize PaymentCompletedEvent", ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private ProductInfo validateProduct(Long productId) {
        ProductInfo product = productFeignClient.getProduct(productId);
        if (!product.isAvailable()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }
        return product;
    }

    private void checkDuplicate(String receiptId) {
        if (paymentRepository.findByReceiptId(receiptId).isPresent()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_VERIFIED);
        }
    }

    private String hashReceiptData(String receiptData) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(receiptData.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException("Failed to hash receipt data", ErrorCode.INVALID_RECEIPT);
        }
    }

    private String truncatePurchaseToken(String purchaseToken) {
        return purchaseToken.length() <= 64 ? purchaseToken : purchaseToken.substring(0, 64);
    }
}
