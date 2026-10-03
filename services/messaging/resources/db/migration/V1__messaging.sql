-- Messaging schema. Flyway applies each file once, in version order. Never edit an applied file: add V2, V3...
-- Users live in Identity, so user ids are plain UUIDs here with no foreign key to another service.

CREATE TABLE conversations (
    id          UUID        PRIMARY KEY,
    type        TEXT        NOT NULL CHECK (type IN ('DIRECT', 'GROUP')),
    -- seq of the newest message. Bumped in the same transaction that inserts the message.
    last_seq    BIGINT      NOT NULL DEFAULT 0 CHECK (last_seq >= 0),
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE conversation_members (
    conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id         UUID NOT NULL,
    PRIMARY KEY (conversation_id, user_id)
);

-- "Conversations of this user", for the conversation list.
CREATE INDEX conversation_members_user ON conversation_members (user_id);

CREATE TABLE messages (
    id              UUID        PRIMARY KEY,
    conversation_id UUID        NOT NULL REFERENCES conversations (id),
    seq             BIGINT      NOT NULL CHECK (seq >= 1),
    sender_id       UUID        NOT NULL,
    client_msg_id   UUID        NOT NULL,
    -- Discriminator for content. TEXT ... VIDEO match message.MessageType.
    type            TEXT        NOT NULL CHECK (type IN ('TEXT', 'EMOTION', 'IMAGE', 'DOCUMENT', 'VIDEO')),
    content         JSONB       NOT NULL,
    metadata        JSONB,
    reply_to        UUID        REFERENCES messages (id),
    forwarded_from  UUID        REFERENCES messages (id),
    status          TEXT        NOT NULL CHECK (status IN ('SENT', 'DELETED', 'ERROR')),
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ,
    -- Same sender, same clientMsgId: the retry gets the stored message back.
    CONSTRAINT messages_sender_client_msg UNIQUE (sender_id, client_msg_id),
    -- One message per seq in a conversation. Also serves catch-up: WHERE conversation_id = ? AND seq > ?.
    CONSTRAINT messages_conversation_seq UNIQUE (conversation_id, seq),
    CONSTRAINT messages_deleted_has_time CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL))
);

CREATE TABLE message_reactions (
    id          UUID        PRIMARY KEY,
    message_id  UUID        NOT NULL REFERENCES messages (id),
    user_id     UUID        NOT NULL,
    code        TEXT        NOT NULL CHECK (code IN ('like', 'love', 'haha', 'wow', 'sad', 'angry')),
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT message_reactions_once UNIQUE (message_id, user_id, code)
);
