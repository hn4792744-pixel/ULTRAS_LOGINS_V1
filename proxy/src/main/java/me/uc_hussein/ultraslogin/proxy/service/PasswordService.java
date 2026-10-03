package me.uc_hussein.ultraslogin.proxy.service;

import me.uc_hussein.ultraslogin.common.config.Cfg;
import me.uc_hussein.ultraslogin.common.security.PasswordBlacklist;
import me.uc_hussein.ultraslogin.common.security.PasswordHasher;
import me.uc_hussein.ultraslogin.common.security.PasswordPolicy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Argon2id hashing/verification on a small dedicated pool (memory-hard work must never run on event threads). */
public final class PasswordService {
    private final Executor crypto;
    private final Path blacklistFile;
    private volatile PasswordHasher hasher = new PasswordHasher(new PasswordHasher.Params(32768, 3, 1));
    private volatile PasswordPolicy policy = new PasswordPolicy(6, 72, false, false, false, false, Set.of());

    public PasswordService(Executor cryptoExecutor, Path blacklistFile) {
        this.crypto = cryptoExecutor;
        this.blacklistFile = blacklistFile;
    }

    public void reload(Cfg cfg, List<String> warnings) {
        String algo = cfg.string("password.algorithm").toUpperCase(Locale.ROOT);
        if (!algo.equals("ARGON2ID")) {
            warnings.add("config.yml: password.algorithm '" + algo + "' is not supported - using ARGON2ID");
        }
        try {
            hasher = new PasswordHasher(new PasswordHasher.Params(cfg.integer("password.argon2.memory-kb", 32768),
                    cfg.integer("password.argon2.iterations", 3), cfg.integer("password.argon2.parallelism", 1)));
        } catch (IllegalArgumentException e) {
            warnings.add("config.yml: password.argon2.* invalid - using memory 32768 KB, 3 iterations, parallelism 1");
            hasher = new PasswordHasher(new PasswordHasher.Params(32768, 3, 1));
        }
        Set<String> blacklist = Set.of();
        try {
            blacklist = PasswordBlacklist.load(blacklistFile);
        } catch (IOException e) {
            warnings.add("password blacklist could not be read: " + e.getMessage());
        }
        policy = PasswordPolicy.from(cfg, blacklist);
    }

    public PasswordPolicy policy() {
        return policy;
    }

    public PasswordPolicy.Result validate(String password, String username) {
        return policy.validate(password, username);
    }

    public CompletableFuture<String> hash(String password) {
        PasswordHasher h = hasher;
        return CompletableFuture.supplyAsync(() -> h.hash(password), crypto);
    }

    public CompletableFuture<Boolean> verify(String password, String hash) {
        PasswordHasher h = hasher;
        return CompletableFuture.supplyAsync(() -> h.verify(password, hash), crypto);
    }

    public boolean needsRehash(String hash) {
        return hasher.needsRehash(hash);
    }
}
