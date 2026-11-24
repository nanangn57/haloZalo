-- Analytics schema — layered rollups (not a 1:1 copy of identity/messaging).
--
-- Canonical write path: ActivityRecorder → recorded_events (idempotency) + fact/rollup tables.
-- Snapshot metrics that can drift (friends count) are read live from identity, not these counters.
--
-- Layer A — ledger
--   recorded_events
-- Layer B — personal facts (source for /me/activity range filters)
--   user_activity_by_day, user_hour_weekday, user_conversation_totals
-- Layer C — platform day facts (admin dashboard; some are SUM-derivable from B long-term)
--   active_users_by_day, media_by_day, groups_by_day, friend_by_day,
--   messages_by_day, registrations_by_day, verifications_by_day
-- Layer D — dimensions / cohorts / lifetime caches
--   known_groups, group_message_totals, user_registrations, user_verifications,
--   user_activity, active_users, totals
--
-- Prefer deriving C from B when a rollup is only a SUM; keep a C table when it holds
-- fields B does not (e.g. groups_by_day.members_sum at create time, media type split).

CREATE SCHEMA IF NOT EXISTS analytics;

-- A. Ledger
CREATE TABLE IF NOT EXISTS analytics.recorded_events (
    event_id VARCHAR(36) NOT NULL,
    type VARCHAR(80) NOT NULL,
    occurred_at TIMESTAMPTZ(6) NOT NULL,
    PRIMARY KEY (event_id)
);

-- D. Legacy lifetime totals (GET /stats); prefer user_activity_by_day for ranged KPIs.
CREATE TABLE IF NOT EXISTS analytics.totals (
    type VARCHAR(80) NOT NULL,
    total BIGINT NOT NULL,
    PRIMARY KEY (type)
);

-- C. Platform messages/day (≈ SUM user_activity_by_day.messages_sent; kept for cheap admin chart).
CREATE TABLE IF NOT EXISTS analytics.messages_by_day (
    day DATE NOT NULL,
    total BIGINT NOT NULL,
    PRIMARY KEY (day)
);

-- D. Lifetime active set (subset of active_users_by_day).
CREATE TABLE IF NOT EXISTS analytics.active_users (
    user_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (user_id)
);

-- D. Groups seen by analytics (create or message). Dedup key for group.created counters.
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

-- D. Lifetime personal cache. friends_count may be overwritten by identity snapshot reconcile.
CREATE TABLE IF NOT EXISTS analytics.user_activity (
    user_id VARCHAR(36) NOT NULL,
    logins BIGINT NOT NULL DEFAULT 0,
    messages_sent BIGINT NOT NULL DEFAULT 0,
    messages_received BIGINT NOT NULL DEFAULT 0,
    groups_created BIGINT NOT NULL DEFAULT 0,
    friends_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id)
);

-- B. Personal day facts — primary store for ranged self-stats.
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

-- C. Platform registration/verify day totals (rebuilt from D cohort tables on reconcile).
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

-- C. Platform friend day totals (≈ SUM of user_activity_by_day friend columns).
CREATE TABLE IF NOT EXISTS analytics.friend_by_day (
    day DATE NOT NULL,
    requests_sent BIGINT NOT NULL DEFAULT 0,
    requests_accepted BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (day)
);

-- C. Platform group creates + member_count at create (members_sum not in user day facts).
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

-- D. Cohort dimensions for verify funnel.
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
