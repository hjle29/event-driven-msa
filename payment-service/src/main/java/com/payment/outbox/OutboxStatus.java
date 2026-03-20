package com.payment.outbox;

public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED,
    DEAD
}
