package me.uc_hussein.ultraslogin.common.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Connection pool + schema migrations. All calls are blocking: use them from a database executor only. */
public final class Database implements AutoCloseable {
    private static final int LATEST_VERSION = 1;

    private final HikariDataSource ds;
    private final boolean sqlite;

    private Database(HikariDataSource ds, boolean sqlite) {
        this.ds = ds;
        this.sqlite = sqlite;
    }

    public static Database open(DatabaseConfig cfg, Path dataDir) throws SQLException {
        HikariConfig hc = new HikariConfig();
        hc.setPoolName("UltrasLogin-DB");
        hc.setConnectionTimeout(10_000);
        boolean sqlite = cfg.type() == DatabaseConfig.Type.SQLITE;
        if (sqlite) {
            Path file = dataDir.resolve(cfg.sqliteFile().isBlank() ? "accounts.db" : cfg.sqliteFile()).toAbsolutePath();
            hc.setDriverClassName("org.sqlite.JDBC");
            hc.setJdbcUrl("jdbc:sqlite:" + file);
            hc.setMaximumPoolSize(2);
            hc.setConnectionInitSql("PRAGMA busy_timeout=5000; PRAGMA foreign_keys=ON;");
        } else {
            hc.setDriverClassName("org.mariadb.jdbc.Driver");
            String ssl = cfg.sslMode().isBlank() ? "disable" : cfg.sslMode();
            hc.setJdbcUrl("jdbc:mariadb://" + cfg.host() + ":" + cfg.port() + "/" + cfg.database() + "?sslMode=" + ssl);
            hc.setUsername(cfg.username());
            hc.setPassword(cfg.password());
            hc.setMaximumPoolSize(cfg.poolSize());
            hc.setMinimumIdle(Math.min(2, cfg.poolSize()));
        }
        HikariDataSource ds;
        try {
            ds = new HikariDataSource(hc);
        } catch (RuntimeException e) {
            // do not leak connection details beyond the message of the driver
            throw new SQLException("Could not open the database pool: " + e.getMessage(), e);
        }
        Database db = new Database(ds, sqlite);
        try {
            db.migrate();
            if (sqlite) {
                try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                    s.execute("PRAGMA journal_mode=WAL");
                }
            }
        } catch (SQLException | RuntimeException e) {
            ds.close();
            throw e instanceof SQLException se ? se : new SQLException(e);
        }
        return db;
    }

    public DataSource dataSource() {
        return ds;
    }

    private void migrate() throws SQLException {
        try (Connection c = ds.getConnection()) {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS ul_schema_version (version INT NOT NULL)");
            }
            int current = 0;
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT MAX(version) FROM ul_schema_version")) {
                if (rs.next()) {
                    current = rs.getInt(1);
                }
            }
            for (int v = current + 1; v <= LATEST_VERSION; v++) {
                String path = "/db/" + (sqlite ? "sqlite" : "mysql") + "/V" + v + "__init.sql";
                String script;
                try (InputStream in = Database.class.getResourceAsStream(path)) {
                    if (in == null) {
                        throw new SQLException("Missing migration resource " + path);
                    }
                    script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new SQLException("Could not read migration " + path, e);
                }
                for (String stmt : script.split(";\\s*\\n")) {
                    String sql = stmt.trim();
                    if (sql.endsWith(";")) {
                        sql = sql.substring(0, sql.length() - 1);
                    }
                    if (!sql.isEmpty()) {
                        try (Statement s = c.createStatement()) {
                            s.execute(sql);
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO ul_schema_version (version) VALUES (?)")) {
                    ps.setInt(1, v);
                    ps.executeUpdate();
                }
            }
        }
    }

    @Override
    public void close() {
        ds.close();
    }
}
