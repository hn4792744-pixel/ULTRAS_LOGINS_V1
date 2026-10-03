package me.uc_hussein.ultraslogin.proxy.service;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import me.uc_hussein.ultraslogin.common.flow.AuthFlow;
import me.uc_hussein.ultraslogin.common.model.Account;
import me.uc_hussein.ultraslogin.common.model.AccountType;
import me.uc_hussein.ultraslogin.common.model.AuthMode;
import me.uc_hussein.ultraslogin.common.model.AuthState;
import me.uc_hussein.ultraslogin.common.security.PasswordPolicy;
import me.uc_hussein.ultraslogin.common.session.SessionService;
import me.uc_hussein.ultraslogin.proxy.PlayerContext;
import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;
import me.uc_hussein.ultraslogin.proxy.config.Settings;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates registration, login, sessions, unregistering and state changes. All database and hashing work
 * runs on dedicated executors; event/command threads only start the work and receive the result.
 */
public final class AuthenticationService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UltrasLoginPlugin plugin;

    public AuthenticationService(UltrasLoginPlugin plugin) {
        this.plugin = plugin;
    }

    // ================================================================== login pipeline

    private static String ipOf(Player p) {
        return p.getRemoteAddress() instanceof InetSocketAddress a && a.getAddress() != null
                ? a.getAddress().getHostAddress() : String.valueOf(p.getRemoteAddress());
    }

    /** Looks up local accounts of a name before login (premium account wins over a cracked one). */
    public CompletableFuture<Account> lookupLocal(String name) {
        return plugin.accounts().findByName(name, AccountType.PREMIUM).thenCompose(prem -> prem.isPresent()
                ? CompletableFuture.completedFuture(prem.get())
                : plugin.accounts().findByName(name, AccountType.CRACKED).thenApply(o -> o.orElse(null)));
    }

    /**
     * Builds the player's context from the stored account, the verified connection kind and the session rules.
     * Fails with {@link LoginDeniedException} (policy) or another exception (database problem -> fail closed).
     */
    public CompletableFuture<PlayerContext> onLogin(Player player, AccountType kind) {
        Settings s = plugin.settings();
        String ipHash = plugin.ipHasher().hash(ipOf(player));
        CompletableFuture<Optional<Account>> lookup = kind == AccountType.CRACKED
                ? plugin.accounts().findByName(player.getUsername(), AccountType.CRACKED)
                : plugin.accounts().findByUuid(player.getUniqueId());

        CompletableFuture<PlayerContext> pipeline = lookup.thenCompose(opt -> {
            long now = System.currentTimeMillis();
            Account acct = opt.orElse(null);
            if (kind == AccountType.CRACKED && acct != null && s.enforceUsernameCase && !acct.username().equals(player.getUsername())) {
                throw new LoginDeniedException("wrong-case", Placeholders.of().text("expected", acct.username()));
            }
            boolean created = acct == null;
            if (created) {
                acct = new Account(player.getUniqueId(), player.getUsername(), kind, now);
            } else if (!acct.username().equals(player.getUsername())) {
                acct.setUsername(player.getUsername());              // premium name change
            }
            SessionService.Result sess = plugin.sessions().check(acct, ipHash, now);
            AuthFlow.Decision d = AuthFlow.decide(new AuthFlow.Input(kind, s.bedrockRequireAuth, acct, sess, acct.hasLanguage()));
            PlayerContext ctx = buildContext(player, kind, ipHash, acct, d);

            if (d.reason() == AuthFlow.Reason.IP_CHANGED) {
                plugin.sessions().invalidate(acct);
                plugin.security().event("SESSION", "session of " + player.getUsername() + " invalidated: network (IP) changed");
            } else if (d.reason() == AuthFlow.Reason.SESSION_EXPIRED) {
                plugin.sessions().invalidate(acct);
            }
            if (d.skipsAuthWorld()) {
                acct.setLastLogin(now);
                acct.setLastIpHash(ipHash);
                acct.setAuthenticated(true);
            }
            CompletableFuture<?> persisted = created ? plugin.accounts().create(acct) : plugin.accounts().save(acct);
            return persisted.thenApply(x -> ctx);
        });

        boolean failOpen = kind == AccountType.PREMIUM || (kind == AccountType.BEDROCK && !s.bedrockRequireAuth);
        if (!failOpen) {
            return pipeline;                                          // cracked / bedrock-with-auth: database is required
        }
        // Mojang/Floodgate already proved identity: a database outage must not lock them out.
        return pipeline.handle((ctx, err) -> {
            if (err == null) {
                return ctx;
            }
            Throwable cause = err.getCause() != null ? err.getCause() : err;
            if (cause instanceof LoginDeniedException lde) {
                throw lde;
            }
            plugin.logger().error("Database problem while loading {} - continuing with defaults: {}", player.getUsername(), cause.toString());
            AuthFlow.Decision d = AuthFlow.decide(new AuthFlow.Input(kind, s.bedrockRequireAuth, null, SessionService.Result.NO_SESSION, true));
            return buildContext(player, kind, ipHash, null, d);
        });
    }

    private PlayerContext buildContext(Player player, AccountType kind, String ipHash, Account acct, AuthFlow.Decision d) {
        Settings s = plugin.settings();
        String lang = acct != null && acct.hasLanguage() ? acct.language() : s.defaultLanguage;
        PlayerContext ctx = new PlayerContext(player, kind, ipHash, lang);
        ctx.setAccount(acct);
        ctx.setMode(d.mode());
        ctx.setReason(d.reason());
        ctx.setAfterLanguage(d.targetState());
        ctx.setIdentityVerified(kind == AccountType.PREMIUM || (kind == AccountType.BEDROCK && !s.bedrockRequireAuth));
        ctx.transition(d.initialState());
        if (d.needsLanguage()) {
            ctx.setGui(s.firstJoinGui.equals("LANGUAGE") ? PlayerContext.GuiType.LANGUAGE : PlayerContext.GuiType.FIRST_JOIN);
            ctx.setGuiId(RANDOM.nextLong());
        }
        ctx.setPendingTeleport(ctx.mayLeaveAuthServer() ? s.spawn.backendTeleport() : s.authentication.backendTeleport());
        return ctx;
    }

    /** First time the player is on a backend server: tell them what is going on. */
    public void onFirstServerJoin(PlayerContext ctx, Player p) {
        if (ctx.state() != AuthState.LANGUAGE_SELECTION) {
            announce(ctx, p);
        }
    }

    private void announce(PlayerContext ctx, Player p) {
        if (ctx.announced()) {
            return;
        }
        ctx.setAnnounced(true);
        var m = plugin.messages();
        String lang = ctx.language();
        switch (ctx.state()) {
            case PREMIUM_AUTHENTICATED -> {
                p.sendMessage(m.component(lang, "premium-verified", null));
                plugin.security().event("PREMIUM", ctx.username() + " verified as Premium (Mojang session check)");
            }
            case BEDROCK_AUTHENTICATED -> {
                p.sendMessage(m.component(lang, "bedrock-welcome", null));
                plugin.security().event("BEDROCK", ctx.username() + " detected as Bedrock (Floodgate)");
            }
            case SESSION_AUTHENTICATED -> {
                p.sendMessage(m.component(lang, "session-restored", null));
                plugin.security().event("SESSION", ctx.username() + " reconnected with a valid session");
            }
            case AUTHENTICATION_REQUIRED -> plugin.reminders().begin(ctx, p);
            default -> { }
        }
        if (ctx.isAuthenticated()) {
            plugin.sounds().play(ctx, "success");
        }
    }

    /** Called when the mandatory language choice was made. */
    public void finishLanguage(PlayerContext ctx) {
        if (ctx.state() != AuthState.LANGUAGE_SELECTION || !ctx.transition(ctx.afterLanguage())) {
            return;
        }
        Player p = plugin.server().getPlayer(ctx.uuid()).orElse(null);
        if (p == null) {
            return;
        }
        announce(ctx, p);
        if (ctx.isAuthenticated()) {
            plugin.bridge().sync(ctx, false);
            plugin.teleports().route(ctx);
        }
    }

    // ================================================================== register

    public void register(PlayerContext ctx, Player p, String password, String confirm) {
        var m = plugin.messages();
        String lang = ctx.language();
        if (!ctx.usesPassword()) {
            p.sendMessage(m.component(lang, "register-not-needed", null));
            return;
        }
        if (ctx.state() != AuthState.AUTHENTICATION_REQUIRED || ctx.mode() != AuthMode.REGISTER) {
            p.sendMessage(m.component(lang, ctx.isAuthenticated() || ctx.mode() == AuthMode.LOGIN ? "register-already" : "busy", null));
            return;
        }
        if (!plugin.rateLimits().allowRegister(ctx.uuid(), ctx.ipHash())) {
            p.sendMessage(m.component(lang, "rate-limited", null));
            return;
        }
        if (!password.equals(confirm)) {
            fail(ctx, p, "register-password-mismatch", null);
            return;
        }
        PasswordPolicy.Result r = plugin.passwords().validate(password, ctx.username());
        if (r != PasswordPolicy.Result.OK) {
            fail(ctx, p, policyKey(r), Placeholders.of().text("min", String.valueOf(plugin.passwords().policy().minimum()))
                    .text("max", String.valueOf(plugin.passwords().policy().maximum())));
            return;
        }
        if (!ctx.transition(AuthState.AUTHENTICATING)) {
            p.sendMessage(m.component(lang, "busy", null));
            return;
        }
        plugin.passwords().hash(password).thenCompose(hash -> {
            Account a = ctx.account();
            long now = System.currentTimeMillis();
            a.setPasswordHash(hash);
            a.setRegisteredAt(now);
            plugin.sessions().create(a, ctx.ipHash(), now);
            a.setAuthenticated(true);
            return plugin.accounts().save(a);
        }).whenComplete((v, err) -> {
            if (err != null) {
                ctx.transition(AuthState.AUTHENTICATION_REQUIRED);
                plugin.logger().error("Registration failed for {}: {}", ctx.username(), rootMessage(err));
                p.sendMessage(m.component(lang, "service-unavailable", null));
                return;
            }
            ctx.transition(AuthState.AUTHENTICATED);
            ctx.setMode(AuthMode.NONE);
            plugin.security().event("AUTH", "registration completed for " + ctx.username() + " (" + ctx.kind() + ")");
            complete(ctx, p, "register-success");
        });
    }

    private static String policyKey(PasswordPolicy.Result r) {
        return switch (r) {
            case EMPTY -> "register-empty";
            case TOO_SHORT -> "register-password-too-short";
            case TOO_LONG -> "register-password-too-long";
            case NEEDS_UPPERCASE -> "register-password-needs-uppercase";
            case NEEDS_LOWERCASE -> "register-password-needs-lowercase";
            case NEEDS_NUMBER -> "register-password-needs-number";
            case NEEDS_SYMBOL -> "register-password-needs-symbol";
            case BLACKLISTED -> "register-password-blacklisted";
            case SAME_AS_USERNAME -> "register-password-same-as-username";
            case INVALID_CHARACTERS -> "register-password-invalid-characters";
            case OK -> "busy";
        };
    }

    private void fail(PlayerContext ctx, Player p, String key, Placeholders ph) {
        p.sendMessage(plugin.messages().component(ctx.language(), key, ph));
        plugin.sounds().play(ctx, "error");
    }

    // ================================================================== login

    public void login(PlayerContext ctx, Player p, String password) {
        var m = plugin.messages();
        String lang = ctx.language();
        Settings s = plugin.settings();
        if (!ctx.usesPassword() || ctx.isAuthenticated()) {
            p.sendMessage(m.component(lang, "login-not-needed", null));
            return;
        }
        if (ctx.mode() == AuthMode.REGISTER) {
            p.sendMessage(m.component(lang, "login-not-registered", null));
            return;
        }
        if (ctx.state() != AuthState.AUTHENTICATION_REQUIRED || ctx.mode() != AuthMode.LOGIN) {
            p.sendMessage(m.component(lang, "busy", null));
            return;
        }
        long remaining = plugin.security().lockRemaining(ctx);
        if (remaining > 0) {
            p.sendMessage(m.component(lang, "login-locked", Placeholders.of().text("minutes", String.valueOf((remaining + 59_999) / 60_000))));
            return;
        }
        if (!plugin.rateLimits().allowLogin(ctx.uuid())) {
            p.sendMessage(m.component(lang, "rate-limited", null));
            return;
        }
        Account a = ctx.account();
        if (a == null || a.passwordHash() == null) {
            p.sendMessage(m.component(lang, "service-unavailable", null));
            return;
        }
        if (!ctx.transition(AuthState.AUTHENTICATING)) {
            p.sendMessage(m.component(lang, "busy", null));
            return;
        }
        String storedHash = a.passwordHash();
        plugin.passwords().verify(password, storedHash).thenCompose(ok -> {
            if (!ok) {
                return CompletableFuture.completedFuture(false);
            }
            CompletableFuture<String> rehash = plugin.passwords().needsRehash(storedHash)
                    ? plugin.passwords().hash(password) : CompletableFuture.completedFuture(null);
            return rehash.thenCompose(newHash -> {
                long now = System.currentTimeMillis();
                if (newHash != null) {
                    a.setPasswordHash(newHash);                       // upgrade to the currently configured Argon2id parameters
                }
                plugin.sessions().create(a, ctx.ipHash(), now);
                a.setAuthenticated(true);
                return plugin.accounts().save(a).thenApply(v -> true);
            });
        }).whenComplete((ok, err) -> {
            if (err != null) {
                ctx.transition(AuthState.AUTHENTICATION_REQUIRED);
                plugin.logger().error("Login failed for {}: {}", ctx.username(), rootMessage(err));
                p.sendMessage(m.component(lang, "service-unavailable", null));
                return;
            }
            if (ok) {
                ctx.transition(AuthState.AUTHENTICATED);
                plugin.security().recordSuccess(ctx);
                plugin.security().event("AUTH", "successful login for " + ctx.username() + " (" + ctx.kind() + ")");
                complete(ctx, p, "login-success");
                return;
            }
            ctx.transition(AuthState.AUTHENTICATION_REQUIRED);
            int left = plugin.security().recordFailure(ctx, s.maxLoginAttempts);
            plugin.security().event("AUTH", "failed login for " + ctx.username());
            plugin.sounds().play(ctx, "error");
            if (left == 0) {
                if (s.kickOnLockout) {
                    p.disconnect(m.component(lang, "login-lockout-kick", Placeholders.of().text("minutes", String.valueOf(s.lockoutMillis / 60_000))));
                } else {
                    p.sendMessage(m.component(lang, "login-locked", Placeholders.of().text("minutes", String.valueOf(s.lockoutMillis / 60_000))));
                }
            } else {
                p.sendMessage(m.component(lang, "login-wrong", Placeholders.of().text("remaining", String.valueOf(left))));
            }
        });
    }

    /** Common tail of every successful authentication: notice, sound, release restrictions, go to spawn. */
    private void complete(PlayerContext ctx, Player p, String messageKey) {
        plugin.reminders().stop(ctx);
        p.sendMessage(plugin.messages().component(ctx.language(), messageKey, null));
        plugin.sounds().play(ctx, "success");
        plugin.bridge().sync(ctx, false);
        plugin.teleports().route(ctx);
    }

    // ================================================================== unregister

    /** Player unregisters their own account (must already be authenticated; password confirmation by default). */
    public void unregisterSelf(PlayerContext ctx, Player p, String passwordOrNull) {
        var m = plugin.messages();
        String lang = ctx.language();
        Settings s = plugin.settings();
        if (!ctx.isAuthenticated()) {
            // never allow an unauthenticated player to remove account protection
            p.sendMessage(m.component(lang, "unregister-need-auth", null));
            plugin.security().event("SECURITY", "blocked /un_register from unauthenticated " + ctx.username());
            return;
        }
        Account a = ctx.account();
        if (!ctx.usesPassword() || a == null || !a.isRegistered()) {
            p.sendMessage(m.component(lang, "unregister-not-applicable", null));
            return;
        }
        if (s.unregisterRequiresPassword && (passwordOrNull == null || passwordOrNull.isEmpty())) {
            p.sendMessage(m.component(lang, "unregister-usage-password", null));
            return;
        }
        if (!plugin.rateLimits().allowLogin(ctx.uuid())) {
            p.sendMessage(m.component(lang, "rate-limited", null));
            return;
        }
        long remaining = plugin.security().lockRemaining(ctx);
        if (remaining > 0) {
            p.sendMessage(m.component(lang, "login-locked", Placeholders.of().text("minutes", String.valueOf((remaining + 59_999) / 60_000))));
            return;
        }
        CompletableFuture<Boolean> check = s.unregisterRequiresPassword
                ? plugin.passwords().verify(passwordOrNull, a.passwordHash()) : CompletableFuture.completedFuture(true);
        check.thenCompose(ok -> {
            if (!ok) {
                return CompletableFuture.completedFuture(false);
            }
            removeRegistration(a);
            return plugin.accounts().save(a).thenApply(v -> true);
        }).whenComplete((ok, err) -> {
            if (err != null) {
                plugin.logger().error("Unregister failed for {}: {}", ctx.username(), rootMessage(err));
                p.sendMessage(m.component(lang, "service-unavailable", null));
            } else if (!ok) {
                int left = plugin.security().recordFailure(ctx, s.maxLoginAttempts);
                plugin.security().event("AUTH", "wrong password while unregistering " + ctx.username());
                p.sendMessage(m.component(lang, left == 0 ? "login-locked" : "unregister-wrong-password",
                        Placeholders.of().text("minutes", String.valueOf(s.lockoutMillis / 60_000))));
            } else {
                plugin.security().event("AUTH", "account unregistered by owner: " + ctx.username());
                p.sendMessage(m.component(lang, "unregister-success", null));
                requireRegistrationNow(ctx, p, s.unregisterRequireRegisterImmediately);
            }
        });
    }

    /** Removes the password and every session. The account row stays (language, settings). */
    private void removeRegistration(Account a) {
        a.setPasswordHash(null);
        a.setRegisteredAt(null);
        a.setAuthenticated(false);
        plugin.sessions().invalidate(a);
    }

    private void requireRegistrationNow(PlayerContext ctx, Player p, boolean immediately) {
        if (!immediately) {
            return;                                                    // takes effect on the next connection
        }
        if (ctx.transition(AuthState.AUTHENTICATION_REQUIRED)) {
            ctx.setMode(AuthMode.REGISTER);
            ctx.setReason(AuthFlow.Reason.UNREGISTERED);
            ctx.setAnnounced(true);
            plugin.bridge().sync(ctx, false);
            plugin.teleports().route(ctx);
            plugin.reminders().begin(ctx, p);
        }
    }

    /** Administrator removes someone's registration (online or offline). Reports to {@code source}. */
    public void adminUnregister(String name, CommandSource source) {
        var m = plugin.messages();
        PlayerContext online = plugin.registry().byName(name);
        CompletableFuture<Account> target = online != null && online.account() != null
                ? CompletableFuture.completedFuture(online.account())
                : plugin.accounts().findByNameAny(name).thenApply(o -> o.orElse(null));
        target.thenCompose(a -> {
            if (a == null) {
                return CompletableFuture.completedFuture(1);
            }
            if (a.type() == AccountType.PREMIUM || !a.isRegistered()) {
                return CompletableFuture.completedFuture(2);
            }
            removeRegistration(a);
            return plugin.accounts().save(a).thenApply(v -> 0);
        }).whenComplete((code, err) -> {
            if (err != null) {
                plugin.logger().error("Admin unregister failed: {}", rootMessage(err));
                m.send(source, "service-unavailable", null);
                return;
            }
            Placeholders ph = Placeholders.of().text("player", name);
            switch (code) {
                case 1 -> m.send(source, "admin-not-found", ph);
                case 2 -> m.send(source, "admin-nothing-to-unregister", ph);
                default -> {
                    plugin.security().event("ADMIN", sourceName(source) + " unregistered " + name);
                    m.send(source, "admin-unregister-done", ph);
                    if (online != null) {
                        plugin.server().getPlayer(online.uuid()).ifPresent(p -> {
                            p.sendMessage(m.component(online.language(), "unregister-by-admin", null));
                            boolean was = online.isAuthenticated();
                            if (was || online.state() == AuthState.AUTHENTICATION_REQUIRED) {
                                if (was) {
                                    online.transition(AuthState.AUTHENTICATION_REQUIRED);
                                }
                                online.setMode(AuthMode.REGISTER);
                                online.setReason(AuthFlow.Reason.UNREGISTERED);
                                online.setAnnounced(true);
                                plugin.bridge().sync(online, false);
                                plugin.teleports().route(online);
                                plugin.reminders().begin(online, p);
                            }
                        });
                    }
                }
            }
        });
    }

    // ================================================================== admin tools

    public void forceLogin(PlayerContext ctx, CommandSource source) {
        var m = plugin.messages();
        Player p = plugin.server().getPlayer(ctx.uuid()).orElse(null);
        if (p == null || ctx.isAuthenticated() || !ctx.transition(AuthState.AUTHENTICATED)) {
            m.send(source, "admin-force-not-possible", Placeholders.of().text("player", ctx.username()));
            return;
        }
        ctx.setMode(AuthMode.NONE);
        plugin.security().event("ADMIN", sourceName(source) + " forced login of " + ctx.username());
        m.send(source, "admin-force-login-done", Placeholders.of().text("player", ctx.username()));
        complete(ctx, p, "login-success");
    }

    public void forceLogout(PlayerContext ctx, CommandSource source) {
        var m = plugin.messages();
        Player p = plugin.server().getPlayer(ctx.uuid()).orElse(null);
        Account a = ctx.account();
        if (p == null || !ctx.usesPassword() || a == null) {
            m.send(source, "admin-force-not-possible", Placeholders.of().text("player", ctx.username()));
            return;
        }
        plugin.sessions().invalidate(a);
        a.setAuthenticated(false);
        plugin.accounts().save(a);
        if (ctx.isAuthenticated()) {
            ctx.transition(AuthState.AUTHENTICATION_REQUIRED);
        }
        ctx.setMode(a.isRegistered() ? AuthMode.LOGIN : AuthMode.REGISTER);
        ctx.setReason(AuthFlow.Reason.NO_SESSION);
        ctx.setAnnounced(true);
        plugin.security().event("ADMIN", sourceName(source) + " forced logout of " + ctx.username());
        m.send(source, "admin-force-logout-done", Placeholders.of().text("player", ctx.username()));
        plugin.bridge().sync(ctx, false);
        plugin.teleports().route(ctx);
        plugin.reminders().begin(ctx, p);
    }

    /** Safe account information for admins: never the password, hash or tokens. */
    public void info(String name, CommandSource source, boolean sessionOnly) {
        var m = plugin.messages();
        PlayerContext online = plugin.registry().byName(name);
        CompletableFuture<Account> target = online != null && online.account() != null
                ? CompletableFuture.completedFuture(online.account())
                : plugin.accounts().findByNameAny(name).thenApply(o -> o.orElse(null));
        target.whenComplete((a, err) -> {
            if (err != null) {
                m.send(source, "service-unavailable", null);
                return;
            }
            if (a == null) {
                m.send(source, "admin-not-found", Placeholders.of().text("player", name));
                return;
            }
            long now = System.currentTimeMillis();
            String session;
            if (a.sessionTokenHash() == null || a.sessionExpiresAt() == null) {
                session = m.plain(m.langOf(source), "value-none");
            } else if (a.sessionExpiresAt() < now) {
                session = m.plain(m.langOf(source), "value-expired") + " (" + DATE.format(Instant.ofEpochMilli(a.sessionExpiresAt())) + ")";
            } else {
                session = m.plain(m.langOf(source), "value-active") + " " + DATE.format(Instant.ofEpochMilli(a.sessionExpiresAt()));
            }
            String none = m.plain(m.langOf(source), "value-none");
            Placeholders ph = Placeholders.of()
                    .text("player", a.username())
                    .text("acctype", a.type().name())
                    .text("uuid", a.uuid().toString())
                    .text("language", a.language() == null ? none : a.language())
                    .text("registered", a.registeredAt() == null ? (a.type() == AccountType.CRACKED || a.type() == AccountType.BEDROCK ? no(a) : none) : DATE.format(Instant.ofEpochMilli(a.registeredAt())))
                    .text("lastlogin", a.lastLogin() == null ? none : DATE.format(Instant.ofEpochMilli(a.lastLogin())))
                    .text("session", session)
                    .text("ip", a.lastIpHash() == null ? none : a.lastIpHash().substring(0, 8) + "\u2026")
                    .text("online", online == null ? "-" : online.state().name());
            m.send(source, sessionOnly ? "admin-session" : "admin-info", ph);
        });
    }

    private static String no(Account a) {
        return a.isRegistered() ? "yes" : "no";
    }

    // ================================================================== lifecycle

    /** Disconnect: the session countdown starts now; every task of the player is stopped. */
    public void onDisconnect(PlayerContext ctx) {
        ctx.markDisconnected();
        plugin.reminders().stop(ctx);
        plugin.bridge().cancelHelloTimer(ctx);
        Account a = ctx.account();
        if (a != null && (ctx.usesPassword() || a.authenticated())) {
            if (ctx.isAuthenticated() && ctx.usesPassword()) {
                plugin.sessions().refreshOnDisconnect(a, System.currentTimeMillis());
            }
            a.setAuthenticated(false);
            plugin.accounts().save(a).exceptionally(t -> {
                plugin.logger().warn("Could not save {} on disconnect: {}", ctx.username(), rootMessage(t));
                return null;
            });
        }
        plugin.registry().remove(ctx);
    }

    static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getClass().getSimpleName();                           // class only: messages could contain SQL parameters
    }

    private static String sourceName(CommandSource s) {
        return s instanceof Player p ? p.getUsername() : "console";
    }
}
