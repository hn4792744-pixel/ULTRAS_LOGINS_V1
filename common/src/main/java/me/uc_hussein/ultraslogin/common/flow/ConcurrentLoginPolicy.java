package me.uc_hussein.ultraslogin.common.flow;

import java.util.Locale;

/**
 * Velocity refuses two connections with the same UUID, so only DENY_NEW can be enforced safely.
 * KICK_OLD / ALLOW_BOTH are accepted in the config but fall back to DENY_NEW (see README).
 */
public enum ConcurrentLoginPolicy {
    DENY_NEW, KICK_OLD, ALLOW_BOTH;

    public static ConcurrentLoginPolicy parse(String s) {
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return DENY_NEW;
        }
    }

    /** The policy that is really enforced. */
    public ConcurrentLoginPolicy effective() {
        return DENY_NEW;
    }
}
