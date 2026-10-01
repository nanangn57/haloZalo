CREATE TABLE IF NOT EXISTS accounts (
    user_id VARCHAR(36) NOT NULL,
    username VARCHAR(32) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY accounts_username (username),
    UNIQUE KEY accounts_email (email)
);

CREATE TABLE IF NOT EXISTS sessions (
    session_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (session_id),
    CONSTRAINT sessions_user_id FOREIGN KEY (user_id) REFERENCES accounts (user_id)
);
