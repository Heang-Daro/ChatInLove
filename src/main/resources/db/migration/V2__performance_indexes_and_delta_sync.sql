-- =============================================================================
-- V2 - Performance indexes + delta-sync sequence
-- =============================================================================
-- 1. The indexes below remove the sequential scans of the hot queries:
--      * GET /api/conversations            (chat list)
--      * GET /api/conversations/{id}/messages
--      * POST /api/conversations/{id}/messages/{mid}/read
--      * JwtFilter (user + device session lookup on every request)
-- 2. messages.sequence_id gives the clients a cheap, exact delta-sync cursor
--    (the "after" parameter) instead of re-downloading the newest N messages.
--
-- The sequence id column is created FIRST because the index below uses it.
-- All statements are idempotent.
-- =============================================================================

-- =============================================================================
-- Delta-sync cursor (must come before the index that uses the column)
-- =============================================================================
CREATE SEQUENCE IF NOT EXISTS messages_sequence_id_seq;

-- Added without a default first, so the backfill below can number the
-- existing rows in creation order.
ALTER TABLE messages ADD COLUMN IF NOT EXISTS sequence_id BIGINT;

-- Number the existing rows oldest-first so that the sequence order matches
-- the creation order of the messages.
WITH ordered AS (
    SELECT id,
           ROW_NUMBER() OVER (ORDER BY created_at ASC, id ASC) AS rn
    FROM messages
    WHERE sequence_id IS NULL
)
UPDATE messages m
SET sequence_id = o.rn
FROM ordered o
WHERE m.id = o.id;

-- From now on the database assigns the value (Hibernate does not write this
-- column, see Message.sequenceId -> insertable = false).
ALTER TABLE messages ALTER COLUMN sequence_id SET DEFAULT nextval('messages_sequence_id_seq');
ALTER SEQUENCE messages_sequence_id_seq OWNED BY messages.sequence_id;

-- Continue after the highest value that is in use.
SELECT setval(
    'messages_sequence_id_seq',
    GREATEST(COALESCE((SELECT MAX(sequence_id) FROM messages), 0), 1)
);

-- ------------------------------------------------------ messages: hot path
-- Conversation pages are read as "conversation_id = ? ORDER BY created_at DESC".
CREATE INDEX IF NOT EXISTS idx_messages_conversation_created
    ON messages (conversation_id, created_at DESC);

-- Delta sync: "conversation_id = ? AND sequence_id > ? ORDER BY sequence_id ASC".
CREATE INDEX IF NOT EXISTS idx_messages_conversation_sequence
    ON messages (conversation_id, sequence_id);

-- Lookup of the messages that belong to a finished call session.
CREATE INDEX IF NOT EXISTS idx_messages_related_call
    ON messages (related_call_id);

-- ------------------------------------------- conversation_members: hot path
-- The chat list joins conversation_members on user_id.
CREATE INDEX IF NOT EXISTS idx_conversation_members_user
    ON conversation_members (user_id);

-- Member lookup for a single conversation (also covered by the unique
-- constraint, but the explicit index keeps the plan stable).
CREATE INDEX IF NOT EXISTS idx_conversation_members_conversation
    ON conversation_members (conversation_id);

-- --------------------------------------------------- conversations: hot path
-- The chat list is sorted by last_message_at.
CREATE INDEX IF NOT EXISTS idx_conversations_last_message
    ON conversations (last_message_at DESC);

-- Status filter of the pending-request query.
CREATE INDEX IF NOT EXISTS idx_conversations_status
    ON conversations (status);

-- ----------------------------------------------- device_sessions: hot path
-- JwtFilter loads the session of the caller on EVERY authenticated request.
CREATE INDEX IF NOT EXISTS idx_device_sessions_user
    ON device_sessions (user_id);

-- Cleanup of expired sessions.
CREATE INDEX IF NOT EXISTS idx_device_sessions_expires
    ON device_sessions (expires_at);

-- --------------------------------------------------------------- users
-- Login lookup by e-mail.
CREATE INDEX IF NOT EXISTS idx_users_email
    ON users (email);
