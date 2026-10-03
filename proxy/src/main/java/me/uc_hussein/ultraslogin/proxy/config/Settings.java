package me.uc_hussein.ultraslogin.proxy.config;

import me.uc_hussein.ultraslogin.common.bridge.RestrictionFlags;
import me.uc_hussein.ultraslogin.common.config.Cfg;
import me.uc_hussein.ultraslogin.common.flow.ConcurrentLoginPolicy;
import me.uc_hussein.ultraslogin.common.flow.PremiumPolicy;
import me.uc_hussein.ultraslogin.common.model.LanguageCode;
import me.uc_hussein.ultraslogin.common.session.SessionService;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Immutable typed snapshot of config.yml, rebuilt on /ultraslogin reload. */
public final class Settings {
    public record Location(String mode, String server, String world, double x, double y, double z, float yaw, float pitch) {
        /** True if the bridge has to teleport the player inside the backend (WORLD / LOCATION mode). */
        public boolean backendTeleport() {
            return !mode.equals("SERVER") && !world.isBlank();
        }
    }

    public record Reminder(boolean enabled, int intervalSeconds, boolean chat, boolean actionbar, boolean title, boolean bossbar) {
    }

    public record Ui(boolean titleEnabled, boolean actionbarEnabled, boolean bossbarEnabled, String bossbarColor, String bossbarOverlay) {
    }

    public record SoundDef(String key, float volume, float pitch) {
    }

    public record Limit(int limit, long windowMillis) {
    }

    public final Location authentication;
    public final Location spawn;
    public final int restrictionFlags;
    public final boolean restrictBeforeLogin;
    public final boolean restrictCommands;
    public final boolean restrictServerSwitch;
    public final Set<String> allowedCommands;   // includes aliases of allowed base commands
    public final Reminder reminder;
    public final Ui ui;
    public final boolean soundsEnabled;
    public final Map<String, SoundDef> sounds;
    public final boolean educationEnabled;
    public final String defaultLanguage;
    public final String firstJoinGui;                 // FIRST_JOIN or LANGUAGE
    public final boolean bedrockEnabled;
    public final boolean bedrockRequireAuth;
    public final boolean crackedEnabled;
    public final PremiumPolicy.Config premium;
    public final long premiumCacheMillis;
    public final long premiumNegativeCacheMillis;
    public final long premiumErrorCacheMillis;
    public final String premiumLookupUrl;
    public final int premiumTimeoutSeconds;
    public final int maxLoginAttempts;
    public final long lockoutMillis;
    public final boolean kickOnLockout;
    public final boolean unregisterRequiresPassword;
    public final boolean enforceUsernameCase;
    public final boolean unregisterRequireRegisterImmediately;
    public final ConcurrentLoginPolicy concurrentPolicy;
    public final SessionService.Config session;
    public final Limit authLimit;
    public final Limit registerLimit;
    public final Limit commandLimit;
    public final int mojangPerMinute;
    public final int bridgeHelloTimeoutSeconds;
    public final boolean bridgeRequireOnAllServers;
    public final long bridgeMaxSkewMillis;
    public final Map<String, Set<String>> aliases;
    public final boolean logConsole;

    private Settings(Cfg c) {
        authentication = location(c, "locations.authentication");
        spawn = location(c, "locations.spawn");
        restrictBeforeLogin = c.bool("security.restrict-before-login", true);
        restrictionFlags = RestrictionFlags.fromConfig(c);
        restrictCommands = restrictBeforeLogin && c.bool("restrictions.commands", true);
        restrictServerSwitch = restrictBeforeLogin && c.bool("restrictions.server-switch", true);
        Set<String> allowed = new LinkedHashSet<>();
        for (String s : c.stringList("commands.allowed-before-login")) {
            allowed.add(s.trim().toLowerCase(Locale.ROOT).replaceFirst("^/", ""));
        }
        reminder = new Reminder(c.bool("login-reminder.enabled", true), Math.max(1, c.integer("login-reminder.interval-seconds", 10)),
                c.bool("login-reminder.mode.chat", true), c.bool("login-reminder.mode.actionbar", false),
                c.bool("login-reminder.mode.title", false), c.bool("login-reminder.mode.bossbar", false));
        ui = new Ui(c.bool("ui.title.enabled", false), c.bool("ui.actionbar.enabled", true), c.bool("ui.bossbar.enabled", false),
                c.string("ui.bossbar.color").toUpperCase(Locale.ROOT), c.string("ui.bossbar.overlay").toUpperCase(Locale.ROOT));
        soundsEnabled = c.bool("sounds.enabled", true);
        Map<String, SoundDef> snd = new HashMap<>();
        for (String id : c.keys("sounds")) {
            if (id.equals("enabled")) {
                continue;
            }
            String key = c.string("sounds." + id + ".sound").trim();
            if (key.isEmpty()) {
                continue;
            }
            if (!key.matches("[a-z0-9_.:/-]+")) {
                c.addWarning("sounds." + id + ".sound '" + key + "' is not a valid sound key (use e.g. ui.button.click) - disabled");
                continue;
            }
            float vol = (float) Math.max(0, Math.min(2, c.decimal("sounds." + id + ".volume", 0.3)));
            float pit = (float) Math.max(0.5, Math.min(2, c.decimal("sounds." + id + ".pitch", 1.0)));
            snd.put(id, new SoundDef(key, vol, pit));
        }
        sounds = Map.copyOf(snd);
        educationEnabled = c.bool("education.enabled", true);
        String lang = LanguageCode.normalize(c.string("language.default"));
        if (lang == null) {
            c.addWarning("language.default must be en or ar - using en");
            lang = "en";
        }
        defaultLanguage = lang;
        firstJoinGui = c.string("language.first-join-gui").equalsIgnoreCase("LANGUAGE") ? "LANGUAGE" : "FIRST_JOIN";
        bedrockEnabled = c.bool("bedrock.enabled", true);
        bedrockRequireAuth = c.bool("bedrock.require-authentication", false);
        crackedEnabled = c.bool("cracked.enabled", true);
        premium = new PremiumPolicy.Config(c.bool("premium.enabled", true), crackedEnabled,
                enumOf(PremiumPolicy.FailureMode.class, c.string("premium.verification-failure.mode"), PremiumPolicy.FailureMode.KICK, c),
                enumOf(PremiumPolicy.RegisteredCrackedPolicy.class, c.string("premium.registered-cracked-name-policy"),
                        PremiumPolicy.RegisteredCrackedPolicy.KEEP_CRACKED, c));
        premiumCacheMillis = Math.max(0, c.number("premium.cache-minutes", 30)) * 60_000L;
        premiumNegativeCacheMillis = Math.max(0, c.number("premium.negative-cache-minutes", 5)) * 60_000L;
        premiumErrorCacheMillis = Math.max(0, c.number("premium.error-cache-seconds", 20)) * 1000L;
        premiumLookupUrl = c.string("premium.lookup-url");
        premiumTimeoutSeconds = Math.max(1, c.integer("premium.timeout-seconds", 4));
        maxLoginAttempts = Math.max(1, c.integer("security.max-login-attempts", 5));
        lockoutMillis = Math.max(1, c.number("security.lockout-minutes", 5)) * 60_000L;
        kickOnLockout = c.bool("security.kick-on-lockout", true);
        unregisterRequiresPassword = c.bool("security.unregister-requires-password", true);
        unregisterRequireRegisterImmediately = c.bool("security.unregister-require-register-immediately", true);
        enforceUsernameCase = c.bool("security.enforce-username-case", true);
        concurrentPolicy = ConcurrentLoginPolicy.parse(c.string("sessions.concurrent-login-policy"));
        if (concurrentPolicy != ConcurrentLoginPolicy.DENY_NEW) {
            c.addWarning("sessions.concurrent-login-policy " + concurrentPolicy + " cannot be enforced safely on Velocity - using DENY_NEW");
        }
        session = SessionService.Config.from(c);
        authLimit = limit(c, "rate-limit.auth-commands", 5, 10);
        registerLimit = limit(c, "rate-limit.register", 3, 60);
        commandLimit = limit(c, "rate-limit.commands", 10, 5);
        mojangPerMinute = Math.max(1, c.integer("rate-limit.mojang-requests-per-minute", 60));
        bridgeHelloTimeoutSeconds = Math.max(3, c.integer("bridge.hello-timeout-seconds", 10));
        bridgeRequireOnAllServers = c.bool("bridge.require-on-all-servers", false);
        bridgeMaxSkewMillis = Math.max(5, c.number("bridge.max-clock-skew-seconds", 60)) * 1000L;
        Map<String, Set<String>> al = new HashMap<>();
        for (String cmd : new String[]{"login", "register", "un_register", "language", "register_info", "ultraslogin"}) {
            Set<String> s = new LinkedHashSet<>();
            for (String a : c.stringList("commands.aliases." + cmd)) {
                if (!a.isBlank()) {
                    s.add(a.trim().toLowerCase(Locale.ROOT));
                }
            }
            al.put(cmd, s);
        }
        aliases = Map.copyOf(al);
        for (String base : new String[]{"login", "register", "language"}) {
            if (allowed.contains(base)) {
                allowed.addAll(aliases.getOrDefault(base, Set.of()));
            }
        }
        allowedCommands = Set.copyOf(allowed);
        logConsole = c.bool("logging.console", true);
    }

    public static Settings from(Cfg c) {
        return new Settings(c);
    }

    private static Location location(Cfg c, String p) {
        String mode = c.string(p + ".mode").toUpperCase(Locale.ROOT);
        if (!mode.equals("SERVER") && !mode.equals("WORLD") && !mode.equals("LOCATION")) {
            c.addWarning(p + ".mode must be SERVER, WORLD or LOCATION - using SERVER");
            mode = "SERVER";
        }
        return new Location(mode, c.string(p + ".server"), c.string(p + ".world"), c.decimal(p + ".x", 0.5), c.decimal(p + ".y", 100),
                c.decimal(p + ".z", 0.5), (float) c.decimal(p + ".yaw", 0), (float) c.decimal(p + ".pitch", 0));
    }

    private static Limit limit(Cfg c, String p, int dl, int dw) {
        return new Limit(Math.max(1, c.integer(p + ".limit", dl)), Math.max(1, c.integer(p + ".window-seconds", dw)) * 1000L);
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String raw, E fallback, Cfg c) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            c.addWarning("invalid value '" + raw + "' for " + type.getSimpleName() + " - using " + fallback);
            return fallback;
        }
    }
}
