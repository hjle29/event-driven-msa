package com.hjle.common.exception;

import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(400, "C001", "Invalid input value"),
    METHOD_NOT_ALLOWED(405, "C002", "Method not allowed"),
    ENTITY_NOT_FOUND(404, "C003", "Entity not found"),
    INTERNAL_SERVER_ERROR(500, "C004", "Server error"),
    INVALID_TYPE_VALUE(400, "C005", "Invalid type value"),
    HANDLE_ACCESS_DENIED(403, "C006", "Access is denied"),

    // Member
    EMAIL_DUPLICATION(409, "M001", "Email is duplicated"),
    LOGIN_INPUT_INVALID(400, "M002", "Login input is invalid"),
    INVALID_TOKEN(401, "M003", "Invalid token"),
    MEMBER_NOT_FOUND(404, "M004", "Member not found"),
    INVALID_PASSWORD(400, "M005", "Invalid password"),
    UNAUTHORIZED(401, "M006", "Unauthorized"),

    // Order
    ORDER_NOT_FOUND(404, "O001", "Order not found"),
    ORDER_MEMBER_FETCH_FAILED(503, "O002", "Failed to fetch member info for order"),
    ORDER_CANCEL_FORBIDDEN(409, "O003", "Order cannot be cancelled"),

    // Settlement
    SETTLEMENT_ALREADY_COMPLETED(409, "S001", "Settlement is already completed and cannot be cancelled"),

    // Payment
    PAYMENT_ALREADY_VERIFIED(409, "PAY001", "Payment receipt has already been verified"),
    PAYMENT_VERIFICATION_FAILED(400, "PAY002", "Payment receipt verification failed"),
    INVALID_RECEIPT(400, "PAY003", "Invalid payment receipt"),

    // Product
    PRODUCT_NOT_FOUND(404, "PR001", "Product not found"),
    PRODUCT_NOT_AVAILABLE(400, "PR002", "Product is not available for purchase");

    private final int status;
    private final String code;
    private final String message;
}
