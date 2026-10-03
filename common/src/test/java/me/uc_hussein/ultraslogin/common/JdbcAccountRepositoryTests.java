package me.uc_hussein.ultraslogin.common;

import me.uc_hussein.ultraslogin.common.db.Database;
import me.uc_hussein.ultraslogin.common.db.DatabaseConfig;
import me.uc_hussein.ultraslogin.common.db.JdbcAccountRepository;
import me.uc_hussein.ultraslogin.common.model.Account;
import me.uc_hussein.ultraslogin.common.model.AccountType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Runs against a real SQLite file (needs the sqlite-jdbc test dependency from Gradle). */
class JdbcAccountRepositoryTests {
    @Test
    void sqliteSchemaAndCrudWork() throws Exception {
        Path dir = Files.createTempDirectory("ultras-db");
        DatabaseConfig cfg = new DatabaseConfig(DatabaseConfig.Type.SQLITE, "test.db", "", 0, "", "", "", 2, "");
        try (Database db = Database.open(cfg, dir)) {
            JdbcAccountRepository repo = new JdbcAccountRepository(db.dataSource());
            Account a = new Account(UUID.randomUUID(), "Steve", AccountType.CRACKED, 5);
            repo.insert(a);
            assertTrue(a.id() > 0);
            assertEquals(1, repo.count());

            a.setPasswordHash("$argon2id$v=19$m=1024,t=2,p=1$AAAAAAAAAAAAAAAAAAAAAA$BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
            a.setLanguage("ar");
            a.setLastIpHash("abc");
            a.setSessionTokenHash("tok");
            a.setSessionExpiresAt(123L);
            a.setSoundsEnabled(false);
            repo.update(a);

            Account b = repo.findByUuid(a.uuid()).orElseThrow();
            assertEquals("ar", b.language());
            assertTrue(b.isRegistered());
            assertFalse(b.soundsEnabled());
            assertEquals(123L, b.sessionExpiresAt());
            assertNull(b.registeredAt());
            assertEquals(a.uuid(), repo.findByName("steve", AccountType.CRACKED).orElseThrow().uuid());
            assertTrue(repo.findByName("steve", AccountType.PREMIUM).isEmpty());
            assertTrue(repo.findByNameAny("steve").isPresent());

            b.setAuthenticated(true);
            repo.update(b);
            repo.resetAuthenticatedFlags();
            assertFalse(repo.findByUuid(a.uuid()).orElseThrow().authenticated());
        }
        try (Database again = Database.open(cfg, dir)) {            // re-open: migrations are idempotent
            assertEquals(1, new JdbcAccountRepository(again.dataSource()).count());
        }
    }
}
