--liquibase formatted sql

--changeset orbitamarket:auth-001
CREATE TABLE IF NOT EXISTS auth.users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    job_title VARCHAR(100),
    company VARCHAR(120),
    phone VARCHAR(30),
    bio VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_auth_users_email UNIQUE (email)
);

CREATE INDEX IF NOT EXISTS idx_auth_users_updated_at ON auth.users (updated_at DESC);

--changeset orbitamarket:auth-002
COMMENT ON TABLE auth.users IS 'Учётные записи, BCrypt hash и пользовательский профиль';
