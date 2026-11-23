-- SYSTEM messages (written by the server, no sender) and per-user push notification prefs.

-- A SYSTEM message has no sender. Every other type must have one. The unique (sender_id, client_msg_id) does not
-- apply to a NULL sender, so system messages never collide with a retry.
ALTER TABLE messages ALTER COLUMN sender_id DROP NOT NULL;

ALTER TABLE messages DROP CONSTRAINT messages_type_check;
ALTER TABLE messages ADD CONSTRAINT messages_type_check
    CHECK (type IN ('TEXT', 'EMOTION', 'IMAGE', 'DOCUMENT', 'VIDEO', 'AUDIO', 'SYSTEM'));
ALTER TABLE messages ADD CONSTRAINT messages_system_has_no_sender
    CHECK ((type = 'SYSTEM') = (sender_id IS NULL));

-- One row per user, created on the first PATCH. No row means both flags are true.
CREATE TABLE notification_prefs (
    user_id         UUID        PRIMARY KEY,
    push_enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    preview_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
