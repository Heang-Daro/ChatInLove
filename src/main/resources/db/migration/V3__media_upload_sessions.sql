-- =============================================================================
-- V3 - Resumable Chunked Media Upload Sessions & Streaming State
-- =============================================================================

CREATE TABLE IF NOT EXISTS media_upload_sessions (
    upload_id VARCHAR(64) PRIMARY KEY,
    uploader_id VARCHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    total_size BIGINT NOT NULL,
    uploaded_bytes BIGINT NOT NULL DEFAULT 0,
    expected_sha256 VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADING',
    temp_file_path TEXT NOT NULL,
    final_file_path TEXT,
    hls_master_playlist_path TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_upload_sessions_status_created ON media_upload_sessions (status, created_at);
CREATE INDEX IF NOT EXISTS idx_upload_sessions_uploader ON media_upload_sessions (uploader_id);
