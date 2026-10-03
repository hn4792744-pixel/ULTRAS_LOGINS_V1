package me.uc_hussein.ultraslogin.common.flow;

import me.uc_hussein.ultraslogin.common.model.Account;
import me.uc_hussein.ultraslogin.common.model.AccountType;

/**
 * Decides, before login, whether a connection must prove Premium ownership (forced online mode) or
 * is a normal cracked connection. The username alone is never proof of ownership: a name that belongs
 * to a Premium account is forced through Mojang's session check, so a cracked client using it is rejected.
 */
public final class PremiumPolicy {
    public enum Mode { FORCE_ONLINE, FORCE_OFFLINE, DENY_INVALID_NAME, DENY_VERIFICATION_FAILED, DENY_CRACKED_DISABLED }

    public enum FailureMode { KICK, CRACKED }

    public enum RegisteredCrackedPolicy { KEEP_CRACKED, FORCE_PREMIUM }

    public record Config(boolean premiumEnabled, boolean crackedEnabled, FailureMode failureMode,
                         RegisteredCrackedPolicy registeredCrackedPolicy) {
    }

    private final Config cfg;

    public PremiumPolicy(Config cfg) {
        this.cfg = cfg;
    }

    /** True if a Mojang lookup is needed to decide (saves external requests). */
    public boolean needsLookup(String name, Account localAccount) {
        if (!cfg.premiumEnabled() || !MojangLookup.isValidJavaName(name)) {
            return false;
        }
        return localDecision(localAccount) == null;
    }

    private Mode localDecision(Account local) {
        if (local == null) {
            return null;
        }
        if (local.type() == AccountType.PREMIUM) {
            return Mode.FORCE_ONLINE;                       // never downgrade a verified premium name
        }
        if (local.type() == AccountType.CRACKED && local.isRegistered()
                && cfg.registeredCrackedPolicy() == RegisteredCrackedPolicy.KEEP_CRACKED) {
            return cfg.crackedEnabled() ? Mode.FORCE_OFFLINE : Mode.DENY_CRACKED_DISABLED;
        }
        return null;
    }

    /**
     * @param localAccount stored account for this username (any type), may be null
     * @param lookup       Mojang result, may be null if {@link #needsLookup} returned false
     */
    public Mode decide(String name, Account localAccount, MojangLookup lookup) {
        if (!MojangLookup.isValidJavaName(name)) {
            return Mode.DENY_INVALID_NAME;
        }
        if (!cfg.premiumEnabled()) {
            return cfg.crackedEnabled() ? Mode.FORCE_OFFLINE : Mode.DENY_CRACKED_DISABLED;
        }
        Mode local = localDecision(localAccount);
        if (local != null) {
            return local;
        }
        if (lookup == null || lookup.status() == MojangLookup.Status.ERROR) {
            // Never weaken security because an external service is down.
            if (cfg.failureMode() == FailureMode.CRACKED && cfg.crackedEnabled()) {
                return Mode.FORCE_OFFLINE;
            }
            return Mode.DENY_VERIFICATION_FAILED;
        }
        if (lookup.status() == MojangLookup.Status.PREMIUM) {
            return Mode.FORCE_ONLINE;
        }
        return cfg.crackedEnabled() ? Mode.FORCE_OFFLINE : Mode.DENY_CRACKED_DISABLED;
    }
}
