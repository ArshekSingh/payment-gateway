CREATE TABLE merchants (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id       VARCHAR(40)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    email           VARCHAR(190) NOT NULL,
    api_key_hash    CHAR(64)     NOT NULL,
    api_key_prefix  VARCHAR(20)  NOT NULL,
    webhook_url     VARCHAR(500),
    webhook_secret  VARCHAR(100) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT uk_merchants_public_id UNIQUE (public_id),
    CONSTRAINT uk_merchants_email UNIQUE (email),
    CONSTRAINT uk_merchants_api_key_hash UNIQUE (api_key_hash)
);

CREATE TABLE payments (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id             VARCHAR(40)  NOT NULL,
    merchant_id           BIGINT       NOT NULL,
    amount                BIGINT       NOT NULL,
    currency              CHAR(3)      NOT NULL,
    status                VARCHAR(30)  NOT NULL,
    capture_automatically BOOLEAN      NOT NULL,
    description           VARCHAR(255),
    merchant_reference    VARCHAR(100),
    idempotency_key       VARCHAR(100),
    request_fingerprint   CHAR(64)     NOT NULL,
    card_brand            VARCHAR(20),
    card_last4            CHAR(4),
    provider_reference    VARCHAR(100),
    failure_code          VARCHAR(60),
    failure_message       VARCHAR(255),
    amount_refunded       BIGINT       NOT NULL DEFAULT 0,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    authorized_at         DATETIME(6),
    captured_at           DATETIME(6),
    CONSTRAINT uk_payments_public_id UNIQUE (public_id),
    CONSTRAINT uk_payments_merchant_idempotency UNIQUE (merchant_id, idempotency_key),
    CONSTRAINT fk_payments_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id)
);

CREATE INDEX idx_payments_merchant_created ON payments (merchant_id, created_at);
CREATE INDEX idx_payments_merchant_status ON payments (merchant_id, status);
CREATE INDEX idx_payments_merchant_reference ON payments (merchant_id, merchant_reference);

CREATE TABLE refunds (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id          VARCHAR(40)  NOT NULL,
    payment_id         BIGINT       NOT NULL,
    amount             BIGINT       NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    reason             VARCHAR(255),
    idempotency_key    VARCHAR(100),
    provider_reference VARCHAR(100),
    failure_code       VARCHAR(60),
    created_at         DATETIME(6)  NOT NULL,
    CONSTRAINT uk_refunds_public_id UNIQUE (public_id),
    CONSTRAINT uk_refunds_payment_idempotency UNIQUE (payment_id, idempotency_key),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
);

CREATE INDEX idx_refunds_payment_created ON refunds (payment_id, created_at);

CREATE TABLE webhook_events (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id        VARCHAR(40)   NOT NULL,
    merchant_id      BIGINT        NOT NULL,
    event_type       VARCHAR(50)   NOT NULL,
    payload          VARCHAR(4000) NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    attempts         INT           NOT NULL DEFAULT 0,
    next_attempt_at  DATETIME(6)   NOT NULL,
    last_error       VARCHAR(500),
    last_http_status INT,
    created_at       DATETIME(6)   NOT NULL,
    delivered_at     DATETIME(6),
    CONSTRAINT uk_webhook_events_public_id UNIQUE (public_id),
    CONSTRAINT fk_webhook_events_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id)
);

CREATE INDEX idx_webhook_events_due ON webhook_events (status, next_attempt_at);
CREATE INDEX idx_webhook_events_merchant ON webhook_events (merchant_id);
