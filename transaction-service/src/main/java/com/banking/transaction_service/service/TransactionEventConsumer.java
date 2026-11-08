package com.banking.transaction_service.service;

import com.banking.transaction_service.entity.Transaction;
import com.banking.transaction_service.entity.TransactionStatus;
import com.banking.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionEventConsumer {
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final long OTP_EXPIRES_MINUTES = 5;

    private static final String TRANSACTION_OTP_GENERATED_TOPIC = "transaction.otp.generated";

    @KafkaListener(topics = "verification.required")
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
            String otpKey = "verification:otp" + transactionId;
            redisTemplate.opsForValue().set(otpKey, otp, OTP_EXPIRES_MINUTES);

            // Update status
            transaction.setStatus(TransactionStatus.PENDING);
            transactionRepository.save(transaction);

            log.info("OTP generated for transaction: {} expires in {} min", transactionId, OTP_EXPIRES_MINUTES);

            // Notify user
            Map<String, Object> otpEvent = new HashMap<>();
            otpEvent.put("transactionId",transactionId);
            otpEvent.put("accountNumber",accountNumber);
            otpEvent.put("reason",reason);
            otpEvent.put("otp",otp);
            otpEvent.put("amount",payload.get("amount"));
            kafkaTemplate.send(TRANSACTION_OTP_GENERATED_TOPIC, transactionId, otpEvent);

        } catch (Exception e) {
            log.error("Error handling verification required {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "fraud.check.clean")
    public void consumeFraudCheckCleanResult(@Payload Map<String, Object> payload){
        try {
            String transactionId = (String) payload.get("transactionId");
            transactionService.processCleanResult(transactionId);

        } catch (Exception e) {
            log.error("Error processing fraud check result: {}", e.getMessage());
        }

    }
}
