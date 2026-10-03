package me.uc_hussein.ultraslogin.common.db;

import me.uc_hussein.ultraslogin.common.model.Account;
import me.uc_hussein.ultraslogin.common.model.AccountType;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Asynchronous account access: every call runs on the database executor, never on a proxy/event thread. */
public final class AccountService {
    private final AccountRepository repo;
    private final Executor executor;

    public AccountService(AccountRepository repo, Executor dbExecutor) {
        this.repo = repo;
        this.executor = dbExecutor;
    }

    public CompletableFuture<Optional<Account>> findByUuid(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> repo.findByUuid(uuid), executor);
    }

    public CompletableFuture<Optional<Account>> findByName(String name, AccountType type) {
        return CompletableFuture.supplyAsync(() -> repo.findByName(name.toLowerCase(java.util.Locale.ROOT), type), executor);
    }

    public CompletableFuture<Optional<Account>> findByNameAny(String name) {
        return CompletableFuture.supplyAsync(() -> repo.findByNameAny(name.toLowerCase(java.util.Locale.ROOT)), executor);
    }

    public CompletableFuture<Account> create(Account account) {
        Account copy = account.copy();
        return CompletableFuture.supplyAsync(() -> {
            repo.insert(copy);
            return copy;
        }, executor).thenApply(saved -> {
            account.setId(saved.id());
            return account;
        });
    }

    /** Persists a snapshot of {@code account}. */
    public CompletableFuture<Void> save(Account account) {
        Account snapshot = account.copy();
        return CompletableFuture.runAsync(() -> repo.update(snapshot), executor);
    }

    public CompletableFuture<Void> resetAuthenticatedFlags() {
        return CompletableFuture.runAsync(repo::resetAuthenticatedFlags, executor);
    }

    public CompletableFuture<Long> count() {
        return CompletableFuture.supplyAsync(repo::count, executor);
    }
}
