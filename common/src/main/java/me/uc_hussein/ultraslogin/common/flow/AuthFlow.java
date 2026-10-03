package me.uc_hussein.ultraslogin.common.flow;

import me.uc_hussein.ultraslogin.common.model.Account;
import me.uc_hussein.ultraslogin.common.model.AccountType;
import me.uc_hussein.ultraslogin.common.model.AuthMode;
import me.uc_hussein.ultraslogin.common.model.AuthState;
import me.uc_hussein.ultraslogin.common.session.SessionService;

/**
 * Pure decision logic for what happens when a player connects. No platform types, fully unit-tested.
 * <pre>
 * PREMIUM (verified by Mojang)          -> PREMIUM_AUTHENTICATED, no register/login, no auth world
 * BEDROCK, require-authentication=false -> BEDROCK_AUTHENTICATED
 * CRACKED / BEDROCK(auth enabled):
 *   not registered                      -> AUTHENTICATION_REQUIRED + REGISTER
 *   valid session                       -> SESSION_AUTHENTICATED
 *   IP changed / expired / none         -> AUTHENTICATION_REQUIRED + LOGIN
 * A first-time player (no language yet) goes through LANGUAGE_SELECTION first.
 * </pre>
 */
public final class AuthFlow {
    public enum Reason { NONE, FIRST_JOIN, UNREGISTERED, NO_SESSION, SESSION_EXPIRED, IP_CHANGED }

    /**
     * @param kind              how the connection was verified (never inferred from the username)
     * @param account           stored account, may be null
     * @param session           result of the session check (ignored for premium / bedrock without auth)
     * @param languageSelected  whether the player already chose a language
     */
    public record Input(AccountType kind, boolean bedrockRequiresAuth, Account account,
                        SessionService.Result session, boolean languageSelected) {
    }

    public record Decision(AuthState initialState, AuthState targetState, AuthMode mode, Reason reason, boolean needsLanguage) {
        /** True when the player may enter the main server without ever seeing the authentication world. */
        public boolean skipsAuthWorld() {
            return mode == AuthMode.NONE;
        }
    }

    private AuthFlow() {
    }

    public static Decision decide(Input in) {
        AuthState target;
        AuthMode mode = AuthMode.NONE;
        Reason reason = Reason.NONE;

        if (in.kind() == AccountType.PREMIUM) {
            target = AuthState.PREMIUM_AUTHENTICATED;
        } else if (in.kind() == AccountType.BEDROCK && !in.bedrockRequiresAuth()) {
            target = AuthState.BEDROCK_AUTHENTICATED;
        } else {
            boolean registered = in.account() != null && in.account().isRegistered();
            if (!registered) {
                target = AuthState.AUTHENTICATION_REQUIRED;
                mode = AuthMode.REGISTER;
                reason = Reason.UNREGISTERED;
            } else {
                switch (in.session()) {
                    case VALID -> target = AuthState.SESSION_AUTHENTICATED;
                    case IP_CHANGED -> {
                        target = AuthState.AUTHENTICATION_REQUIRED;
                        mode = AuthMode.LOGIN;
                        reason = Reason.IP_CHANGED;
                    }
                    case EXPIRED -> {
                        target = AuthState.AUTHENTICATION_REQUIRED;
                        mode = AuthMode.LOGIN;
                        reason = Reason.SESSION_EXPIRED;
                    }
                    default -> {
                        target = AuthState.AUTHENTICATION_REQUIRED;
                        mode = AuthMode.LOGIN;
                        reason = Reason.NO_SESSION;
                    }
                }
            }
        }
        boolean needsLanguage = !in.languageSelected();
        AuthState initial = needsLanguage ? AuthState.LANGUAGE_SELECTION : target;
        if (needsLanguage && reason == Reason.NONE) {
            reason = Reason.FIRST_JOIN;
        }
        return new Decision(initial, target, mode, reason, needsLanguage);
    }
}
