-- =============================================================================
-- V1 - Baseline schema
-- =============================================================================
-- Every statement is idempotent (IF NOT EXISTS) so this migration is safe on
--   * an empty database  -> the tables are created
--   * an existing database (created by Hibernate ddl-auto=update before) -> no-op
--
-- Unique constraints and foreign keys are intentionally NOT created here:
-- Hibernate still owns them (ddl-auto=update) and would otherwise create a
-- second, redundant constraint with a different generated name.
-- Performance indexes live in V2.
-- =============================================================================

-- ---------------------------------------------------------------- users
CREATE TABLE IF NOT EXISTS users (
    id                    VARCHAR(255) NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    username              VARCHAR(255) NOT NULL,
    profile_image_url     VARCHAR(2048),
    foreground_image_url  VARCHAR(2048),
    role                  VARCHAR(255) NOT NULL,
    created_at            TIMESTAMP(6)  NOT NULL,
    updated_at            TIMESTAMP(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

-- ------------------------------------------------------- device_sessions
CREATE TABLE IF NOT EXISTS device_sessions (
    id                   VARCHAR(255) NOT NULL,
    user_id              VARCHAR(255) NOT NULL,
    map_x                DOUBLE PRECISION,
    map_y                DOUBLE PRECISION,
    city                 VARCHAR(255),
    country              VARCHAR(255),
    location_updated_at  TIMESTAMP(6),
    device_name          VARCHAR(255) NOT NULL,
    device_type          VARCHAR(255) NOT NULL,
    ip_address           VARCHAR(255) NOT NULL,
    user_agent           VARCHAR(255) NOT NULL,
    refresh_token_hash   VARCHAR(255) NOT NULL,
    last_active_at       TIMESTAMP(6)  NOT NULL,
    expires_at           TIMESTAMP(6)  NOT NULL,
    revoked              BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP(6)  NOT NULL,
    CONSTRAINT pk_device_sessions PRIMARY KEY (id)
);

-- --------------------------------------------------------- conversations
CREATE TABLE IF NOT EXISTS conversations (
    id             VARCHAR(255) NOT NULL,
    type           VARCHAR(255) NOT NULL,
    name           VARCHAR(255),
    description    VARCHAR(255),
    private_key    VARCHAR(255),
    requester_id   VARCHAR(255),
    status         VARCHAR(255),
    created_at     TIMESTAMP(6)  NOT NULL,
    updated_at     TIMESTAMP(6)  NOT NULL,
    last_message_at TIMESTAMP(6),
    CONSTRAINT pk_conversations PRIMARY KEY (id)
);

-- --------------------------------------------------- conversation_members
CREATE TABLE IF NOT EXISTS conversation_members (
    id                   VARCHAR(255) NOT NULL,
    conversation_id      VARCHAR(255) NOT NULL,
    user_id              VARCHAR(255) NOT NULL,
    role                 VARCHAR(255) NOT NULL,
    joined_at            TIMESTAMP(6)  NOT NULL,
    left_at              TIMESTAMP(6),
    last_read_message_id VARCHAR(255),
    created_at           TIMESTAMP(6)  NOT NULL,
    CONSTRAINT pk_conversation_members PRIMARY KEY (id)
);

-- -------------------------------------------------------------- messages
CREATE TABLE IF NOT EXISTS messages (
    id               VARCHAR(255) NOT NULL,
    conversation_id  VARCHAR(255) NOT NULL,
    sender_id        VARCHAR(255) NOT NULL,
    is_read          BOOLEAN       NOT NULL DEFAULT FALSE,
    type             VARCHAR(255) NOT NULL,
    content          TEXT          NOT NULL,
    created_at       TIMESTAMP(6)  NOT NULL,
    edited_at        TIMESTAMP(6),
    deleted_at       TIMESTAMP(6),
    related_call_id  VARCHAR(255),
    CONSTRAINT pk_messages PRIMARY KEY (id)
);

-- --------------------------------------------------------- call_sessions
CREATE TABLE IF NOT EXISTS call_sessions (
    id           VARCHAR(255) NOT NULL,
    caller_id    VARCHAR(255) NOT NULL,
    receiver_id  VARCHAR(255) NOT NULL,
    sdp_offer    TEXT,
    status       VARCHAR(255) NOT NULL,
    call_type    VARCHAR(255) NOT NULL,
    expires_at   TIMESTAMP(6)  NOT NULL,
    created_at   TIMESTAMP(6)  NOT NULL,
    ended_at     TIMESTAMP(6),
    CONSTRAINT pk_call_sessions PRIMARY KEY (id)
);

-- ---------------------------------------------------- group_call_sessions
CREATE TABLE IF NOT EXISTS group_call_sessions (
    id               VARCHAR(255) NOT NULL,
    conversation_id  VARCHAR(255) NOT NULL,
    caller_id        VARCHAR(255) NOT NULL,
    status           VARCHAR(255) NOT NULL,
    call_type        VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP(6)  NOT NULL,
    ended_at         TIMESTAMP(6),
    CONSTRAINT pk_group_call_sessions PRIMARY KEY (id)
);

-- -------------------------------------------- group_call_participants (@ElementCollection)
CREATE TABLE IF NOT EXISTS group_call_participants (
    session_id VARCHAR(255) NOT NULL,
    user_id    VARCHAR(255) NOT NULL,
    CONSTRAINT pk_group_call_participants PRIMARY KEY (session_id, user_id)
);

-- ---------------------------------------------------------- group_invites
CREATE TABLE IF NOT EXISTS group_invites (
    id                VARCHAR(255) NOT NULL,
    conversation_id   VARCHAR(255) NOT NULL,
    invited_user_id   VARCHAR(255) NOT NULL,
    invited_by_user_id VARCHAR(255) NOT NULL,
    status            VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP(6)  NOT NULL,
    responded_at      TIMESTAMP(6),
    CONSTRAINT pk_group_invites PRIMARY KEY (id)
);

-- ----------------------------------------------------------- invite_links
CREATE TABLE IF NOT EXISTS invite_links (
    id                VARCHAR(255) NOT NULL,
    conversation_id   VARCHAR(255) NOT NULL,
    created_by_user_id VARCHAR(255) NOT NULL,
    token             VARCHAR(64)  NOT NULL,
    expires_at        TIMESTAMP(6),
    max_uses          INTEGER,
    uses_count        INTEGER       NOT NULL DEFAULT 0,
    revoked           BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP(6)  NOT NULL,
    CONSTRAINT pk_invite_links PRIMARY KEY (id)
);

-- ----------------------------------------------------------------- rooms
CREATE TABLE IF NOT EXISTS rooms (
    id          BIGINT       GENERATED BY DEFAULT AS IDENTITY,
    room_id     VARCHAR(255),
    room_name   VARCHAR(255),
    room_images VARCHAR(255),
    created_at  TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_rooms PRIMARY KEY (id)
);

-- -------------------------------------------------------------- sessions
CREATE TABLE IF NOT EXISTS sessions (
    id          VARCHAR(255) NOT NULL,
    device_type VARCHAR(255),
    device_name VARCHAR(255),
    user_id     VARCHAR(255),
    map_x       VARCHAR(255),
    map_y       VARCHAR(255),
    city        VARCHAR(255),
    country     VARCHAR(255),
    session_id  VARCHAR(255),
    token       VARCHAR(255),
    login_at    TIMESTAMP(6),
    update_at   TIMESTAMP(6),
    CONSTRAINT pk_sessions PRIMARY KEY (id)
);

-- -------------------------------------------------------- user_activities
CREATE TABLE IF NOT EXISTS user_activities (
    id        BIGINT       GENERATED BY DEFAULT AS IDENTITY,
    is_online BOOLEAN      NOT NULL DEFAULT FALSE,
    user_id   VARCHAR(255),
    CONSTRAINT pk_user_activities PRIMARY KEY (id)
);
