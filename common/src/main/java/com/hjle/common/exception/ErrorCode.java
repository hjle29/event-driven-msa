package com.hjle.common.exception;

import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(400, "C001", "Invalid input value"),
    METHOD_NOT_ALLOWED(405, "C002", "Method not allowed"),
    ENTITY_NOT_FOUND(400, "C003", "Entity not found"),
    INTERNAL_SERVER_ERROR(500, "C004", "Server error"),
    INVALID_TYPE_VALUE(400, "C005", "Invalid type value"),
    HANDLE_ACCESS_DENIED(403, "C006", "Access is denied"),

    // Member
    EMAIL_DUPLICATION(400, "M001", "Email is duplicated"),
    LOGIN_INPUT_INVALID(400, "M002", "Login input is invalid");

    private final int status;
    private final String code;
    private final String message;
}