package com.banking.transaction_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

//Redis - cache work here

@FeignClient(name = "account-service", url = "${account.service.url}")
public interface AccountServiceClient {
    @PutMapping("/api/v1/accounts/{accountNumber}/debit")
    String debitAmount(@PathVariable String accountNumber, @RequestParam BigDecimal amount);

}
