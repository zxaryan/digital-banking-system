package com.banking.account_service.service;

import com.banking.account_service.dto.AccountResponse;
import com.banking.account_service.dto.CreateAccountRequest;
import com.banking.account_service.entity.Account;
import com.banking.account_service.entity.AccountStatus;
import com.banking.account_service.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;

    @Override
    public AccountResponse createAccount(CreateAccountRequest request) {

        log.info("Creating account for: {}", request.getEmail());

        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException(
                    "Account already exists for: " + request.getEmail()
            );
        }

        Account account = Account.builder()
                .accountNumber(UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .accountHolderName(request.getAccountHolderName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .accountType(request.getAccountType())
                .accountStatus(AccountStatus.ACTIVE)
                .balance(BigDecimal.ZERO)
                .dailyTransactionLimit(new BigDecimal("200000.00"))
                .build();

        Account savedAccount = accountRepository.save(account);

        log.info("Account created successfully: {}", savedAccount.getAccountNumber());

        return MapToRes

        return AccountResponse.builder()
                .id(savedAccount.getId())
                .accountNumber(savedAccount.getAccountNumber())
                .accountHolderName(savedAccount.getAccountHolderName())
                .email(savedAccount.getEmail())
                .phone(savedAccount.getPhone())
                .accountType(savedAccount.getAccountType())
                .accountStatus(savedAccount.getAccountStatus())
                .balance(savedAccount.getBalance())
                .dailyTransactionLimit(savedAccount.getDailyTransactionLimit())
                .createdAt(savedAccount.getCreatedAt())
                .build();
    }

    @Override
    public AccountResponse getAccount(String accountNumber) {
        return null;
    }

    @Override
    public BigDecimal getBalance(String accountNumber) {
        return null;
    }

    @Override
    public void blockAccount(String accountNumber) {

    }

    @Override
    public void debitAmount(String accountNumber, BigDecimal amount) {

    }

    @Override
    public void creditAmount(String accountNumber, BigDecimal amount) {

    }
}
