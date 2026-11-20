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
    PRIMARY KEY (group_id)
);

CREATE TABLE IF NOT EXISTS analytics.group_message_totals (
    group_id VARCHAR(36) NOT NULL,
    total BIGINT NOT NULL,
    PRIMARY KEY (group_id)
);

CREATE TABLE IF NOT EXISTS analytics.user_activity (
    user_id VARCHAR(36) NOT NULL,
    logins BIGINT NOT NULL,
    messages_sent BIGINT NOT NULL,
    groups_created BIGINT NOT NULL,
    PRIMARY KEY (user_id)
);
