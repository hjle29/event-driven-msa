package com.payment.exception;

import com.hjle.common.dto.response.ApiResponse;
import com.hjle.common.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("receipt_id")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(ErrorCode.PAYMENT_ALREADY_VERIFIED.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
