--liquibase formatted sql

--changeset orbitamarket:payments-001
CREATE TABLE IF NOT EXISTS payments.accounts (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    balance BIGINT NOT NULL DEFAULT 0 CHECK (balance >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_payments_accounts_user UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS payments.payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    balance_after BIGINT,
    status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_payments_order UNIQUE (order_id)
);

CREATE TABLE IF NOT EXISTS payments.inbox_messages (
    id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS payments.outbox_messages (
    id UUID PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_payments_user_created
    ON payments.payments (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payments_outbox_pending
    ON payments.outbox_messages (created_at) WHERE published_at IS NULL;

--changeset orbitamarket:payments-002
COMMENT ON TABLE payments.accounts IS 'Один кошелёк геокредитов на пользователя';
COMMENT ON TABLE payments.payments IS 'Идемпотентный финансовый результат по order_id';
COMMENT ON TABLE payments.inbox_messages IS 'Дедупликация входящих Kafka-событий';
COMMENT ON TABLE payments.outbox_messages IS 'Надёжная публикация результатов оплаты';
