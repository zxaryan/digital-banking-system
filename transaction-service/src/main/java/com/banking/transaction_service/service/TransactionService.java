package com.banking.transaction_service.service;

import com.banking.transaction_service.dto.TransferRequest;
import com.banking.transaction_service.dto.TransactionResponse;

import java.util.List;

public interface TransactionService {
    TransactionResponse transfer(TransferRequest request);
    TransactionResponse getTransaction(String transactionId);
    List<TransactionResponse> getTransactionHistory(String accountNumber);
    TransactionResponse verifyOTP(String transactionId, String otp);

    void processCleanResult(String transactionId);
}
