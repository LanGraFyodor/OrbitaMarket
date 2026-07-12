--liquibase formatted sql

--changeset orbitamarket:notifications-001
CREATE TABLE IF NOT EXISTS notifications.notifications (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    order_id UUID,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(140) NOT NULL,
    message VARCHAR(500) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_notifications_event UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON notifications.notifications (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread
    ON notifications.notifications (user_id, created_at DESC) WHERE is_read = FALSE;
CREATE INDEX IF NOT EXISTS idx_notifications_order
    ON notifications.notifications (order_id);

--changeset orbitamarket:notifications-002
COMMENT ON TABLE notifications.notifications IS 'Идемпотентная история результатов оплаты для SSE-центра';
