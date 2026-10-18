# payment-gateway

A payment gateway backend built with Spring Boot 3.5 (Java 17). Merchants are onboarded by an admin, then use an API key to create payments, confirm them with a card, capture, cancel and refund, and receive signed webhooks. Card processing goes through a pluggable `PaymentProvider`; a simulated provider is included so everything runs end to end without an external acquirer.

## Run

```bash
mvn spring-boot:run            # H2 in-memory, admin key "dev-admin-key-change-me"
mvn test                       # unit + integration tests
```

Production profile (MySQL 8+):

```bash
export DB_URL='jdbc:mysql://host:3306/payment_gateway' DB_USERNAME=... DB_PASSWORD=...
export GATEWAY_ADMIN_API_KEY='<long random string>'
java -jar target/payment-gateway-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod
```

Schema is managed by Flyway (`src/main/resources/db/migration`).

## API

Merchant endpoints use `Authorization: Bearer <api key>`. Admin endpoints use `X-Admin-Key`. Amounts are integers in the currency's minor unit (cents, paise). Errors share one shape: `{ "code", "message", "timestamp", "details" }`.

| Method | Path | Purpose |
|---|---|---|
| POST | `/admin/merchants` | Onboard a merchant. Returns the API key **once**, plus the webhook secret |
| GET | `/admin/merchants` | List merchants (paged) |
| GET | `/v1/merchant` | Current merchant profile |
| PUT | `/v1/merchant/webhook` | Set or clear the webhook URL |
| POST | `/v1/payments` | Create a payment (`Idempotency-Key` supported) |
| GET | `/v1/payments?status=&page=&size=` | List payments (newest first, size capped at 100) |
| GET | `/v1/payments/{id}` | Fetch a payment |
| POST | `/v1/payments/{id}/confirm` | Authorize with a card; auto-captures unless `captureAutomatically=false` |
| POST | `/v1/payments/{id}/capture` | Capture an authorized payment |
| POST | `/v1/payments/{id}/cancel` | Cancel, voiding any authorization |
| POST | `/v1/payments/{id}/refunds` | Full or partial refund (`Idempotency-Key` supported) |
| GET | `/v1/payments/{id}/refunds` | List refunds |

Payment states: `CREATED → AUTHORIZED → CAPTURED → PARTIALLY_REFUNDED / REFUNDED`, with `FAILED` (retryable by confirming again) and `CANCELLED`.

### Quick walk-through

```bash
ADMIN=dev-admin-key-change-me
KEY=$(curl -s -X POST localhost:8080/admin/merchants -H "X-Admin-Key: $ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Acme","email":"acme@example.com"}' | jq -r .apiKey)

PAY=$(curl -s -X POST localhost:8080/v1/payments -H "Authorization: Bearer $KEY" \
  -H 'Content-Type: application/json' -d '{"amount":2500,"currency":"USD"}' | jq -r .id)

curl -s -X POST localhost:8080/v1/payments/$PAY/confirm -H "Authorization: Bearer $KEY" \
  -H 'Content-Type: application/json' \
  -d '{"card":{"number":"4242424242424242","expMonth":12,"expYear":2030,"cvc":"123"}}'
```

### Simulated provider test cards

| Card | Result |
|---|---|
| `4242424242424242` (any valid Luhn number) | Approved |
| `4000000000000002` | `card_declined` |
| `4000000000009995` | `insufficient_funds` |
| `4000000000000069` | `expired_card` |
| `4000000000000119` | Provider outage (HTTP 502, state unchanged) |

## Webhooks

Events: `payment.authorized`, `payment.captured`, `payment.failed`, `payment.cancelled`, `refund.succeeded`, `refund.failed`.

Events are written to an outbox table in the same transaction as the change, then delivered by a scheduler (claim with `SKIP LOCKED`, deliver, record outcome) with exponential backoff and a retry limit. Each request carries:

- `X-Gateway-Event-Id`: event id, use it to de-duplicate (delivery is at-least-once)
- `X-Gateway-Signature: t=<unix seconds>,v1=<hex>` where `v1 = HMAC_SHA256(webhook_secret, "<t>.<raw body>")`. Verify it and reject old timestamps.

Webhook URLs must be `https` and must not resolve to private or loopback addresses. For local development set `GATEWAY_WEBHOOK_ALLOW_PRIVATE_TARGETS=true`.

## Design notes

- **Security:** API keys are stored as SHA-256 hashes; raw card data is never persisted or logged (only brand and last4). The app is stateless, with no sessions or cookies.
- **Concurrency:** confirm, capture, cancel and refund take a row lock on the payment, and `@Version` guards the rest.
- **Idempotency:** create payment and create refund accept `Idempotency-Key`. Same key with the same parameters replays the original response; different parameters return 422.

## Before real money

- Replace `SimulatedPaymentProvider` with a real PSP implementation of `PaymentProvider`. Handling raw card numbers puts you in PCI DSS scope, so prefer hosted fields or tokenization from the PSP.
- The provider call currently runs inside the DB transaction. With a real PSP, add a `PENDING` state and call it outside the transaction.
- Add rate limiting, API-key rotation, encryption at rest for webhook secrets, and an audit log.
