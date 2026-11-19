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
