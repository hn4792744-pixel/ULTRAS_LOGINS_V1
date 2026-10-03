CREATE TABLE IF NOT EXISTS ul_accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    username VARCHAR(32) NOT NULL,
    username_lower VARCHAR(32) NOT NULL,
    account_type VARCHAR(16) NOT NULL,
    password_hash VARCHAR(255),
    language VARCHAR(8),
    registered_at BIGINT,
    last_login BIGINT,
    last_ip_hash VARCHAR(64),
    session_token_hash VARCHAR(64),
    session_created_at BIGINT,
    session_expires_at BIGINT,
    authenticated INTEGER NOT NULL DEFAULT 0,
    notifications_enabled INTEGER NOT NULL DEFAULT 1,
    sounds_enabled INTEGER NOT NULL DEFAULT 1,
    reminders_enabled INTEGER NOT NULL DEFAULT 1,
    created_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ul_accounts_name ON ul_accounts (username_lower, account_type);
