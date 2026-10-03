package me.uc_hussein.ultraslogin.common.session;

import me.uc_hussein.ultraslogin.common.config.Cfg;
import me.uc_hussein.ultraslogin.common.model.Account;
import me.uc_hussein.ultraslogin.common.security.SecureTokens;

/**
 * Session rules. A session lets a player reconnect without /login. It starts when the player logs in and is
 * refreshed when they disconnect, so "duration" is measured from the last disconnect. The IP is only an extra layer.
 */
public final class SessionService {
    public enum Result { VALID, NO_SESSION, EXPIRED, IP_CHANGED, DISABLED }

    public record Config(boolean enabled, long durationMillis, boolean bindToIp, boolean requireLoginOnIpChange,
                         boolean requireLoginAfterTimeout) {
        public static Config from(Cfg c) {
            return new Config(c.bool("sessions.enabled", true),
                    Math.max(0, c.number("sessions.duration-minutes", 1)) * 60_000L,
                    c.bool("sessions.bind-to-ip", true),
                    c.bool("sessions.require-login-on-ip-change", true),
                    c.bool("sessions.require-login-after-timeout", true));
        }
    }

    private final Config cfg;

    public SessionService(Config cfg) {
        this.cfg = cfg;
    }

    /** Evaluates the stored session of {@code a} for a connection from {@code ipHash}. Does not modify anything. */
    public Result check(Account a, String ipHash, long now) {
        if (!cfg.enabled()) {
            return Result.DISABLED;
        }
        if (a == null || a.sessionTokenHash() == null || a.sessionExpiresAt() == null) {
            return Result.NO_SESSION;
        }
        if (cfg.bindToIp() && cfg.requireLoginOnIpChange()
                && (a.lastIpHash() == null || !a.lastIpHash().equals(ipHash))) {
            return Result.IP_CHANGED;
        }
        if (now > a.sessionExpiresAt() && cfg.requireLoginAfterTimeout()) {
            return Result.EXPIRED;
        }
        return Result.VALID;
    }

    /** Starts a new session on login/registration. Returns the random identifier (only its hash is stored). */
    public String create(Account a, String ipHash, long now) {
        String token = SecureTokens.newToken();
        a.setSessionTokenHash(SecureTokens.sha256Hex(token));
        a.setSessionCreatedAt(now);
        a.setSessionExpiresAt(now + cfg.durationMillis());
        a.setLastIpHash(ipHash);
        a.setLastLogin(now);
        return token;
    }

    /** Called on disconnect of an authenticated player: the countdown starts now. */
    public void refreshOnDisconnect(Account a, long now) {
        if (cfg.enabled() && a.sessionTokenHash() != null) {
            a.setSessionExpiresAt(now + cfg.durationMillis());
        }
    }

    /** Revokes the session (unregister, admin action, IP change, logout). */
    public void invalidate(Account a) {
        a.setSessionTokenHash(null);
        a.setSessionCreatedAt(null);
        a.setSessionExpiresAt(null);
    }

    public Config config() {
        return cfg;
    }
}
