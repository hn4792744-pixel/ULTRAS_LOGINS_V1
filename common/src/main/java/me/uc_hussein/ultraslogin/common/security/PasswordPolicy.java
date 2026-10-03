package me.uc_hussein.ultraslogin.common.security;

import me.uc_hussein.ultraslogin.common.config.Cfg;

import java.util.Locale;
import java.util.Set;

/** Configurable password rules. */
public final class PasswordPolicy {
    public enum Result { OK, EMPTY, TOO_SHORT, TOO_LONG, NEEDS_UPPERCASE, NEEDS_LOWERCASE, NEEDS_NUMBER, NEEDS_SYMBOL, BLACKLISTED, SAME_AS_USERNAME, INVALID_CHARACTERS }

    private final int min;
    private final int max;
    private final boolean upper;
    private final boolean lower;
    private final boolean number;
    private final boolean symbol;
    private final Set<String> blacklist;

    public PasswordPolicy(int min, int max, boolean upper, boolean lower, boolean number, boolean symbol, Set<String> blacklistLowercase) {
        this.min = min;
        this.max = max;
        this.upper = upper;
        this.lower = lower;
        this.number = number;
        this.symbol = symbol;
        this.blacklist = blacklistLowercase == null ? Set.of() : blacklistLowercase;
    }

    public static PasswordPolicy from(Cfg c, Set<String> blacklist) {
        int min = Math.max(1, c.integer("password.minimum-length", 6));
        int max = Math.max(min, c.integer("password.maximum-length", 72));
        return new PasswordPolicy(min, max, c.bool("password.require-uppercase", false), c.bool("password.require-lowercase", false),
                c.bool("password.require-number", false), c.bool("password.require-symbol", false), blacklist);
    }

    public int minimum() { return min; }
    public int maximum() { return max; }

    public Result validate(String pw, String username) {
        if (pw == null || pw.isEmpty()) {
            return Result.EMPTY;
        }
        if (pw.length() < min) {
            return Result.TOO_SHORT;
        }
        if (pw.length() > max) {
            return Result.TOO_LONG;
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasNumber = false;
        boolean hasSymbol = false;
        for (int i = 0; i < pw.length(); i++) {
            char ch = pw.charAt(i);
            if (Character.isISOControl(ch) || Character.isWhitespace(ch)) {
                return Result.INVALID_CHARACTERS;
            }
            if (Character.isUpperCase(ch)) {
                hasUpper = true;
            } else if (Character.isLowerCase(ch)) {
                hasLower = true;
            } else if (Character.isDigit(ch)) {
                hasNumber = true;
            } else {
                hasSymbol = true;
            }
        }
        if (upper && !hasUpper) {
            return Result.NEEDS_UPPERCASE;
        }
        if (lower && !hasLower) {
            return Result.NEEDS_LOWERCASE;
        }
        if (number && !hasNumber) {
            return Result.NEEDS_NUMBER;
        }
        if (symbol && !hasSymbol) {
            return Result.NEEDS_SYMBOL;
        }
        String low = pw.toLowerCase(Locale.ROOT);
        if (username != null && low.equals(username.toLowerCase(Locale.ROOT))) {
            return Result.SAME_AS_USERNAME;
        }
        if (blacklist.contains(low)) {
            return Result.BLACKLISTED;
        }
        return Result.OK;
    }
}
