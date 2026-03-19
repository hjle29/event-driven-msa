package com.payment.service;

import com.hjle.common.exception.BusinessException;
import com.hjle.common.exception.ErrorCode;
import com.payment.client.ProductFeignClient;
import com.payment.client.dto.ProductInfo;
import com.payment.domain.Payment;
import com.payment.dto.AppleReceiptRequest;
import com.payment.dto.GoogleReceiptRequest;
import com.payment.dto.PaymentResponse;
import com.payment.event.PaymentCompletedEvent;
import com.payment.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
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
    private final KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;
    private final ProductFeignClient productFeignClient;

    @Value("${kafka.topic.payment-completed}")
    private String paymentCompletedTopic;

    @CircuitBreaker(name = "iapVerification")
    @Retry(name = "iapVerification")
    @Transactional
    public PaymentResponse verifyApple(AppleReceiptRequest request) {
        // 1. 상품 유효성 검증 및 가격 조회 (클라이언트 제공 가격 절대 신뢰하지 않음)
        ProductInfo product = validateProduct(request.getProductId());

        String receiptId = hashReceiptData(request.getReceiptData());
        checkDuplicate(receiptId);

        // TODO: 실제 Apple 서버 검증 (https://buy.itunes.apple.com/verifyReceipt)
        // AppleVerificationResponse appleResponse = appleIapClient.verify(request.getReceiptData());
        // if (!appleResponse.isSuccess()) { throw new BusinessException(ErrorCode.PAYMENT_VERIFICATION_FAILED); }

        Payment payment = Payment.builder()
                .memberId(request.getMemberId())
                .productId(request.getProductId())
                .receiptId(receiptId)
                .provider("APPLE")
                .amount(product.getPrice())
                .build();

        payment.complete();
        Payment savedPayment = paymentRepository.save(payment);

        publishPaymentCompletedEvent(savedPayment);

        log.info("Apple IAP verified: paymentId={}, memberId={}, productId={}, amount={}",
                savedPayment.getId(), savedPayment.getMemberId(),
                savedPayment.getProductId(), savedPayment.getAmount());

        return PaymentResponse.from(savedPayment);
    }

    @CircuitBreaker(name = "iapVerification")
    @Retry(name = "iapVerification")
    @Transactional
    public PaymentResponse verifyGoogle(GoogleReceiptRequest request) {
        // 1. 상품 유효성 검증 및 가격 조회
        ProductInfo product = validateProduct(request.getProductId());

        String receiptId = truncatePurchaseToken(request.getPurchaseToken());
        checkDuplicate(receiptId);

        // TODO: 실제 Google Play Developer API 검증
        // GoogleVerificationResponse googleResponse = googleIapClient.verify(request.getPackageName(), request.getPurchaseToken());
        // if (!googleResponse.isSuccess()) { throw new BusinessException(ErrorCode.PAYMENT_VERIFICATION_FAILED); }

        Payment payment = Payment.builder()
                .memberId(request.getMemberId())
                .productId(request.getProductId())
                .receiptId(receiptId)
                .provider("GOOGLE")
                .amount(product.getPrice())
                .build();

        payment.complete();
        Payment savedPayment = paymentRepository.save(payment);

        publishPaymentCompletedEvent(savedPayment);

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

    /**
     * 상품이 존재하는지, 판매 가능 상태인지 검증.
     * 가격을 product-service에서 가져와 결제에 사용한다.
     */
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

    private void publishPaymentCompletedEvent(Payment payment) {
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .paymentId(payment.getId())
                .memberId(payment.getMemberId())
                .productId(payment.getProductId())
                .amount(payment.getAmount())
                .completedAt(payment.getCompletedAt())
                .build();

        kafkaTemplate.send(paymentCompletedTopic, String.valueOf(payment.getId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish PaymentCompletedEvent: paymentId={}", payment.getId(), ex);
                    } else {
                        log.info("PaymentCompletedEvent published: paymentId={}, offset={}",
                                payment.getId(), result.getRecordMetadata().offset());
                    }
                });
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
