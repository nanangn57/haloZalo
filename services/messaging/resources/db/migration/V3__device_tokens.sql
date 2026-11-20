-- Push device tokens for offline notifications (Expo / FCM).

CREATE TABLE device_tokens (
    user_id    UUID         NOT NULL,
    token      TEXT         NOT NULL,
    platform   TEXT         NOT NULL DEFAULT 'unknown',
    updated_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (user_id, token)
);

CREATE INDEX device_tokens_token ON device_tokens (token);
