-- Files uploaded to a conversation. The bytes live in the media store (local disk for now), keyed by id.
-- A message points at a row here through content->>'mediaId'; download links are signed per read, never stored.

CREATE TABLE media (
    id              UUID        PRIMARY KEY,
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    -- Only the uploader can attach the file to a message, and only in this conversation.
    uploader_id     UUID        NOT NULL,
    kind            TEXT        NOT NULL CHECK (kind IN ('IMAGE', 'VIDEO', 'DOCUMENT')),
    file_name       TEXT        NOT NULL,
    mime_type       TEXT        NOT NULL,
    size_bytes      BIGINT      NOT NULL CHECK (size_bytes > 0),
    width           INTEGER     CHECK (width > 0),
    height          INTEGER     CHECK (height > 0),
    created_at      TIMESTAMPTZ NOT NULL
);
