package me.uc_hussein.ultraslogin.common;

import me.uc_hussein.ultraslogin.common.flow.AuthFlow;
import me.uc_hussein.ultraslogin.common.flow.AuthFlow.Reason;
import me.uc_hussein.ultraslogin.common.flow.CommandPolicy;
import me.uc_hussein.ultraslogin.common.flow.ConcurrentLoginPolicy;
import me.uc_hussein.ultraslogin.common.flow.MojangLookup;
import me.uc_hussein.ultraslogin.common.flow.PremiumPolicy;
import me.uc_hussein.ultraslogin.common.flow.PremiumPolicy.Mode;
import me.uc_hussein.ultraslogin.common.model.Account;
import me.uc_hussein.ultraslogin.common.model.AccountType;
import me.uc_hussein.ultraslogin.common.model.AuthMode;
import me.uc_hussein.ultraslogin.common.model.AuthState;
import me.uc_hussein.ultraslogin.common.session.SessionService;
import me.uc_hussein.ultraslogin.common.session.SessionService.Result;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FlowTests {
    private static final long MIN = 60_000L;
    private final SessionService sessions = new SessionService(new SessionService.Config(true, MIN, true, true, true));

    private static Account registered(String name, String lang) {
        Account a = new Account(UUID.randomUUID(), name, AccountType.CRACKED, 0);
        a.setPasswordHash("$argon2id$hash");
        a.setLanguage(lang);
        return a;
    }

    private static AuthFlow.Decision decide(AccountType kind, boolean bedrockAuth, Account a, Result s, boolean lang) {
        return AuthFlow.decide(new AuthFlow.Input(kind, bedrockAuth, a, s, lang));
    }

    // ---- Premium -------------------------------------------------------------------------

    @Test
    void verifiedPremiumSkipsEverything() {
        var d = decide(AccountType.PREMIUM, false, null, Result.NO_SESSION, true);
        assertEquals(AuthState.PREMIUM_AUTHENTICATED, d.initialState());
        assertEquals(AuthMode.NONE, d.mode());
        assertTrue(d.skipsAuthWorld());
    }

    private PremiumPolicy policy(PremiumPolicy.FailureMode f) {
        return new PremiumPolicy(new PremiumPolicy.Config(true, true, f, PremiumPolicy.RegisteredCrackedPolicy.KEEP_CRACKED));
    }

    @Test
    void premiumNameIsForcedThroughMojangAuthSoACrackedClientCannotUseIt() {
        var lookup = new MojangLookup(MojangLookup.Status.PREMIUM, UUID.randomUUID());
        assertEquals(Mode.FORCE_ONLINE, policy(PremiumPolicy.FailureMode.KICK).decide("Hussein", null, lookup));
    }

    @Test
    void knownPremiumAccountIsNeverDowngradedEvenIfMojangIsDown() {
        Account premium = new Account(UUID.randomUUID(), "Hussein", AccountType.PREMIUM, 0);
        PremiumPolicy p = policy(PremiumPolicy.FailureMode.CRACKED);
        assertFalse(p.needsLookup("Hussein", premium));
        assertEquals(Mode.FORCE_ONLINE, p.decide("Hussein", premium, null));
    }

    @Test
    void mojangFailureNeverGrantsAccessByDefault() {
        var err = new MojangLookup(MojangLookup.Status.ERROR, null);
        assertEquals(Mode.DENY_VERIFICATION_FAILED, policy(PremiumPolicy.FailureMode.KICK).decide("Steve", null, err));
        assertEquals(Mode.DENY_VERIFICATION_FAILED, policy(PremiumPolicy.FailureMode.KICK).decide("Steve", null, null));
        assertEquals(Mode.FORCE_OFFLINE, policy(PremiumPolicy.FailureMode.CRACKED).decide("Steve", null, err));
    }

    @Test
    void unknownNameIsCrackedAndInvalidNamesAreDenied() {
        var none = new MojangLookup(MojangLookup.Status.NOT_PREMIUM, null);
        assertEquals(Mode.FORCE_OFFLINE, policy(PremiumPolicy.FailureMode.KICK).decide("SomeCracked", null, none));
        assertEquals(Mode.DENY_INVALID_NAME, policy(PremiumPolicy.FailureMode.KICK).decide("bad name!", null, none));
        assertEquals(Mode.DENY_INVALID_NAME, policy(PremiumPolicy.FailureMode.KICK).decide(".BedrockLike", null, none));
    }

    @Test
    void registeredCrackedNameKeepsWorkingWithoutExternalRequest() {
        PremiumPolicy p = policy(PremiumPolicy.FailureMode.KICK);
        Account cracked = registered("OldPlayer", "en");
        assertFalse(p.needsLookup("OldPlayer", cracked));
        assertEquals(Mode.FORCE_OFFLINE, p.decide("OldPlayer", cracked, null));
        assertTrue(p.needsLookup("NewPlayer", null));
    }

    @Test
    void mojangResponseParsing() {
        var ok = MojangLookup.fromResponse(200, "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}");
        assertEquals(MojangLookup.Status.PREMIUM, ok.status());
        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), ok.uuid());
        assertEquals(MojangLookup.Status.NOT_PREMIUM, MojangLookup.fromResponse(404, "").status());
        assertEquals(MojangLookup.Status.NOT_PREMIUM, MojangLookup.fromResponse(204, null).status());
        assertEquals(MojangLookup.Status.ERROR, MojangLookup.fromResponse(429, "").status());
        assertEquals(MojangLookup.Status.ERROR, MojangLookup.fromResponse(500, "x").status());
        assertEquals(MojangLookup.Status.ERROR, MojangLookup.fromResponse(200, "<html>").status());
    }

    // ---- Bedrock -------------------------------------------------------------------------

    @Test
    void bedrockWithAuthDisabledIsAuthenticatedAndSkipsAuthWorld() {
        var d = decide(AccountType.BEDROCK, false, null, Result.NO_SESSION, true);
        assertEquals(AuthState.BEDROCK_AUTHENTICATED, d.initialState());
        assertTrue(d.skipsAuthWorld());
    }

    @Test
    void bedrockWithAuthEnabledFollowsTheCrackedRulesAndIsNeverPremium() {
        var reg = decide(AccountType.BEDROCK, true, null, Result.NO_SESSION, true);
        assertEquals(AuthMode.REGISTER, reg.mode());
        assertEquals(AuthState.AUTHENTICATION_REQUIRED, reg.initialState());
        Account a = registered(".BedrockGuy", "en");
        assertEquals(AuthMode.LOGIN, decide(AccountType.BEDROCK, true, a, Result.NO_SESSION, true).mode());
        assertEquals(AuthState.SESSION_AUTHENTICATED, decide(AccountType.BEDROCK, true, a, Result.VALID, true).initialState());
        assertNotEquals(AuthState.PREMIUM_AUTHENTICATED, reg.targetState());
    }

    // ---- Cracked / sessions -----------------------------------------------------------------

    @Test
    void crackedUnregisteredMustRegister() {
        var d = decide(AccountType.CRACKED, false, null, Result.NO_SESSION, true);
        assertEquals(AuthMode.REGISTER, d.mode());
        assertEquals(Reason.UNREGISTERED, d.reason());
        assertFalse(d.skipsAuthWorld());
    }

    @Test
    void crackedRegisteredMustLoginUnlessSessionValid() {
        Account a = registered("Steve", "en");
        assertEquals(AuthMode.LOGIN, decide(AccountType.CRACKED, false, a, Result.NO_SESSION, true).mode());
        var s = decide(AccountType.CRACKED, false, a, Result.VALID, true);
        assertEquals(AuthState.SESSION_AUTHENTICATED, s.initialState());
        assertTrue(s.skipsAuthWorld());
    }

    @Test
    void sessionValidThenExpiredThenIpChanged() {
        Account a = registered("Steve", "en");
        long t0 = 10_000_000L;
        sessions.create(a, "ipA", t0);
        assertEquals(Result.VALID, sessions.check(a, "ipA", t0 + 30_000));
        assertEquals(Result.EXPIRED, sessions.check(a, "ipA", t0 + MIN + 1));
        assertEquals(Result.IP_CHANGED, sessions.check(a, "ipB", t0 + 1000));
        var d = decide(AccountType.CRACKED, false, a, Result.IP_CHANGED, true);
        assertEquals(Reason.IP_CHANGED, d.reason());
        assertEquals(AuthMode.LOGIN, d.mode());
    }

    @Test
    void disconnectStartsTheSessionCountdown() {
        Account a = registered("Steve", "en");
        sessions.create(a, "ip", 0);
        sessions.refreshOnDisconnect(a, 5 * MIN);
        assertEquals(Result.VALID, sessions.check(a, "ip", 5 * MIN + 30_000));
        assertEquals(Result.EXPIRED, sessions.check(a, "ip", 6 * MIN + 1));
    }

    @Test
    void unregisterAndAdminUnregisterInvalidateTheSession() {
        Account a = registered("Steve", "en");
        sessions.create(a, "ip", 0);
        assertEquals(Result.VALID, sessions.check(a, "ip", 1));
        a.setPasswordHash(null);               // what unregister does ...
        sessions.invalidate(a);                // ... together with revoking the session
        assertEquals(Result.NO_SESSION, sessions.check(a, "ip", 1));
        assertEquals(AuthMode.REGISTER, decide(AccountType.CRACKED, false, a, Result.NO_SESSION, true).mode());
    }

    @Test
    void sessionSettingsAreRespected() {
        Account a = registered("Steve", "en");
        var noIp = new SessionService(new SessionService.Config(true, MIN, false, false, true));
        noIp.create(a, "ipA", 0);
        assertEquals(Result.VALID, noIp.check(a, "ipZ", 1));
        assertEquals(Result.DISABLED, new SessionService(new SessionService.Config(false, MIN, true, true, true)).check(a, "ipA", 1));
        var noTimeout = new SessionService(new SessionService.Config(true, MIN, true, true, false));
        noTimeout.create(a, "ipA", 0);
        assertEquals(Result.VALID, noTimeout.check(a, "ipA", 10 * MIN));
    }

    @Test
    void sessionStoresOnlyAHashOfTheRandomId() {
        Account a = registered("Steve", "en");
        String token = sessions.create(a, "ip", 0);
        assertNotNull(a.sessionTokenHash());
        assertNotEquals(token, a.sessionTokenHash());
        assertEquals(64, a.sessionTokenHash().length());
    }

    // ---- Language ---------------------------------------------------------------------------

    @Test
    void firstJoinRequiresLanguageSelectionBeforeAnythingElse() {
        var d = decide(AccountType.CRACKED, false, null, Result.NO_SESSION, false);
        assertEquals(AuthState.LANGUAGE_SELECTION, d.initialState());
        assertEquals(AuthState.AUTHENTICATION_REQUIRED, d.targetState());
        assertTrue(d.needsLanguage());
        assertEquals(Reason.UNREGISTERED, d.reason());
        var p = decide(AccountType.PREMIUM, false, null, Result.NO_SESSION, false);
        assertEquals(AuthState.LANGUAGE_SELECTION, p.initialState());
        assertEquals(AuthState.PREMIUM_AUTHENTICATED, p.targetState());
        assertEquals(Reason.FIRST_JOIN, p.reason());
    }

    @Test
    void reconnectDoesNotAskForLanguageAgainAndLanguagesAreIndependent() {
        Account a = registered("A", "en");
        Account b = registered("B", "ar");
        assertFalse(decide(AccountType.CRACKED, false, a, Result.NO_SESSION, a.hasLanguage()).needsLanguage());
        a.setLanguage("ar");
        assertEquals("ar", a.language());
        assertEquals("ar", b.language());
        b.setLanguage("en");
        assertEquals("ar", a.language(), "changing B must not change A");
    }

    // ---- State machine / commands / concurrency -------------------------------------------------

    @Test
    void stateMachineRejectsShortcuts() {
        assertTrue(AuthState.NEW.canTransitionTo(AuthState.LANGUAGE_SELECTION));
        assertFalse(AuthState.NEW.canTransitionTo(AuthState.AUTHENTICATED), "never straight to authenticated");
        assertFalse(AuthState.LANGUAGE_SELECTION.canTransitionTo(AuthState.AUTHENTICATED));
        assertTrue(AuthState.AUTHENTICATION_REQUIRED.canTransitionTo(AuthState.AUTHENTICATING));
        assertTrue(AuthState.AUTHENTICATING.canTransitionTo(AuthState.AUTHENTICATED));
        assertTrue(AuthState.AUTHENTICATED.canTransitionTo(AuthState.AUTHENTICATION_REQUIRED));
        assertTrue(AuthState.SESSION_AUTHENTICATED.isAuthenticated());
        assertFalse(AuthState.AUTHENTICATING.isAuthenticated());
        assertFalse(AuthState.LANGUAGE_SELECTION.isAuthenticated());
    }

    @Test
    void onlyConfiguredCommandsPassBeforeLogin() {
        Set<String> allowed = Set.of("login", "register", "language");
        assertTrue(CommandPolicy.allowed("login secret", allowed));
        assertTrue(CommandPolicy.allowed("/REGISTER setting", allowed));
        assertFalse(CommandPolicy.allowed("spawn", allowed));
        assertFalse(CommandPolicy.allowed("un_register", allowed), "an unauthenticated player must not unregister");
        assertFalse(CommandPolicy.allowed("minecraft:login x", allowed), "namespaced forms are not allowed");
        assertFalse(CommandPolicy.allowed("", allowed));
    }

    @Test
    void duplicateSessionPolicyIsAlwaysDenyNew() {
        assertEquals(ConcurrentLoginPolicy.DENY_NEW, ConcurrentLoginPolicy.parse("garbage"));
        assertEquals(ConcurrentLoginPolicy.DENY_NEW, ConcurrentLoginPolicy.KICK_OLD.effective());
        assertEquals(ConcurrentLoginPolicy.DENY_NEW, ConcurrentLoginPolicy.ALLOW_BOTH.effective());
    }
}
