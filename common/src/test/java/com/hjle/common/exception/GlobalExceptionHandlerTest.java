package com.hjle.common.exception;

import com.hjle.common.dto.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("BusinessException은 ErrorCode에 정의된 HTTP 상태코드를 반환한다")
    void handleBusinessException_returnsErrorCodeHttpStatus() {
        BusinessException ex = new BusinessException(ErrorCode.SETTLEMENT_ALREADY_COMPLETED);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT); // 409
    }

    @Test
    @DisplayName("ENTITY_NOT_FOUND는 404를 반환한다")
    void handleBusinessException_entityNotFound_returns404() {
        BusinessException ex = new BusinessException(ErrorCode.ENTITY_NOT_FOUND);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND); // 404
    }

    @Test
    @DisplayName("HANDLE_ACCESS_DENIED는 403을 반환한다")
    void handleBusinessException_accessDenied_returns403() {
        BusinessException ex = new BusinessException(ErrorCode.HANDLE_ACCESS_DENIED);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN); // 403
    }

    @Test
    @DisplayName("응답 body에 에러 메시지가 포함된다")
    void handleBusinessException_responseBodyContainsMessage() {
        BusinessException ex = new BusinessException(ErrorCode.SETTLEMENT_ALREADY_COMPLETED);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("error");
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.SETTLEMENT_ALREADY_COMPLETED.getMessage());
    }
}
