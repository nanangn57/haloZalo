-- One DIRECT conversation per pair of users, and the time of the newest message for the conversation list.

ALTER TABLE conversations
    -- "<smaller user id>:<larger user id>" for DIRECT, NULL for GROUP. See Conversation.directKey.
    ADD COLUMN direct_key      TEXT,
    -- created_at of the newest message. Set in the same transaction that bumps last_seq.
    ADD COLUMN last_message_at TIMESTAMPTZ;

UPDATE conversations c
SET direct_key = (
    SELECT string_agg(m.user_id::text, ':' ORDER BY m.user_id::text)
    FROM conversation_members m
    WHERE m.conversation_id = c.id)
WHERE c.type = 'DIRECT';

UPDATE conversations c
SET last_message_at = (SELECT created_at FROM messages WHERE conversation_id = c.id AND seq = c.last_seq)
WHERE c.last_seq > 0;

-- Fails if two DIRECT conversations already exist for one pair. Only development data can have that:
-- reset it with ./local-deps.sh reset.
ALTER TABLE conversations
    ADD CONSTRAINT conversations_direct_key UNIQUE (direct_key),
    ADD CONSTRAINT conversations_direct_has_key CHECK ((type = 'DIRECT') = (direct_key IS NOT NULL));
