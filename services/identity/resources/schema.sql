CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE IF NOT EXISTS identity.accounts (
    user_id VARCHAR(36) NOT NULL,
    username VARCHAR(32) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT accounts_username UNIQUE (username),
    CONSTRAINT accounts_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS identity.sessions (
    session_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    expires_at TIMESTAMPTZ(6) NOT NULL,
    PRIMARY KEY (session_id),
    CONSTRAINT sessions_user_id FOREIGN KEY (user_id) REFERENCES identity.accounts (user_id)
);
