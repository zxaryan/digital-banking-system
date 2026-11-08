package com.banking.fraud_detection_service.service;

import com.banking.fraud_detection_service.client.AccountServiceClient;
import com.banking.fraud_detection_service.entity.FraudCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionServiceImpl implements FraudDetectionService {
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${fraud.max-transaction-per-minute}")
    private int maxTransactionsPerMinute;

    private static final String VERIFICATION_REQUIRED_TOPIC = "verification.required";
    private static final String FRAUD_CHECK_CLEAN_RESULT_TOPIC = "fraud.check.clean";
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void checkTransaction(Map<String, Object> payload) {
        String transactionId = (String) payload.get("transactionId");
        String accountNumber = (String) payload.get("senderAccountNumber");
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());

//      fetch real balance from account service via feignClient
        BigDecimal senderBalance = accountServiceClient.getBalance(accountNumber);
        log.info("Checking transaction: {} account: {} amount: {} balance: {}",transactionId, accountNumber, amount, senderBalance);

        FraudCheckResult result = performFraudChecks(accountNumber, amount, senderBalance);

        if (result.isFraud()){
            log.info("Suspicious activity detected - account: {}" + "reason: {} - requesting OTP verification" , accountNumber, result.getReason());
            Map<String, Object> verificationEvent = new HashMap<>();
            verificationEvent.put("transactionId", transactionId);
            verificationEvent.put("accountNumber", accountNumber);
            verificationEvent.put("amount", amount);
            verificationEvent.put("reason",result.getReason());

            kafkaTemplate.send(VERIFICATION_REQUIRED_TOPIC, transactionId, verificationEvent);
        } else{
            log.info("Transaction clean");
            Map<String, Object> transactionCleanEvent = new HashMap<>();
            transactionCleanEvent.put("transactionId", transactionId);
            transactionCleanEvent.put("isFraud", false);
            transactionCleanEvent.put("reason", null);

            kafkaTemplate.send(FRAUD_CHECK_CLEAN_RESULT_TOPIC, transactionId, transactionCleanEvent);


        }

    }

    private FraudCheckResult performFraudChecks(String accountNumber, BigDecimal amount, BigDecimal senderBalance) {
        // 1. Velocity check
        if (isVelocityExceeded(accountNumber)){
            return new FraudCheckResult(true, "Too many transactions in 60 seconds" + "- velocity limit exceeded");
        }
//        // 1. Amount check
//        if (isAmountSuspicious(accountNumber, amount)){
//            return new FraudCheckResult(true, "Unusual transaction amount" + "- exceeds 5x of your average");
//        }
//        // 1. Balance check
//        if (senderBalance.compareTo(BigDecimal.ZERO)>0 && isBalanceFailded(senderBalance, amount) ) {
//            return new FraudCheckResult(true, "Transaction exceeds 90% of your account balance");
//        }
        return new FraudCheckResult(false, null);
    }

    private boolean isVelocityExceeded(String accountNumber) {
        String key = "fraud:velocity" + accountNumber;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1){
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }
        log.info("Velocity Check - account : {} count: {}/{}",accountNumber, count,maxTransactionsPerMinute);
        return count != null && count > maxTransactionsPerMinute;
    }
}