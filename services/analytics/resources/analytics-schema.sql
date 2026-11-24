CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE IF NOT EXISTS analytics.recorded_events (
    event_id VARCHAR(36) NOT NULL,
    type VARCHAR(80) NOT NULL,
    occurred_at TIMESTAMPTZ(6) NOT NULL,
    PRIMARY KEY (event_id)
);

CREATE TABLE IF NOT EXISTS analytics.totals (
    type VARCHAR(80) NOT NULL,
    total BIGINT NOT NULL,
    PRIMARY KEY (type)
);

CREATE TABLE IF NOT EXISTS analytics.messages_by_day (
    day DATE NOT NULL,
    total BIGINT NOT NULL,
    PRIMARY KEY (day)
);

CREATE TABLE IF NOT EXISTS analytics.active_users (
    user_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (user_id)
);

CREATE TABLE IF NOT EXISTS analytics.known_groups (
    group_id VARCHAR(36) NOT NULL,
    member_count BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (group_id)
);

CREATE TABLE IF NOT EXISTS analytics.group_message_totals (
    group_id VARCHAR(36) NOT NULL,
    total BIGINT NOT NULL,
    PRIMARY KEY (group_id)
);

CREATE TABLE IF NOT EXISTS analytics.user_activity (
    user_id VARCHAR(36) NOT NULL,
    logins BIGINT NOT NULL DEFAULT 0,
    messages_sent BIGINT NOT NULL DEFAULT 0,
    messages_received BIGINT NOT NULL DEFAULT 0,
    groups_created BIGINT NOT NULL DEFAULT 0,
    friends_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id)
);

CREATE TABLE IF NOT EXISTS analytics.user_activity_by_day (
    user_id VARCHAR(36) NOT NULL,
    day DATE NOT NULL,
    logins BIGINT NOT NULL DEFAULT 0,
    messages_sent BIGINT NOT NULL DEFAULT 0,
    messages_received BIGINT NOT NULL DEFAULT 0,
    messages_direct BIGINT NOT NULL DEFAULT 0,
    messages_group BIGINT NOT NULL DEFAULT 0,
    messages_text BIGINT NOT NULL DEFAULT 0,
    messages_media BIGINT NOT NULL DEFAULT 0,
    groups_created BIGINT NOT NULL DEFAULT 0,
    friend_requests_sent BIGINT NOT NULL DEFAULT 0,
    friend_requests_received BIGINT NOT NULL DEFAULT 0,
    friend_requests_accepted BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day)
);

CREATE TABLE IF NOT EXISTS analytics.user_hour_weekday (
    user_id VARCHAR(36) NOT NULL,
    weekday SMALLINT NOT NULL,
    hour SMALLINT NOT NULL,
    messages_sent BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, weekday, hour)
);

CREATE TABLE IF NOT EXISTS analytics.user_conversation_totals (
    user_id VARCHAR(36) NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    messages_sent BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, conversation_id)
);

CREATE TABLE IF NOT EXISTS analytics.active_users_by_day (
    day DATE NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (day, user_id)
);

CREATE TABLE IF NOT EXISTS analytics.registrations_by_day (
    day DATE NOT NULL,
    total BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (day)
);

CREATE TABLE IF NOT EXISTS analytics.verifications_by_day (
    day DATE NOT NULL,
    total BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (day)
);

CREATE TABLE IF NOT EXISTS analytics.friend_by_day (
    day DATE NOT NULL,
    requests_sent BIGINT NOT NULL DEFAULT 0,
    requests_accepted BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (day)
);

CREATE TABLE IF NOT EXISTS analytics.groups_by_day (
    day DATE NOT NULL,
    groups_created BIGINT NOT NULL DEFAULT 0,
    members_sum BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (day)
);

CREATE TABLE IF NOT EXISTS analytics.media_by_day (
    day DATE NOT NULL,
    type VARCHAR(20) NOT NULL,
    total BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (day, type)
);

CREATE TABLE IF NOT EXISTS analytics.user_registrations (
    user_id VARCHAR(36) NOT NULL,
    day DATE NOT NULL,
    PRIMARY KEY (user_id)
);

CREATE INDEX IF NOT EXISTS user_registrations_day
    ON analytics.user_registrations (day);

CREATE TABLE IF NOT EXISTS analytics.user_verifications (
    user_id VARCHAR(36) NOT NULL,
    day DATE NOT NULL,
    PRIMARY KEY (user_id)
);

CREATE INDEX IF NOT EXISTS user_verifications_day
    ON analytics.user_verifications (day);

-- Existing installs may still have the old known_groups / user_activity shapes.
ALTER TABLE analytics.known_groups ADD COLUMN IF NOT EXISTS member_count BIGINT NOT NULL DEFAULT 1;
ALTER TABLE analytics.user_activity ADD COLUMN IF NOT EXISTS messages_received BIGINT NOT NULL DEFAULT 0;
ALTER TABLE analytics.user_activity ADD COLUMN IF NOT EXISTS friends_count BIGINT NOT NULL DEFAULT 0;
