package me.uc_hussein.ultraslogin.common;

/** Sink for security events. Implementations must never receive passwords, hashes or keys. */
public interface SecurityLog {
    void event(String category, String message);

    /** Log that discards everything (tests). */
    SecurityLog NOOP = (c, m) -> { };
}
