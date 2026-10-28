-- How far each user has read in each conversation. Not stored on messages: one row per user and conversation,
-- shared by all of that user's devices, so reading on one device clears unread on the others.

CREATE TABLE read_markers (
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL,
    -- Highest seq the user has read. Only ever moves forward.
    last_read_seq   BIGINT      NOT NULL CHECK (last_read_seq >= 0),
    updated_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (conversation_id, user_id)
);
