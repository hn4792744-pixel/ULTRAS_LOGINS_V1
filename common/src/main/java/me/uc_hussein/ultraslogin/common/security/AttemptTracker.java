package me.uc_hussein.ultraslogin.common.security;

import java.util.HashMap;
import java.util.Map;

/** Brute-force protection: after {@code maxAttempts} failures a key is locked for {@code lockoutMillis}. */
public final class AttemptTracker {
    public record Failure(int attempts, boolean lockedNow, long lockedUntil) {
    }

    private static final class Entry {
        int count;
        long lockedUntil;
        long lastFailure;
    }

    private final int maxAttempts;
    private final long lockoutMillis;
    private final long windowMillis;
    private final Map<String, Entry> entries = new HashMap<>();

    public AttemptTracker(int maxAttempts, long lockoutMillis) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.lockoutMillis = Math.max(1, lockoutMillis);
        this.windowMillis = Math.max(this.lockoutMillis, 10 * 60_000L);
    }

    public synchronized boolean isLocked(String key, long now) {
        return remainingLockMillis(key, now) > 0;
    }

    public synchronized long remainingLockMillis(String key, long now) {
        Entry e = entries.get(key);
        return e == null ? 0 : Math.max(0, e.lockedUntil - now);
    }

    public synchronized Failure recordFailure(String key, long now) {
        cleanup(now);
        Entry e = entries.computeIfAbsent(key, k -> new Entry());
        if (now - e.lastFailure > windowMillis) {
            e.count = 0;
        }
        e.lastFailure = now;
        e.count++;
        boolean locked = false;
        if (e.count >= maxAttempts) {
            e.lockedUntil = now + lockoutMillis;
            e.count = 0;
            locked = true;
        }
        return new Failure(e.count, locked, e.lockedUntil);
    }

    public synchronized void reset(String key) {
        entries.remove(key);
    }

    private void cleanup(long now) {
        if (entries.size() < 1024) {
            return;
        }
        entries.values().removeIf(e -> now - e.lastFailure > windowMillis && e.lockedUntil < now);
    }
}
