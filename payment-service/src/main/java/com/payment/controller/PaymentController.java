package com.payment.controller;

import com.hjle.common.dto.response.ApiResponse;
import com.payment.dto.AppleReceiptRequest;
import com.payment.dto.GoogleReceiptRequest;
import com.payment.dto.PaymentResponse;
import com.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/apple")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentResponse> verifyApple(@Valid @RequestBody AppleReceiptRequest request) {
        PaymentResponse response = paymentService.verifyApple(request);
        return ApiResponse.success("Apple IAP verified successfully", response);
    }

    @PostMapping("/google")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentResponse> verifyGoogle(@Valid @RequestBody GoogleReceiptRequest request) {
        PaymentResponse response = paymentService.verifyGoogle(request);
        return ApiResponse.success("Google IAP verified successfully", response);
    }

    @GetMapping("/history")
    public ApiResponse<List<PaymentResponse>> getHistory(
            @RequestHeader("X-Member-Id") Long memberId) {
        List<PaymentResponse> history = paymentService.getHistory(memberId);
        return ApiResponse.success(history);
    }
}
