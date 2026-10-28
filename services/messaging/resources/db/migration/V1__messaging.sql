-- Messaging tables, in schema messaging of the shared halozalo database. Flyway applies each file once, in version
-- order, and keeps its history in messaging.flyway_schema_history. Never edit an applied file: add V2, V3...
-- No foreign keys into other schemas: user ids and group ids belong to identity and are plain UUIDs here.

CREATE TABLE conversations (
    -- For GROUP, the id of the group in identity.
    id              UUID        PRIMARY KEY,
    type            TEXT        NOT NULL CHECK (type IN ('DIRECT', 'GROUP')),
    -- seq of the newest message. Bumped in the same transaction that inserts the message.
    last_seq        BIGINT      NOT NULL DEFAULT 0 CHECK (last_seq >= 0),
    created_at      TIMESTAMPTZ NOT NULL,
    -- created_at of the newest message, for the conversation list. Set together with last_seq.
    last_message_at TIMESTAMPTZ,
    -- "<smaller user id>:<larger user id>" for DIRECT, NULL for GROUP. See Conversation.directKey.
    direct_key      TEXT,
    CONSTRAINT conversations_direct_key UNIQUE (direct_key),
    CONSTRAINT conversations_direct_has_key CHECK ((type = 'DIRECT') = (direct_key IS NOT NULL))
);

-- The two people of a DIRECT conversation. Messaging owns 1-1 conversations, so it keeps them.
-- Group members are never stored here: they live in identity and are asked through MembershipLookup.
CREATE TABLE direct_members (
    conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id         UUID NOT NULL,
    PRIMARY KEY (conversation_id, user_id)
);

-- "DIRECT conversations of this user", for the conversation list.
CREATE INDEX direct_members_user ON direct_members (user_id);

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
    -- One message per seq in a conversation. Also serves history and catch-up by seq.
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
