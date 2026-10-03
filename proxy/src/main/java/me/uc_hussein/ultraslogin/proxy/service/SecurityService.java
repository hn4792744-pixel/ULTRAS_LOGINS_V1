package me.uc_hussein.ultraslogin.proxy.service;

import me.uc_hussein.ultraslogin.common.SecurityLog;
import me.uc_hussein.ultraslogin.common.security.AttemptTracker;
import me.uc_hussein.ultraslogin.proxy.PlayerContext;
import me.uc_hussein.ultraslogin.proxy.config.Settings;

/** Brute-force protection per account and per IP, plus security-event logging helpers. */
public final class SecurityService {
    private final SecurityLog log;
    private volatile AttemptTracker accountAttempts;
    private volatile AttemptTracker ipAttempts;
    private int lastMax = -1;
    private long lastLockout = -1;

    public SecurityService(SecurityLog log) {
        this.log = log;
    }

    public synchronized void reconfigure(Settings s) {
        if (s.maxLoginAttempts != lastMax || s.lockoutMillis != lastLockout) {
            accountAttempts = new AttemptTracker(s.maxLoginAttempts, s.lockoutMillis);
            ipAttempts = new AttemptTracker(s.maxLoginAttempts * 3, s.lockoutMillis);   // shared IPs get more room
            lastMax = s.maxLoginAttempts;
            lastLockout = s.lockoutMillis;
        }
    }

    private static String accKey(PlayerContext c) {
        return "acc:" + c.uuid();
    }

    private static String ipKey(PlayerContext c) {
        return "ip:" + c.ipHash();
    }

    /** Remaining lock time in millis (0 = not locked). */
    public long lockRemaining(PlayerContext c) {
        long now = System.currentTimeMillis();
        return Math.max(accountAttempts.remainingLockMillis(accKey(c), now), ipAttempts.remainingLockMillis(ipKey(c), now));
    }

    /** Records a wrong password. Returns attempts left before lockout, or 0 if the account is now locked. */
    public int recordFailure(PlayerContext c, int maxAttempts) {
        long now = System.currentTimeMillis();
        AttemptTracker.Failure f = accountAttempts.recordFailure(accKey(c), now);
        ipAttempts.recordFailure(ipKey(c), now);
        if (f.lockedNow()) {
            log.event("SECURITY", "lockout for " + c.username() + " after too many wrong passwords");
            return 0;
        }
        return Math.max(0, maxAttempts - f.attempts());
    }

    public void recordSuccess(PlayerContext c) {
        accountAttempts.reset(accKey(c));
    }

    public void event(String category, String message) {
        log.event(category, message);
    }
}
