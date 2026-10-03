package me.uc_hussein.ultraslogin.common.bridge;

/** A bridge message was malformed, forged, stale or replayed. */
public final class BridgeException extends Exception {
    public BridgeException(String message) {
        super(message);
    }
}
