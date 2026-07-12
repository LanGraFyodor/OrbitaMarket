--liquibase formatted sql

--changeset orbitamarket:orders-001
CREATE TABLE IF NOT EXISTS orders.orders (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    type VARCHAR(32),
    payload TEXT NOT NULL,
    price BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS orders.inbox_messages (
    id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS orders.outbox_messages (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    topic VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT fk_orders_outbox_order FOREIGN KEY (order_id) REFERENCES orders.orders(id)
);

CREATE INDEX IF NOT EXISTS idx_orders_user_created
    ON orders.orders (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_user_status
    ON orders.orders (user_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_outbox_pending
    ON orders.outbox_messages (created_at) WHERE published_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_orders_outbox_order
    ON orders.outbox_messages (order_id);

--changeset orbitamarket:orders-002
COMMENT ON TABLE orders.orders IS 'Жизненный цикл ARCHIVE/TASKING/MONITORING';
COMMENT ON TABLE orders.inbox_messages IS 'Дедупликация результатов оплаты';
COMMENT ON TABLE orders.outbox_messages IS 'Надёжная публикация команд оплаты';
