package com.banking.transaction_service.service;

import com.banking.transaction_service.client.AccountServiceClient;
import com.banking.transaction_service.dto.TransferRequest;
import com.banking.transaction_service.dto.TransactionResponse;
import com.banking.transaction_service.entity.Transaction;
import com.banking.transaction_service.entity.TransactionStatus;
import com.banking.transaction_service.entity.TransactionType;
import com.banking.transaction_service.event.TransactionCompletedEvent;
import com.banking.transaction_service.event.TransactionInitiatedEvent;
import com.banking.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String,String> redisTemplate;

    private static final String TRANSACTION_INITIATED_TOPIC="transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC="transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC="transaction.refunded";
    private static final String FRAUD_DETECTED_TOPIC="fraud.detected";

    @Override
    public TransactionResponse transfer(TransferRequest request) {

        // 1. Create transaction as PENDING
        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(request.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(request.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());

        // 2. Save transaction before remote debit
        Transaction savedTransaction = transactionRepository.save(transaction);

        // 3. Debit sender account
        accountServiceClient.debitAmount(
                savedTransaction.getSenderAccountNumber(),
                savedTransaction.getAmount()
        );

        // 4. Update transaction status
        savedTransaction.setStatus(TransactionStatus.PROCESSING);
        savedTransaction = transactionRepository.save(savedTransaction);

        // 5. Publish Kafka event
        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                savedTransaction.getId(),
                savedTransaction.getSenderAccountNumber(),
                savedTransaction.getReceiverAccountNumber(),
                savedTransaction.getAmount(),
                savedTransaction.getDescription()
        );

        kafkaTemplate.send(
                TRANSACTION_INITIATED_TOPIC,
                savedTransaction.getId(),
                event
        );

        return mapToResponse(savedTransaction);
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.setId(transaction.getId());
        response.setSenderAccountNumber(transaction.getSenderAccountNumber());
        response.setReceiverAccountNumber(transaction.getReceiverAccountNumber());
        response.setAmount(transaction.getAmount());
        response.setDescription(transaction.getDescription());
        response.setReferenceNumber(transaction.getReferenceNumber());
        response.setFailureReason(transaction.getFailureReason());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setCompletedAt(transaction.getCompletedAt());
        return response;
    }

    @Override
    public TransactionResponse getTransaction(String transactionId) {
        return mapToResponse(transactionRepository.findById(transactionId)
               .orElseThrow(()->new RuntimeException("Transaction not found: " + transactionId)));
    }

    @Override
    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        return transactionRepository.findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
               .stream()
               .map(this::mapToResponse)
               .collect(Collectors.toList());
    }

    @Override
    public TransactionResponse verifyOTP(String transactionId, String otp) {
        log.info("OTP verification for: {}", transactionId);
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(()->new RuntimeException("Transaction not found: "+ transactionId));
        String otpKey = "verification:otp" + transactionId;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);
        if (storedOtp==null){
            log.warn("OTP expired for transaction: {}", transactionId);
            compensateTransaction(transaction, "OTP expired - transaction cancelled - amount refunded");
            return mapToResponse(transaction);
        }
        if (!storedOtp.equals(otp)){
            // Block account and refund
            log.warn("Wrong OTP - blocking account - refunding: {}", transactionId);
            redisTemplate.delete(otpKey);
            blockAccountAndCompensate(transaction, "Wrong otp entered - transaction cancelled - account blocked for security");
            return mapToResponse(transaction);
        }
        // OTP correct - complete transaction
        log.info("OTP verified - completing transaction: {}", transactionId);
        redisTemplate.delete(otpKey);
        completeTransaction(transaction,"");
        return mapToResponse(transaction);
    }

    @Override
    public void processCleanResult(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(()->new RuntimeException("Transaction not found "+ transactionId));
        if (transaction.getStatus() != TransactionStatus.PROCESSING){
            log.warn("Transaction {} not PROCESSING - skipping", transactionId);
            return;
        }
        completeTransaction(transaction, "");
    }

    private void completeTransaction(Transaction transaction, String reason) {
        log.warn("SAGA COMPENSATION - refunding: {} amount: {}", transaction.getSenderAccountNumber(), transaction.getAmount());

        // Credit money back to sender synchronously
        accountServiceClient.creditAmount(transaction.getSenderAccountNumber(), transaction.getAmount());
        transaction.setStatus(TransactionStatus.FLAGGED);
        transaction.setFailureReason(reason+ "SAGA COMPENSATION EXECUTED - amount refunded at" + LocalDateTime.now());
        transactionRepository.save(transaction);

        // Publish refund event - Notification service will alert user
        Map<String, Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId",transaction.getId());
        refundEvent.put("senderAccountNumber",transaction.getSenderAccountNumber());
        refundEvent.put("amount",transaction.getAmount());
        refundEvent.put("reason",reason);

        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC, transaction.getId(), refundEvent);
        log.info("SAGA COMPENSATION COMPLETE: {} refunded to: {}", transaction.getAmount(), transaction.getSenderAccountNumber());
    }

    private void blockAccountAndCompensate(Transaction transaction, String reason) {
        // Publish fraud detection event - account service will block account
        Map<String, Object> fraudEvent = new HashMap<>();

        fraudEvent.put("transactionId",transaction.getId());
        fraudEvent.put("senderAccountNumber",transaction.getSenderAccountNumber());
        fraudEvent.put("reason",reason);

        kafkaTemplate.send(FRAUD_DETECTED_TOPIC, transaction.getSenderAccountNumber(), fraudEvent);
        log.warn("fraud detected published - account: {} will be blocked, kindly contact to your bank", transaction.getSenderAccountNumber());

        // SAGA COMPENSATION - refund sender
        compensateTransaction(transaction, reason);
    }

    private void compensateTransaction(Transaction transaction, String s) {
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        TransactionCompletedEvent completedEvent = new TransactionCompletedEvent(
                transaction.getId(),
                transaction.getSenderAccountNumber(),
                transaction.getReceiverAccountNumber(),
                transaction.getAmount(),
                transaction.getDescription()
        );
        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC,transaction.getId(), completedEvent);
        log.info("SAGA complete - transaction completed: {}",transaction.getId());
    }
}