package io.github.hjle.order.outbox;

public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED,
    DEAD
}
