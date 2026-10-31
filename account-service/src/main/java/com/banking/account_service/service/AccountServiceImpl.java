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
import java.security.SecureRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private static final SecureRandom secureRandom = new SecureRandom();

    @Override
    public AccountResponse createAccount(CreateAccountRequest request) {

        log.info("Creating account for: {}", request.getEmail());

        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException(
                    "Account already exists for: " + request.getEmail()
            );
        }

        Account account = new Account();

        account.setAccountNumber(generateAccountNumber());
        account.setAccountHolderName(request.getAccountHolderName());
        account.setPhone(request.getPhone());
        account.setEmail(request.getEmail());
        account.setAccountType(request.getAccountType());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setBalance(BigDecimal.ZERO);
        account.setDailyTransactionLimit(new BigDecimal("200000.00"));

        Account savedAccount = accountRepository.save(account);

        log.info("Account created successfully: {}", savedAccount.getAccountNumber());

        return MapToResponse(savedAccount);
    }

    private String generateAccountNumber() {
        String accountNumber;
        do {
            long number = secureRandom.nextLong(1000000000000L);
//            long number = secureRandom.nextLong(100000000000L, 1000000000000L);
            accountNumber = String.format("%012d", number);
        }while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    private AccountResponse MapToResponse(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setAccountHolderName(account.getAccountHolderName());
        response.setAccountNumber(account.getAccountNumber());
        response.setEmail(account.getEmail());
        response.setPhone(account.getPhone());
        response.setAccountType(account.getAccountType());
        response.setAccountStatus(account.getAccountStatus());
        response.setBalance(account.getBalance());
        response.setDailyTransactionLimit(account.getDailyTransactionLimit());
        response.setCreatedAt(account.getCreatedAt());

        return response;

    }

    @Override
    public AccountResponse getAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()->new RuntimeException("Account not found"));
        return MapToResponse(account);
    }

    @Override
    public BigDecimal getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()->new RuntimeException("Account not found"));
        return account.getBalance();
    }

    @Override //Called by Fraud Detection service via Kafka
    public void blockAccount(String accountNumber) {
        log.info("Blocking account for: {}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()->new RuntimeException("Account not found"));
        account.setAccountStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account blocked: {}", accountNumber);
    }

    @Override //Called by Transaction Service via Kafka
    public void debitAmount(String accountNumber, BigDecimal amount) {
        log.info("Deducting account {} for account: {}",amount, accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()->new RuntimeException("Account not found"));
        if (account.getAccountStatus() == AccountStatus.INACTIVE){
            throw new RuntimeException("Account is inactive");
        }
        if (account.getBalance().compareTo(amount) < 0){
            throw new RuntimeException("Insufficient funds for account "+ accountNumber);
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        log.info("Balance Updated. new balance: {}",account.getBalance());
    }

    @Override //Called by Transaction Service via Kafka
    public void creditAmount(String accountNumber, BigDecimal amount) {
        log.info("Crediting account {} for account: {}",amount, accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()->new RuntimeException("Account not found"));
        account.setBalance(account.getBalance().add(amount));

        accountRepository.save(account);

        log.info("Balance Credited. new balance: {}",account.getBalance());

    }
}