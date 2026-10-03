package me.uc_hussein.ultraslogin.common;

import me.uc_hussein.ultraslogin.common.security.AttemptTracker;
import me.uc_hussein.ultraslogin.common.security.IpHasher;
import me.uc_hussein.ultraslogin.common.security.PasswordHasher;
import me.uc_hussein.ultraslogin.common.security.PasswordPolicy;
import me.uc_hussein.ultraslogin.common.security.PasswordPolicy.Result;
import me.uc_hussein.ultraslogin.common.security.RateLimiter;
import me.uc_hussein.ultraslogin.common.security.SecureTokens;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SecurityTests {
    private final PasswordHasher hasher = new PasswordHasher(new PasswordHasher.Params(1024, 2, 1)); // small = fast tests

    // ---- Password / Argon2id -------------------------------------------------------------

    @Test
    void correctPasswordVerifies() {
        String h = hasher.hash("MyPassword123");
        assertTrue(hasher.verify("MyPassword123", h));
    }

    @Test
    void incorrectPasswordFails() {
        String h = hasher.hash("MyPassword123");
        assertFalse(hasher.verify("mypassword123", h));
        assertFalse(hasher.verify("", h));
    }

    @Test
    void hashIsArgon2idPhcWithSaltAndNoPlaintext() {
        String h = hasher.hash("MyPassword123");
        assertTrue(h.startsWith("$argon2id$v=19$m=1024,t=2,p=1$"));
        assertFalse(h.contains("MyPassword123"));
        assertNotEquals(h, hasher.hash("MyPassword123"), "salt must make every hash unique");
    }

    @Test
    void malformedOrTamperedHashesNeverVerify() {
        assertFalse(hasher.verify("x", null));
        assertFalse(hasher.verify("x", "plaintext"));
        assertFalse(hasher.verify("x", "$argon2id$v=19$m=99999999,t=1,p=1$AAAAAAAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
        String h = hasher.hash("secret");
        assertFalse(hasher.verify("secret", h.substring(0, h.length() - 3) + "AAA"));
    }

    @Test
    void rehashDetectsWeakerParameters() {
        String weak = hasher.hash("pw");
        PasswordHasher stronger = new PasswordHasher(new PasswordHasher.Params(2048, 3, 1));
        assertTrue(stronger.needsRehash(weak));
        assertFalse(hasher.needsRehash(weak));
        assertTrue(stronger.verify("pw", weak), "old hashes still verify after a parameter change");
    }

    // ---- Registration validation -------------------------------------------------------------

    @Test
    void passwordPolicyRules() {
        PasswordPolicy p = new PasswordPolicy(6, 72, true, true, true, false, Set.of("password1"));
        assertEquals(Result.EMPTY, p.validate("", "Steve"));
        assertEquals(Result.TOO_SHORT, p.validate("Ab1", "Steve"));
        assertEquals(Result.TOO_LONG, p.validate("Aa1".repeat(30), "Steve"));
        assertEquals(Result.NEEDS_UPPERCASE, p.validate("abcdef1", "Steve"));
        assertEquals(Result.NEEDS_LOWERCASE, p.validate("ABCDEF1", "Steve"));
        assertEquals(Result.NEEDS_NUMBER, p.validate("Abcdefg", "Steve"));
        assertEquals(Result.BLACKLISTED, new PasswordPolicy(6, 72, false, false, false, false, Set.of("password1")).validate("Password1", "Steve"));
        assertEquals(Result.SAME_AS_USERNAME, new PasswordPolicy(3, 72, false, false, false, false, Set.of()).validate("steve", "Steve"));
        assertEquals(Result.INVALID_CHARACTERS, p.validate("Abc 123", "Steve"));
        assertEquals(Result.OK, p.validate("MyPassword123", "Steve"));
        assertEquals(Result.NEEDS_SYMBOL, new PasswordPolicy(6, 72, false, false, false, true, Set.of()).validate("Abcdef1", "x"));
    }

    // ---- Brute force / rate limit -------------------------------------------------------------

    @Test
    void bruteForceLocksAfterMaxAttemptsAndResetsOnSuccess() {
        AttemptTracker t = new AttemptTracker(3, 5 * 60_000L);
        long now = 1_000_000L;
        assertFalse(t.recordFailure("acc", now).lockedNow());
        assertFalse(t.recordFailure("acc", now).lockedNow());
        assertTrue(t.recordFailure("acc", now).lockedNow());
        assertTrue(t.isLocked("acc", now + 1000));
        assertTrue(t.remainingLockMillis("acc", now + 1000) > 0);
        assertFalse(t.isLocked("acc", now + 5 * 60_000L + 1), "lock expires");
        t.recordFailure("acc", now);
        t.reset("acc");
        assertFalse(t.recordFailure("acc", now).lockedNow(), "success resets the counter");
        assertFalse(t.isLocked("other", now), "keys are independent");
    }

    @Test
    void rateLimiterAllowsLimitPerWindow() {
        RateLimiter r = new RateLimiter(2, 1000);
        assertTrue(r.tryAcquire("p", 0));
        assertTrue(r.tryAcquire("p", 10));
        assertFalse(r.tryAcquire("p", 20));
        assertTrue(r.tryAcquire("q", 20));
        assertTrue(r.tryAcquire("p", 1001), "window rolls over");
    }

    @Test
    void tokensAreRandomAndIpHashIsKeyed() {
        assertNotEquals(SecureTokens.newToken(), SecureTokens.newToken());
        assertEquals(43, SecureTokens.newToken().length());
        IpHasher a = new IpHasher("0123456789abcdef0123".getBytes());
        IpHasher b = new IpHasher("another-secret-key-xx".getBytes());
        assertEquals(a.hash("1.2.3.4"), a.hash("1.2.3.4"));
        assertNotEquals(a.hash("1.2.3.4"), a.hash("1.2.3.5"));
        assertNotEquals(a.hash("1.2.3.4"), b.hash("1.2.3.4"));
        assertFalse(a.hash("1.2.3.4").contains("1.2.3.4"));
    }
}
