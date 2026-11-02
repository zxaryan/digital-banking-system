package com.banking.transaction_service.service;

import com.banking.transaction_service.dto.TransferRequest;
import com.banking.transaction_service.dto.TransactionResponse;

public interface TransactionService {

    TransactionResponse transfer(TransferRequest request);

    TransactionResponse getTransaction(String transactionId);

    TransactionResponse getTransactionHistory(String accountNumber);

    TransactionResponse verifyOTP(String transactionId, String otp);
}
