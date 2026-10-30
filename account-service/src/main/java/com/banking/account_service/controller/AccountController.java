package com.banking.account_service.controller;

import com.banking.account_service.dto.AccountResponse;
import com.banking.account_service.dto.CreateAccountRequest;
import com.banking.account_service.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request));
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String accountNumber){
        return ResponseEntity.ok(accountService.getAccount(accountNumber));
    }

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable String accountNumber){
        return ResponseEntity.ok(accountService.getBalance(accountNumber));
    }

    @PutMapping("/{accountNumber}/block")
    public ResponseEntity<String> blockAccount(@PathVariable String accountNumber){
        accountService.blockAccount(accountNumber);
        return ResponseEntity.ok("Account blocked successfully");
    }

    /* SAGA Step-1 ----> Deduct Balance ==>  Called by Transaction Service when  transfer is initiated */
    @PutMapping("/{accountNumber}/debit")
    public ResponseEntity<String> debitAmount(@PathVariable String accountNumber, @RequestParam BigDecimal amount){
        accountService.debitAmount(accountNumber, amount);
        return ResponseEntity.ok("Amount debited successfully");
    }


    @PutMapping("/{accountNumber}/credit")
    public ResponseEntity<String> creditAmount(@PathVariable String accountNumber, @RequestParam BigDecimal amount){
        accountService.creditAmount(accountNumber, amount);
        return ResponseEntity.ok("Amount credited successfully");
    }
}
