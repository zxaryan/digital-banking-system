package com.banking.fraud_detection_service.service;

import java.util.Map;

public interface FraudDetectionService {
     void checkTransaction(Map<String, Object> payload);
}
