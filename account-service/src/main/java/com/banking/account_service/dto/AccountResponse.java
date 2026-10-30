package com.banking.account_service.dto;

import com.banking.account_service.entity.AccountStatus;
import com.banking.account_service.entity.AccountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class AccountResponse {
    private UUID id;
    private String accountNumber;
    private String accountHolderName;
    private String phone;
    private String email;
    private AccountType accountType;
    private AccountStatus accountStatus;
    private BigDecimal balance;
    private BigDecimal dailyTransactionLimit;
    private LocalDateTime createdAt;
}
