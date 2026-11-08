# Roadmap and Integrations

This is a proposed roadmap based on the current repository and the requested web/mobile direction. None of the frontend items below are implemented yet. Prioritize correctness and service contracts before adding clients.

## Phase 1 — Make backend workflows reliable

- Align gateway routes with the versioned account and transaction API paths; add route tests.
- Define and test the transfer state machine, including idempotency, duplicate requests, failures, and compensation.
- Complete the fraud result and verification flows. Register the intended listeners, implement OTP verification and expiry behavior, and ensure notification delivery has a defined contract.
- Correct event topic mismatches and add versioned event schemas, retry/dead-letter behavior, and consumer idempotency.
- Apply account creation's initial deposit consistently with the API contract.
- Add service-level tests for insufficient funds, concurrent debit, event publication failure, redelivery, and rollback/compensation.

## Phase 2 — Establish production foundations

- Replace development credentials and local-only settings with environment-specific configuration and secret management.
- Add database migrations, service readiness checks, structured correlation IDs, and metrics/traces/logging.
- Add authentication and role-based authorization for customers, support, and administrative actions.
- Define API and event compatibility policies, retention, backup/recovery, and deployment practices.
- Add repeatable local development infrastructure and CI checks.

## Phase 3 — Web application

The repository currently has no web client. Select one primary web framework rather than maintaining two clients without a product or team need:

### Option A: React

- Build a TypeScript single-page application for registration/account views, balances, transaction history, and transfer initiation.
- Use the API gateway as the only backend entry point.
- Add accessible forms, clear pending/completed/failed transaction states, secure session handling, and generated/maintained API types.

### Option B: Angular

- Build a TypeScript application using Angular's routing, forms, and HTTP facilities for the same customer workflows.
- Use the same gateway contracts, accessibility requirements, and security model as the React option.

The current repository does not establish a preference between React and Angular. Choose based on team skills, product needs, and client support requirements before scaffolding a frontend.

## Phase 4 — Mobile application

- Evaluate React Native for iOS and Android after the public API contract and authentication model are stable.
- Reuse API types and domain-level validation where appropriate, but keep UI and platform-specific security behavior appropriate to mobile.
- Use platform secure storage for credentials/tokens; do not store sensitive payment or OTP data in ordinary application preferences.
- Consider React Native Web only if shared web/mobile UI is an explicit product goal; do not assume it replaces a deliberate web-framework decision.

## Planned integrations

| Integration | Repository status | Follow-up work |
|---|---|---|
| PostgreSQL | Configured for account, transaction, and payment services | Add migrations, isolated local setup, backup/retention, and deployment configuration |
| Apache Kafka | Producers/consumers exist for portions of transaction and fraud flow | Stabilize topic names and schemas; complete consumers, retries, dead-letter handling, and idempotency |
| Redis | Used for fraud velocity; OTP storage code is incomplete/unwired | Define key format, expiry, failure behavior, and production availability requirements |
| Razorpay | Java dependency and environment-backed properties are configured | Implement payment API and webhook verification, idempotency, reconciliation, and safe secret handling |
| SMTP email | Mail properties are configured for notification service | Implement event consumption, templates, delivery failure/retry behavior, and privacy-safe logging |
| Authentication provider | Not present | Select an identity approach, define token validation and roles, and enforce authorization in services/gateway |
| CI/CD and deployment | No pipeline or deployment manifests identified | Add build/test pipeline, container/deployment strategy, config/secrets management, and release process |

## Suggested acceptance criteria

Before presenting a feature as complete:

1. Its API/event contract and failure behavior are documented.
2. Automated tests cover normal behavior and important failure cases.
3. Secrets and sensitive data are handled safely.
4. Logs/metrics identify the workflow without exposing sensitive values.
5. Gateway routes, client contracts, and service behavior are verified together.
6. Documentation reflects what is actually deployed, not only the intended design.
