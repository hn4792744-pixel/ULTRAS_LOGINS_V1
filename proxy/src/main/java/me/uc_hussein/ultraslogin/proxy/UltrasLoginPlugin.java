package me.uc_hussein.ultraslogin.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import me.uc_hussein.ultraslogin.common.config.Cfg;
import me.uc_hussein.ultraslogin.common.db.AccountService;
import me.uc_hussein.ultraslogin.common.db.Database;
import me.uc_hussein.ultraslogin.common.db.DatabaseConfig;
import me.uc_hussein.ultraslogin.common.db.JdbcAccountRepository;
import me.uc_hussein.ultraslogin.common.security.IpHasher;
import me.uc_hussein.ultraslogin.common.security.SecretStore;
import me.uc_hussein.ultraslogin.common.session.SessionService;
import me.uc_hussein.ultraslogin.proxy.command.AdminCommand;
import me.uc_hussein.ultraslogin.proxy.command.LanguageCommand;
import me.uc_hussein.ultraslogin.proxy.command.LoginCommand;
import me.uc_hussein.ultraslogin.proxy.command.RegisterCommand;
import me.uc_hussein.ultraslogin.proxy.command.RegisterInfoCommand;
import me.uc_hussein.ultraslogin.proxy.command.UnregisterCommand;
import me.uc_hussein.ultraslogin.proxy.config.ProxyConfig;
import me.uc_hussein.ultraslogin.proxy.config.Settings;
import me.uc_hussein.ultraslogin.proxy.listener.BridgeListener;
import me.uc_hussein.ultraslogin.proxy.listener.CommandListener;
import me.uc_hussein.ultraslogin.proxy.listener.ConnectionListener;
import me.uc_hussein.ultraslogin.proxy.service.AuthenticationService;
import me.uc_hussein.ultraslogin.proxy.service.BedrockService;
import me.uc_hussein.ultraslogin.proxy.service.BridgeService;
import me.uc_hussein.ultraslogin.proxy.service.GuiService;
import me.uc_hussein.ultraslogin.proxy.service.LanguageService;
import me.uc_hussein.ultraslogin.proxy.service.MessageService;
import me.uc_hussein.ultraslogin.proxy.service.PasswordService;
import me.uc_hussein.ultraslogin.proxy.service.PremiumService;
import me.uc_hussein.ultraslogin.proxy.service.RateLimitService;
import me.uc_hussein.ultraslogin.proxy.service.ReminderService;
import me.uc_hussein.ultraslogin.proxy.service.SecurityLogger;
import me.uc_hussein.ultraslogin.proxy.service.SecurityService;
import me.uc_hussein.ultraslogin.proxy.service.SoundService;
import me.uc_hussein.ultraslogin.proxy.service.TeleportService;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Ultras_login_v1 - Velocity authentication plugin (Premium / Cracked / Bedrock) by UC_Hussein.
 * This class only wires services together; all logic lives in the service classes.
 */
@Plugin(id = "ultras_login", name = "Ultras_login_v1", version = "1.0.0", authors = {"UC_Hussein"},
        description = "Premium, cracked and Bedrock authentication for Velocity",
        dependencies = {@Dependency(id = "floodgate", optional = true)})
public final class UltrasLoginPlugin {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDir;

    private ProxyConfig config;
    private SecurityLogger secLog;
    private BedrockService bedrock;
    private IpHasher ipHasher;
    private ExecutorService dbExecutor;
    private ExecutorService cryptoExecutor;
    private Database database;
    private AccountService accounts;
    private volatile SessionService sessions = new SessionService(new SessionService.Config(true, 60_000L, true, true, true));
    private PasswordService passwords;
    private MessageService messages;
    private LanguageService languages;
    private PlayerRegistry registry;
    private SecurityService security;
    private RateLimitService rateLimits;
    private PremiumService premium;
    private BridgeService bridge;
    private TeleportService teleports;
    private GuiService gui;
    private ReminderService reminders;
    private SoundService sounds;
    private AuthenticationService auth;
    private volatile boolean ready;

    @Inject
    public UltrasLoginPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDir) {
        this.server = server;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        config = new ProxyConfig(dataDir);
        ProxyConfig.Snapshot snap = config.reload();
        secLog = new SecurityLogger(dataDir, logger, () -> config.settings().logConsole);
        bedrock = new BedrockService(server, logger);
        registry = new PlayerRegistry();
        // Listeners are always registered: if startup fails below, they refuse everything that is not Mojang-verified.
        server.getEventManager().register(this, new ConnectionListener(this));
        server.getEventManager().register(this, new CommandListener(this));
        server.getEventManager().register(this, new BridgeListener(this));
        try {
            start(snap);
            ready = true;
            logger.info("Ultras_login_v1 enabled (premium={}, bedrock={}, floodgate={}).", config.settings().premium.premiumEnabled(),
                    config.settings().bedrockEnabled, bedrock.floodgatePresent());
        } catch (Exception | LinkageError e) {
            logger.error("Ultras_login_v1 FAILED to start - only Mojang-verified players are admitted until this is fixed: {}", e.toString());
        }
    }

    private void start(ProxyConfig.Snapshot snap) throws Exception {
        Settings s = snap.settings();
        ipHasher = new IpHasher(SecretStore.loadOrCreate(dataDir.resolve("ip-hash.key"), 32));
        DatabaseConfig dbCfg = DatabaseConfig.from(snap.config());
        dbExecutor = Executors.newFixedThreadPool(dbCfg.type() == DatabaseConfig.Type.SQLITE ? 2 : Math.max(2, dbCfg.poolSize()), named("UltrasLogin-DB"));
        int hashThreads = Math.max(1, snap.config().integer("password.max-concurrent-hashes", 2));
        cryptoExecutor = Executors.newFixedThreadPool(hashThreads, named("UltrasLogin-Crypto"));
        database = Database.open(dbCfg, dataDir);
        accounts = new AccountService(new JdbcAccountRepository(database.dataSource()), dbExecutor);
        accounts.resetAuthenticatedFlags().exceptionally(t -> null);

        passwords = new PasswordService(cryptoExecutor, dataDir.resolve("password-blacklist.txt"));
        messages = new MessageService(this);
        languages = new LanguageService(this);
        security = new SecurityService(secLog);
        rateLimits = new RateLimitService();
        premium = new PremiumService(this);
        bridge = new BridgeService(this);
        teleports = new TeleportService(this);
        gui = new GuiService(this);
        reminders = new ReminderService(this);
        sounds = new SoundService(this);
        auth = new AuthenticationService(this);
        applyConfig(snap);
        bridge.init();
        registerCommands(s);
    }

    /** Applies a freshly loaded configuration to the services (database pool and sessions of players stay untouched). */
    private void applyConfig(ProxyConfig.Snapshot snap) {
        List<String> warnings = snap.warnings();
        messages.reload(snap.config(), warnings);
        passwords.reload(snap.config(), warnings);
        sessions = new SessionService(snap.settings().session);
        rateLimits.reconfigure(snap.settings());
        security.reconfigure(snap.settings());
        if (bridge != null) {
            bridge.rebuild();
        }
        for (String w : warnings) {
            logger.warn("[config] {}", w);
        }
    }

    /** /ultraslogin reload. Returns the number of configuration warnings. */
    public int reload() {
        ProxyConfig.Snapshot snap = config.reload();
        applyConfig(snap);
        secLog.event("RELOAD", "configuration reloaded (" + snap.warnings().size() + " warnings)");
        return snap.warnings().size();
    }

    private void registerCommands(Settings s) {
        CommandManager cm = server.getCommandManager();
        reg(cm, "login", s, new LoginCommand(this));
        reg(cm, "register", s, new RegisterCommand(this));
        reg(cm, "un_register", s, new UnregisterCommand(this));
        reg(cm, "language", s, new LanguageCommand(this));
        reg(cm, "register_info", s, new RegisterInfoCommand(this));
        reg(cm, "ultraslogin", s, new AdminCommand(this));
    }

    private void reg(CommandManager cm, String name, Settings s, SimpleCommand cmd) {
        Set<String> aliases = s.aliases.getOrDefault(name, Set.of());
        CommandMeta meta = cm.metaBuilder(name).aliases(aliases.toArray(new String[0])).plugin(this).build();
        cm.register(meta, cmd);
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        ready = false;
        try {
            if (registry != null && auth != null) {
                for (PlayerContext ctx : List.copyOf(registry.all())) {
                    auth.onDisconnect(ctx);                    // starts session countdown + saves
                }
            }
            if (dbExecutor != null) {
                dbExecutor.shutdown();
                dbExecutor.awaitTermination(5, TimeUnit.SECONDS);
            }
            if (cryptoExecutor != null) {
                cryptoExecutor.shutdownNow();
            }
            if (database != null) {
                database.close();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (secLog != null) {
                secLog.close();
            }
        }
    }

    private static java.util.concurrent.ThreadFactory named(String prefix) {
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    /** True if the player/console may use an admin feature (specific node or the ultraslogin.admin parent). */
    public boolean has(CommandSource source, String permission) {
        return source.hasPermission(permission) || source.hasPermission("ultraslogin.admin");
    }

    public boolean ready() { return ready; }
    public ProxyServer server() { return server; }
    public Logger logger() { return logger; }
    public Path dataDir() { return dataDir; }
    public ProxyConfig config() { return config; }
    public Settings settings() { return config.settings(); }
    public Cfg rawConfig() { return config.raw(); }
    public SecurityLogger secLog() { return secLog; }
    public BedrockService bedrock() { return bedrock; }
    public IpHasher ipHasher() { return ipHasher; }
    public AccountService accounts() { return accounts; }
    public SessionService sessions() { return sessions; }
    public PasswordService passwords() { return passwords; }
    public MessageService messages() { return messages; }
    public LanguageService languages() { return languages; }
    public PlayerRegistry registry() { return registry; }
    public SecurityService security() { return security; }
    public RateLimitService rateLimits() { return rateLimits; }
    public PremiumService premium() { return premium; }
    public BridgeService bridge() { return bridge; }
    public TeleportService teleports() { return teleports; }
    public GuiService gui() { return gui; }
    public ReminderService reminders() { return reminders; }
    public SoundService sounds() { return sounds; }
    public AuthenticationService auth() { return auth; }
}
