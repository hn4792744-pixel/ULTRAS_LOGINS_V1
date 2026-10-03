package me.uc_hussein.ultraslogin.common.db;

import me.uc_hussein.ultraslogin.common.model.Account;
import me.uc_hussein.ultraslogin.common.model.AccountType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;

/** JDBC implementation (SQLite, MariaDB, MySQL). */
public final class JdbcAccountRepository implements AccountRepository {
    private final DataSource ds;

    public JdbcAccountRepository(DataSource ds) {
        this.ds = ds;
    }

    @Override
    public Optional<Account> findByUuid(UUID uuid) {
        return queryOne(Sql.FIND_BY_UUID, ps -> ps.setString(1, uuid.toString()));
    }

    @Override
    public Optional<Account> findByName(String usernameLower, AccountType type) {
        return queryOne(Sql.FIND_BY_NAME_AND_TYPE, ps -> {
            ps.setString(1, usernameLower);
            ps.setString(2, type.name());
        });
    }

    @Override
    public Optional<Account> findByNameAny(String usernameLower) {
        return queryOne(Sql.FIND_BY_NAME, ps -> ps.setString(1, usernameLower));
    }

    @Override
    public Account insert(Account a) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(Sql.INSERT, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setString(i++, a.uuid().toString());
            ps.setString(i++, a.username());
            ps.setString(i++, a.usernameLower());
            ps.setString(i++, a.type().name());
            setString(ps, i++, a.passwordHash());
            setString(ps, i++, a.language());
            setLong(ps, i++, a.registeredAt());
            setLong(ps, i++, a.lastLogin());
            setString(ps, i++, a.lastIpHash());
            setString(ps, i++, a.sessionTokenHash());
            setLong(ps, i++, a.sessionCreatedAt());
            setLong(ps, i++, a.sessionExpiresAt());
            ps.setInt(i++, a.authenticated() ? 1 : 0);
            ps.setInt(i++, a.notificationsEnabled() ? 1 : 0);
            ps.setInt(i++, a.soundsEnabled() ? 1 : 0);
            ps.setInt(i++, a.remindersEnabled() ? 1 : 0);
            ps.setLong(i, a.createdAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    a.setId(keys.getLong(1));
                }
            }
            return a;
        } catch (SQLException e) {
            throw new DataAccessException("insert account failed", e);
        }
    }

    @Override
    public void update(Account a) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(Sql.UPDATE)) {
            int i = 1;
            ps.setString(i++, a.uuid().toString());
            ps.setString(i++, a.username());
            ps.setString(i++, a.usernameLower());
            ps.setString(i++, a.type().name());
            setString(ps, i++, a.passwordHash());
            setString(ps, i++, a.language());
            setLong(ps, i++, a.registeredAt());
            setLong(ps, i++, a.lastLogin());
            setString(ps, i++, a.lastIpHash());
            setString(ps, i++, a.sessionTokenHash());
            setLong(ps, i++, a.sessionCreatedAt());
            setLong(ps, i++, a.sessionExpiresAt());
            ps.setInt(i++, a.authenticated() ? 1 : 0);
            ps.setInt(i++, a.notificationsEnabled() ? 1 : 0);
            ps.setInt(i++, a.soundsEnabled() ? 1 : 0);
            ps.setInt(i++, a.remindersEnabled() ? 1 : 0);
            ps.setLong(i, a.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("update account failed", e);
        }
    }

    @Override
    public void resetAuthenticatedFlags() {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate(Sql.RESET_AUTHENTICATED);
        } catch (SQLException e) {
            throw new DataAccessException("reset flags failed", e);
        }
    }

    @Override
    public long count() {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(Sql.COUNT)) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("count failed", e);
        }
    }

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private Optional<Account> queryOne(String sql, Binder binder) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("query failed", e);
        }
    }

    private static Account map(ResultSet rs) throws SQLException {
        Account a = new Account();
        a.setId(rs.getLong("id"));
        a.setUuid(UUID.fromString(rs.getString("uuid")));
        a.setUsername(rs.getString("username"));
        a.setType(AccountType.valueOf(rs.getString("account_type")));
        a.setPasswordHash(rs.getString("password_hash"));
        a.setLanguage(rs.getString("language"));
        a.setRegisteredAt(getLong(rs, "registered_at"));
        a.setLastLogin(getLong(rs, "last_login"));
        a.setLastIpHash(rs.getString("last_ip_hash"));
        a.setSessionTokenHash(rs.getString("session_token_hash"));
        a.setSessionCreatedAt(getLong(rs, "session_created_at"));
        a.setSessionExpiresAt(getLong(rs, "session_expires_at"));
        a.setAuthenticated(rs.getInt("authenticated") != 0);
        a.setNotificationsEnabled(rs.getInt("notifications_enabled") != 0);
        a.setSoundsEnabled(rs.getInt("sounds_enabled") != 0);
        a.setRemindersEnabled(rs.getInt("reminders_enabled") != 0);
        a.setCreatedAt(rs.getLong("created_at"));
        return a;
    }

    private static Long getLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private static void setString(PreparedStatement ps, int i, String v) throws SQLException {
        if (v == null) {
            ps.setNull(i, Types.VARCHAR);
        } else {
            ps.setString(i, v);
        }
    }

    private static void setLong(PreparedStatement ps, int i, Long v) throws SQLException {
        if (v == null) {
            ps.setNull(i, Types.BIGINT);
        } else {
            ps.setLong(i, v);
        }
    }
}
