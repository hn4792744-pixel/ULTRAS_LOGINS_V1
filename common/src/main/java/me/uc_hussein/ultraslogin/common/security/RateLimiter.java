package me.uc_hussein.ultraslogin.common.security;

import java.util.HashMap;
import java.util.Map;

/** Fixed-window rate limiter: at most {@code limit} acquisitions per key per window. Thread-safe. */
public final class RateLimiter {
    private static final class Window {
        long start;
        int count;
    }

    private final int limit;
    private final long windowMillis;
    private final Map<String, Window> windows = new HashMap<>();

    public RateLimiter(int limit, long windowMillis) {
        this.limit = Math.max(1, limit);
        this.windowMillis = Math.max(1, windowMillis);
    }

    public synchronized boolean tryAcquire(String key, long now) {
        if (windows.size() > 4096) {
            windows.values().removeIf(w -> now - w.start >= windowMillis);
        }
        Window w = windows.computeIfAbsent(key, k -> {
            Window nw = new Window();
            nw.start = now;
            return nw;
        });
        if (now - w.start >= windowMillis) {
            w.start = now;
            w.count = 0;
        }
        if (w.count >= limit) {
            return false;
        }
        w.count++;
        return true;
    }

    public synchronized void reset(String key) {
        windows.remove(key);
    }
}
