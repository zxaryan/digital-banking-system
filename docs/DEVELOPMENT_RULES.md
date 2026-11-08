# Development Rules

These rules are proposed project conventions. They are not all enforced by current CI or tooling.

## 1. Keep service ownership clear

- A service owns its domain behavior and data. Other services must use a documented API or versioned event instead of reading or writing its database directly.
- Keep each service independently buildable and testable. Avoid sharing persistence entities across services.
- Put cross-service calls behind a typed client and define timeouts and failure behavior; do not make a remote call look like a local database operation.

## 2. Protect money and transaction state

- Represent amounts with `BigDecimal`, use an explicit currency and scale, and never use `float` or `double` for financial values.
- Validate positive amounts, currency, account state, ownership, and applicable balance/limit rules at the service boundary and again where required by domain invariants.
- Use database transactions for local state changes. Do not assume a database transaction spans HTTP calls or Kafka.
- Transfers must be idempotent and recoverable. Define an idempotency key, explicit state transitions, and compensating actions before claiming transfer completion.
- Record financial movements in an auditable ledger rather than relying only on a mutable balance. Reconcile balance updates and ledger entries atomically.
- Make completion, failure, cancellation, and verification states explicit. Never return success for a pending or failed operation.

## 3. Treat APIs as contracts

- Keep external endpoints versioned and document request, response, error, and pagination behavior.
- Validate request bodies with Jakarta Validation and return consistent, meaningful error responses. Do not expose stack traces or internal exception details.
- Use typed DTOs rather than generic maps for stable inter-service messages and HTTP payloads.
- Keep gateway route predicates aligned with the actual controller paths. Add route tests whenever a public route changes.
- Add authentication and authorization checks before exposing account, balance, transfer, or administrative operations.

## 4. Treat Kafka events as durable contracts

- Give every event a documented name, schema/version, producer, consumer, keying strategy, and compatibility policy.
- Include stable event and correlation identifiers; design consumers to handle duplicate delivery safely.
- Configure retries, dead-letter handling, and observability. Do not swallow processing errors or acknowledge events before required state changes are durable.
- Use an outbox or equivalent reliable publication pattern when a database update and event publication must stay consistent.
- Keep topic names consistent across producers and consumers; add contract tests to prevent misspellings and schema drift.

## 5. Handle credentials and personal information safely

- Never commit real passwords, API keys, tokens, private keys, or customer data. Use environment variables for local development and a secret manager in deployed environments.
- Do not log OTPs, credentials, full payment details, or unnecessary personal/account data. Redact sensitive values in logs and test fixtures.
- Generate security-sensitive values with a cryptographically secure random generator, store them with a short expiry, and compare them safely.
- Keep health and management endpoints minimally exposed; separate liveness/readiness from operational details.

## 6. Persistence and schema changes

- Use database constraints for critical uniqueness and integrity rules; handle concurrent requests rather than relying only on pre-checks.
- Use reviewed, versioned schema migrations for shared or deployed environments. Do not rely on Hibernate auto-update outside disposable local development.
- Define indexes and retention rules for transaction history, event records, and audit data.
- Keep persistence transactions short and avoid holding database locks across network calls.

## 7. Tests and quality gates

- Add unit tests for domain rules and service tests for persistence and integrations.
- Add API validation/error tests, gateway route tests, Kafka contract/consumer tests, and integration tests for transfer success and failure/compensation.
- Test boundary conditions: duplicate requests, concurrent debits, insufficient funds, timeouts, redelivered events, expired OTPs, and service outages.
- Keep tests deterministic; use controlled clocks, generated test data, and isolated infrastructure rather than shared external accounts or real credentials.
- Before merging, run the affected service's `./gradlew test`; run all services' tests when changing shared contracts or cross-service behavior.
- Treat compiler warnings, failing tests, and unhandled errors as issues to resolve, not as expected success.

## 8. Frontend and integration conventions

- Do not treat proposed React, Angular, or React Native clients as existing code until their applications, build configuration, and API contracts are added to the repository.
- A web or mobile client must use the public gateway API, not connect directly to service databases or internal service URLs.
- Keep client-side validation for usability, but enforce all security and business rules in backend services.
- Generate or maintain typed client models from a reviewed API contract where practical; never duplicate authorization decisions in client code.

