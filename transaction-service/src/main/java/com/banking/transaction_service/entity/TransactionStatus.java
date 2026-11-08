package com.banking.transaction_service.entity;

public enum TransactionStatus {
    PENDING,
    PROCESSING,
    FLAGGED,
    COMPLETED,
    FAILED,
    CANCELLED
}