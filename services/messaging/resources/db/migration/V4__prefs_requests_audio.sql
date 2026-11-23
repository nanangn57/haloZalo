-- Per-user conversation prefs (pin, mute, archive), message requests from strangers, and AUDIO messages.

-- One row per user and conversation, created on the first PATCH. No row means all three are false.
CREATE TABLE conversation_prefs (
    user_id         UUID        NOT NULL,
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    pinned          BOOLEAN     NOT NULL DEFAULT FALSE,
    muted           BOOLEAN     NOT NULL DEFAULT FALSE,
    archived        BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, conversation_id)
);

-- Conversation list: "my prefs for these conversations".
CREATE INDEX conversation_prefs_conversation ON conversation_prefs (conversation_id);

-- Message requests: OPEN for the user who started the chat (or once accepted), PENDING for a stranger's recipient.
ALTER TABLE direct_members
    ADD COLUMN request_status TEXT NOT NULL DEFAULT 'OPEN' CHECK (request_status IN ('OPEN', 'PENDING'));

ALTER TABLE messages DROP CONSTRAINT messages_type_check;
ALTER TABLE messages ADD CONSTRAINT messages_type_check
    CHECK (type IN ('TEXT', 'EMOTION', 'IMAGE', 'DOCUMENT', 'VIDEO', 'AUDIO'));
