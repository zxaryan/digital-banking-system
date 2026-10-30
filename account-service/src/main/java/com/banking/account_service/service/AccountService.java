package com.banking.account_service.service;

import com.banking.account_service.dto.AccountResponse;
import com.banking.account_service.dto.CreateAccountRequest;

import java.math.BigDecimal;

public interface AccountService {
    AccountResponse createAccount(CreateAccountRequest request);
    AccountResponse getAccount(String accountNumber);
    BigDecimal getBalance(String accountNumber);
    void blockAccount(String accountNumber);
    void debitAmount(String accountNumber, BigDecimal amount);
    void creditAmount(String accountNumber, BigDecimal amount);
}