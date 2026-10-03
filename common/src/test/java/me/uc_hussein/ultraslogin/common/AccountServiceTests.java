package me.uc_hussein.ultraslogin.common;

import me.uc_hussein.ultraslogin.common.db.AccountRepository;
import me.uc_hussein.ultraslogin.common.db.AccountService;
import me.uc_hussein.ultraslogin.common.model.Account;
import me.uc_hussein.ultraslogin.common.model.AccountType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceTests {
    /** In-memory repository used to test the service without a database. */
    static final class MemoryRepo implements AccountRepository {
        final Map<Long, Account> rows = new HashMap<>();
        long next = 1;

        public Optional<Account> findByUuid(UUID u) {
            return rows.values().stream().filter(a -> a.uuid().equals(u)).findFirst().map(Account::copy);
        }
        public Optional<Account> findByName(String n, AccountType t) {
            return rows.values().stream().filter(a -> a.usernameLower().equals(n) && a.type() == t).findFirst().map(Account::copy);
        }
        public Optional<Account> findByNameAny(String n) {
            return rows.values().stream().filter(a -> a.usernameLower().equals(n)).findFirst().map(Account::copy);
        }
        public Account insert(Account a) { a.setId(next++); rows.put(a.id(), a.copy()); return a; }
        public void update(Account a) { rows.put(a.id(), a.copy()); }
        public void resetAuthenticatedFlags() { rows.values().forEach(a -> a.setAuthenticated(false)); }
        public long count() { return rows.size(); }
    }

    private final Executor direct = Runnable::run;

    @Test
    void createFindSaveRoundTrip() {
        AccountService svc = new AccountService(new MemoryRepo(), direct);
        Account a = new Account(UUID.randomUUID(), "Steve", AccountType.CRACKED, 1);
        svc.create(a).join();
        assertTrue(a.id() > 0);
        a.setLanguage("ar");
        svc.save(a).join();
        Account loaded = svc.findByName("STEVE", AccountType.CRACKED).join().orElseThrow();
        assertEquals("ar", loaded.language());
        assertTrue(svc.findByName("steve", AccountType.PREMIUM).join().isEmpty(), "type is part of the identity");
    }

    @Test
    void crackedAndPremiumWithTheSameNameAreDistinctAccounts() {
        AccountService svc = new AccountService(new MemoryRepo(), direct);
        Account premium = new Account(UUID.randomUUID(), "Hussein", AccountType.PREMIUM, 1);
        Account cracked = new Account(UUID.randomUUID(), "Hussein", AccountType.CRACKED, 2);
        cracked.setPasswordHash("$argon2id$x");
        svc.create(premium).join();
        svc.create(cracked).join();
        Account foundCracked = svc.findByName("hussein", AccountType.CRACKED).join().orElseThrow();
        assertEquals(cracked.uuid(), foundCracked.uuid());
        assertNotEquals(premium.uuid(), cracked.uuid(), "a cracked connection never inherits the premium UUID/account");
    }

    @Test
    void savingASnapshotIsNotAffectedByLaterMutation() {
        MemoryRepo repo = new MemoryRepo();
        AccountService svc = new AccountService(repo, direct);
        Account a = new Account(UUID.randomUUID(), "A", AccountType.CRACKED, 1);
        svc.create(a).join();
        a.setPasswordHash("h1");
        svc.save(a).join();
        a.setPasswordHash("h2");
        assertEquals("h1", repo.rows.get(a.id()).passwordHash());
    }

    @Test
    void toStringNeverContainsHashes() {
        Account a = new Account(UUID.randomUUID(), "A", AccountType.CRACKED, 1);
        a.setPasswordHash("SECRET-HASH");
        a.setSessionTokenHash("SECRET-TOKEN");
        assertFalse(a.toString().contains("SECRET"));
    }
}
