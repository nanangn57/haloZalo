CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE IF NOT EXISTS identity.accounts (
    user_id VARCHAR(36) NOT NULL,
    username VARCHAR(32) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ(6) NOT NULL,
    platform_role VARCHAR(16) NOT NULL DEFAULT 'USER',
    banned_at TIMESTAMPTZ(6),
    PRIMARY KEY (user_id),
    CONSTRAINT accounts_username UNIQUE (username),
    CONSTRAINT accounts_email UNIQUE (email)
);

ALTER TABLE identity.accounts
    ADD COLUMN IF NOT EXISTS platform_role VARCHAR(16) NOT NULL DEFAULT 'USER';

ALTER TABLE identity.accounts
    ADD COLUMN IF NOT EXISTS banned_at TIMESTAMPTZ(6);

CREATE TABLE IF NOT EXISTS identity.verifications (
    token VARCHAR(36) NOT NULL,
    username VARCHAR(32) NOT NULL,
    email VARCHAR(254) NOT NULL,
    display_name VARCHAR(64),
    password_hash VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ(6) NOT NULL,
    expires_at TIMESTAMPTZ(6) NOT NULL,
    PRIMARY KEY (token),
    CONSTRAINT verifications_username UNIQUE (username),
    CONSTRAINT verifications_email UNIQUE (email)
);

ALTER TABLE identity.verifications
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(64);

CREATE TABLE IF NOT EXISTS identity.password_resets (
    token VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMPTZ(6) NOT NULL,
    expires_at TIMESTAMPTZ(6) NOT NULL,
    PRIMARY KEY (token)
);

CREATE TABLE IF NOT EXISTS identity.sessions (
    session_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMPTZ(6) NOT NULL,
    expires_at TIMESTAMPTZ(6) NOT NULL,
    device_id VARCHAR(64),
    device_label VARCHAR(128),
    user_agent VARCHAR(512),
    last_seen_at TIMESTAMPTZ(6),
    PRIMARY KEY (session_id),
    CONSTRAINT sessions_user_id FOREIGN KEY (user_id) REFERENCES identity.accounts (user_id)
);

ALTER TABLE identity.sessions
    ADD COLUMN IF NOT EXISTS device_id VARCHAR(64);
ALTER TABLE identity.sessions
    ADD COLUMN IF NOT EXISTS device_label VARCHAR(128);
ALTER TABLE identity.sessions
    ADD COLUMN IF NOT EXISTS user_agent VARCHAR(512);
ALTER TABLE identity.sessions
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ(6);

CREATE UNIQUE INDEX IF NOT EXISTS sessions_user_device
    ON identity.sessions (user_id, device_id)
    WHERE device_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS identity.groups (
    group_id VARCHAR(36) NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(280),
    created_at TIMESTAMPTZ(6) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    avatar VARCHAR(2048),
    PRIMARY KEY (group_id)
);

ALTER TABLE identity.groups
    ALTER COLUMN description TYPE VARCHAR(280);
ALTER TABLE identity.groups
    ADD COLUMN IF NOT EXISTS avatar VARCHAR(2048);

CREATE TABLE IF NOT EXISTS identity.memberships (
    group_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    joined_at TIMESTAMPTZ(6) NOT NULL,
    role VARCHAR(16) NOT NULL,
    CONSTRAINT memberships_primary PRIMARY KEY (group_id, user_id)
);

-- Groups only have OWNER and MEMBER now. Old ADMIN rows become MEMBER.
UPDATE identity.memberships SET role = 'MEMBER' WHERE role = 'ADMIN';

CREATE TABLE IF NOT EXISTS identity.profiles (
    user_id VARCHAR(36) NOT NULL,
    display_name VARCHAR(64),
    avatar VARCHAR(2048),
    bio VARCHAR(280),
    PRIMARY KEY (user_id)
);

ALTER TABLE identity.profiles
    ADD COLUMN IF NOT EXISTS bio VARCHAR(280);

CREATE TABLE IF NOT EXISTS identity.friend_requests (
    from_user_id VARCHAR(36) NOT NULL,
    to_user_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (from_user_id, to_user_id)
);

CREATE TABLE IF NOT EXISTS identity.friendships (
    user_id VARCHAR(36) NOT NULL,
    friend_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (user_id, friend_id)
);

CREATE TABLE IF NOT EXISTS identity.blocks (
    blocker_id VARCHAR(36) NOT NULL,
    blocked_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (blocker_id, blocked_id)
);
