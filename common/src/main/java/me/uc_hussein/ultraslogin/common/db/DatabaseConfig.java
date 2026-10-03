package me.uc_hussein.ultraslogin.common.db;

import me.uc_hussein.ultraslogin.common.config.Cfg;

import java.util.Locale;

/** Connection settings. {@code password} is never logged. */
public record DatabaseConfig(Type type, String sqliteFile, String host, int port, String database,
                             String username, String password, int poolSize, String sslMode) {
    public enum Type { SQLITE, MARIADB, MYSQL }

    public static DatabaseConfig from(Cfg c) {
        Type t;
        try {
            t = Type.valueOf(c.string("database.type").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            c.addWarning("database.type must be SQLITE, MARIADB or MYSQL - using SQLITE");
            t = Type.SQLITE;
        }
        return new DatabaseConfig(t, c.string("database.sqlite-file"), c.string("database.host"),
                c.integer("database.port", 3306), c.string("database.database"), c.string("database.username"),
                c.string("database.password"), Math.max(1, c.integer("database.pool-size", 8)), c.string("database.ssl-mode"));
    }
}
