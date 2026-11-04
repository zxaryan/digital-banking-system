package com.banking.transaction_service.service;

import com.banking.transaction_service.entity.Transaction;
import com.banking.transaction_service.entity.TransactionStatus;
import com.banking.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionEventConsumer {
    private final TransactionRepository transactionRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private static final long OTP_EXPIRES_MINUTES = 5;
    public void consumeVerificationRequired(@Payload Map<String, Object> payload){
        try {
            String transactionId = (String) payload.get("transactionId");
            String accountNumber = (String) payload.get("accountNumber");
            String reason = (String) payload.get("reason");

            log.info("Verification required - transaction: {} reason: {}",transactionId, reason);

            Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(()-> new RuntimeException("Transaction not found "+ transactionId));

            if (transaction.getStatus() != TransactionStatus.PROCESSING){
                log.warn("Transaction {} not PROCESSING - skipping", transactionId);
                return;
            }

            // Generate 6 digit OTP
            String otp = String.format("%06d", (int) (Math.random() * 900000) + 100000);

            // Store otp in Redis - expires in 5 min
            String optKey = "verification:otp" + transactionId;
            redisTemplate.opsForValue().set(optKey, otp, OTP_EXPIRES_MINUTES);

            // Update status
            transaction.setStatus(TransactionStatus.PENDING);
            transactionRepository.save(transaction);

            log.info("OTP generated for transaction: {} expires in {} min", transactionId, OTP_EXPIRES_MINUTES);

            // Notify user


        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
