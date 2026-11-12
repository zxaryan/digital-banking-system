# Digital Banking System - Microservices

A Java and Spring Boot banking backend organized as independently built services. The repository currently contains account, transaction, fraud-detection, payment, notification, and API gateway modules. It does not currently contain a web or mobile frontend.

> **Project status:** This is an early-stage implementation, not a production-ready banking platform. Several service flows and gateway routes are incomplete or inconsistent; see [Architecture](ARCHITECTURE.md) before relying on the described workflows.

## Services

| Module | Local port | Current responsibility |
|---|---:|---|
| `api-gateway` | 8080 | Spring Cloud Gateway routes configured for backend services |
| `account-service` | 8081 | Account creation, account lookup, balance, debit, credit, and block operations |
| `transaction-service` | 8082 | Transfer request and transaction lookup/history endpoints; publishes transaction events |
| `payment-service` | 8083 | Spring Boot service scaffold with Razorpay configuration; payment operations are not implemented |
| `fraud-detection-service` | 8084 | Consumes initiated-transaction events and applies a Redis-backed velocity check |
| `notification-service` | 8085 | Spring Boot and mail configuration scaffold; notification handling is not implemented |

## Technology

- Java 25 and Gradle wrapper per service
- Spring Boot 4.1.2 and Spring Cloud components
- PostgreSQL with Spring Data JPA
- Apache Kafka for asynchronous events
- Redis for fraud-velocity state and planned OTP storage
- OpenFeign for synchronous service-to-service HTTP calls

## Local development

### Prerequisites

- JDK 25
- PostgreSQL available at `localhost:5433`, with `account_db`, `transaction_db`, and `payment_db`
- Kafka available at `localhost:9092`
- Redis available at `localhost:6379`
- SMTP credentials for the notification service, and Razorpay credentials if implementing payment integration

No Docker Compose or other local infrastructure definition is currently included. Configure infrastructure and credentials outside the repository. Some service configuration still contains development database settings; replace those with environment-based configuration before sharing or deploying the application.

### Run a service

Run each command from its service directory, in a separate terminal:

```bash
cd account-service
./gradlew bootRun
```

Replace `account-service` with `api-gateway`, `transaction-service`, `payment-service`, `fraud-detection-service`, or `notification-service` to start another module. Service startup requires its configured dependencies.

### Run tests

From a service directory:

```bash
./gradlew test
```

The current tests are primarily Spring application-context smoke tests; they do not establish end-to-end banking workflow correctness.

## API overview

The implemented controller paths are versioned:

- Accounts: `/api/v1/accounts`
- Transactions: `/api/v1/transactions`

For the current operations and request behavior, inspect the account and transaction controllers. **The gateway is currently configured with different path prefixes** (`/api/accounts/**` and `/api/transactions/**`), so these controller paths are not yet exposed through matching gateway routes.

## Documentation

- [Architecture and current implementation gaps](ARCHITECTURE.md)
- [Development and contribution rules](DEVELOPMENT_RULES.md)
- [Roadmap and frontend/integration options](ROADMAP_AND_INTEGRATIONS.md)

## Important

This repository is not a substitute for a reviewed banking system. Do not use it with real customer data, real funds, or production credentials. Authentication, authorization, operational safeguards, and reliable transaction orchestration need to be designed and verified before production use.
