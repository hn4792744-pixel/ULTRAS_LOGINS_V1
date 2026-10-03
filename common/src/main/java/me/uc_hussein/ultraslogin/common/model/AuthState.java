package me.uc_hussein.ultraslogin.common.model;

/** Explicit login state machine. A plain boolean is never used to decide access. */
public enum AuthState {
    NEW,
    LANGUAGE_SELECTION,
    AUTHENTICATION_REQUIRED,
    AUTHENTICATING,
    AUTHENTICATED,
    PREMIUM_AUTHENTICATED,
    BEDROCK_AUTHENTICATED,
    SESSION_AUTHENTICATED;

    /** True for the four states that grant gameplay access. */
    public boolean isAuthenticated() {
        return this == AUTHENTICATED || this == PREMIUM_AUTHENTICATED
                || this == BEDROCK_AUTHENTICATED || this == SESSION_AUTHENTICATED;
    }

    public boolean canTransitionTo(AuthState next) {
        if (next == null || next == this) {
            return false;
        }
        switch (this) {
            case NEW:
                return next != AUTHENTICATING && next != AUTHENTICATED;
            case LANGUAGE_SELECTION:
                return next == AUTHENTICATION_REQUIRED || next == PREMIUM_AUTHENTICATED
                        || next == BEDROCK_AUTHENTICATED || next == SESSION_AUTHENTICATED;
            case AUTHENTICATION_REQUIRED:
                return next == AUTHENTICATING || next == AUTHENTICATED || next == LANGUAGE_SELECTION;
            case AUTHENTICATING:
                return next == AUTHENTICATED || next == AUTHENTICATION_REQUIRED;
            default: // authenticated states: only a logout / invalidation leaves them
                return next == AUTHENTICATION_REQUIRED || next == LANGUAGE_SELECTION;
        }
    }
}
