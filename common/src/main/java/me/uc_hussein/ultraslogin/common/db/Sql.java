package me.uc_hussein.ultraslogin.common.db;

/** Portable SQL (valid on SQLite and MariaDB/MySQL). Parameters are always bound, never concatenated. */
final class Sql {
    static final String COLUMNS = "id, uuid, username, username_lower, account_type, password_hash, language, registered_at, "
            + "last_login, last_ip_hash, session_token_hash, session_created_at, session_expires_at, authenticated, "
            + "notifications_enabled, sounds_enabled, reminders_enabled, created_at";

    static final String FIND_BY_UUID = "SELECT " + COLUMNS + " FROM ul_accounts WHERE uuid = ?";
    static final String FIND_BY_NAME_AND_TYPE = "SELECT " + COLUMNS + " FROM ul_accounts WHERE username_lower = ? AND account_type = ? ORDER BY id LIMIT 1";
    static final String FIND_BY_NAME = "SELECT " + COLUMNS + " FROM ul_accounts WHERE username_lower = ? ORDER BY id LIMIT 1";
    static final String INSERT = "INSERT INTO ul_accounts (uuid, username, username_lower, account_type, password_hash, language, registered_at, "
            + "last_login, last_ip_hash, session_token_hash, session_created_at, session_expires_at, authenticated, "
            + "notifications_enabled, sounds_enabled, reminders_enabled, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    static final String UPDATE = "UPDATE ul_accounts SET uuid = ?, username = ?, username_lower = ?, account_type = ?, password_hash = ?, "
            + "language = ?, registered_at = ?, last_login = ?, last_ip_hash = ?, session_token_hash = ?, session_created_at = ?, "
            + "session_expires_at = ?, authenticated = ?, notifications_enabled = ?, sounds_enabled = ?, reminders_enabled = ? WHERE id = ?";
    static final String RESET_AUTHENTICATED = "UPDATE ul_accounts SET authenticated = 0 WHERE authenticated <> 0";
    static final String COUNT = "SELECT COUNT(*) FROM ul_accounts";

    private Sql() {
    }
}
