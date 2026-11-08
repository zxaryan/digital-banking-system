# Architecture

## Current shape

The repository is a set of independently built Spring Boot applications, each with its own Gradle build. The account, transaction, and payment modules are configured for separate PostgreSQL databases. Kafka is configured for asynchronous messaging, Redis is used by fraud detection, and the transaction and fraud services use OpenFeign to call the account service.

```text
Clients
   |
   v
API Gateway (:8080) -- configured routes --> service URLs (:8081-8085)
                                                  |
                +---------------------------------+-----------------+
                |                 |               |                 |
           Account service   Transaction       Fraud detection   Payment / notification
           PostgreSQL        service            Redis + Kafka     configuration scaffolds
                             PostgreSQL
                ^                 |
                |                 +---- transaction.initiated ----> Fraud detection
                +------ HTTP debit (Feign)
```

The diagram shows configured dependencies and the intended high-level flow; it does not imply that every configured route or event workflow currently works end to end.

## Service inventory

| Service | Implemented surface | Persistence and integrations |
|---|---|---|
| API gateway | Route configuration and Actuator endpoints | Spring Cloud Gateway; Redis properties are configured |
| Account | Create and retrieve accounts, read balance, block, debit, and credit | PostgreSQL, JPA, Kafka listeners |
| Transaction | Initiate transfer, fetch transaction, list sender history, OTP verification endpoint | PostgreSQL, Kafka producer, Feign account client, Redis configuration |
| Fraud detection | Consume transaction-initiated events and apply a velocity check | Redis, Kafka producer/consumer, Feign account client |
| Payment | Application bootstrap only | PostgreSQL and Razorpay properties/dependency are configured; no payment workflow or API is implemented |
| Notification | Application bootstrap only | Kafka and SMTP properties are configured; no notification consumer or sending workflow is implemented |

## Current API endpoints

### Account service (`:8081`)

Base path: `/api/v1/accounts`

| Method | Path | Behavior |
|---|---|---|
| `POST` | `/` | Create an account |
| `GET` | `/{accountNumber}` | Retrieve an account |
| `GET` | `/{accountNumber}/balance` | Retrieve the balance |
| `PUT` | `/{accountNumber}/block` | Block an account |
| `PUT` | `/{accountNumber}/debit?amount=...` | Debit an account |
| `PUT` | `/{accountNumber}/credit?amount=...` | Credit an account |

### Transaction service (`:8082`)

Base path: `/api/v1/transactions`

| Method | Path | Behavior |
|---|---|---|
| `POST` | `/transfer` | Persist a transaction, call account debit, and publish `transaction.initiated` |
| `GET` | `/{transactionId}` | Retrieve a transaction |
| `GET` | `/account/{accountNumber}` | List transactions sent from an account |
| `POST` | `/{transactionId}/verify?otp=...` | Verification endpoint exists, but its service implementation currently returns `null` |

## Event and transfer behavior

The implemented transfer path in `transaction-service` saves a transaction, synchronously requests a debit from `account-service`, then publishes a `transaction.initiated` event. `fraud-detection-service` consumes that topic and currently performs a Redis-backed transaction-velocity check. Depending on the result, it publishes either `verification.required` or `fraud.check.clean`.

The codebase does **not** currently complete a reliable transfer saga: there is no verified end-to-end completion path, compensation handling, or durable event/outbox mechanism. Treat the topic names below as a view of current code, not as a stable event contract.

| Topic | Current code behavior |
|---|---|
| `transaction.initiated` | Published by transaction service; consumed by fraud detection |
| `verification.required` | Published by fraud detection; transaction consumer method exists but is not registered with `@KafkaListener` |
| `fraud.check.clean` | Published by fraud detection; no matching consumer is currently implemented |
| `transcation.completed` | Account service listens on this misspelled topic to credit a receiver; no producer for this topic is present |
| `fraud.detected` | Account service listens for this event to block an account; no producer is present in the inspected implementation |
| `transaction.completed`, `transaction.refunded` | Topic constants exist in transaction service, but no publishing flow is implemented for them |

## Important implementation gaps

These are documentation findings from the current source, not a complete code or security audit:

1. **Gateway/controller path mismatch:** the gateway uses `/api/accounts/**` and `/api/transactions/**`, while the controllers use `/api/v1/accounts/**` and `/api/v1/transactions/**`. Verify and align predicates and path rewriting before routing client traffic.
2. **Transfer orchestration is incomplete:** a debit can succeed before a later Kafka publish fails, and there is no compensation or outbox. Receiver credit and transaction completion are not wired into the initiated flow.
3. **OTP flow is incomplete:** the verification-required consumer is not registered as a Kafka listener, OTP verification returns `null`, and the account notification step is only a placeholder.
4. **Account creation does not apply the requested initial deposit:** the DTO requires `initialDeposit`, but the service initializes the saved balance to zero.
5. **Account event topic mismatch:** the consumer subscribes to `transcation.completed` (misspelled); the transaction service declares a different completion topic and does not publish it.
6. **Fraud threshold injection needs correction:** `FraudDetectionServiceImpl` uses `@Value("{fraud.max-transaction-per-minute}")` rather than Spring's `${...}` placeholder syntax. Verify startup and injection behavior after fixing it.
7. **Payment and notification are scaffolds:** configured libraries and properties do not yet implement payment APIs, webhook validation, Kafka consumers, or message delivery.
8. **Gateway routing and health checks need tests:** current tests are context-load smoke tests, not route, event, or business-flow tests.

## Configuration and operations

The service YAML files currently assume local dependency addresses and expose different Actuator endpoint sets. Account and transaction configurations include development database credentials, and JPA schema auto-update and SQL logging are enabled. These are development choices, not production configuration.

Before deploying, move secrets to a managed secret store or environment-based configuration, use reviewed schema migrations, restrict Actuator endpoints, add readiness/dependency checks, and define deployment and observability standards. No deployment manifests or shared local infrastructure orchestration are currently included.

