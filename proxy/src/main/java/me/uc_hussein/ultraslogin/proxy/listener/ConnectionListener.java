package me.uc_hussein.ultraslogin.proxy.listener;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.uc_hussein.ultraslogin.common.flow.MojangLookup;
import me.uc_hussein.ultraslogin.common.flow.PremiumPolicy;
import me.uc_hussein.ultraslogin.common.model.Account;
import me.uc_hussein.ultraslogin.common.model.AccountType;
import me.uc_hussein.ultraslogin.proxy.PlayerContext;
import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;
import me.uc_hussein.ultraslogin.proxy.config.Settings;
import me.uc_hussein.ultraslogin.proxy.service.LoginDeniedException;
import me.uc_hussein.ultraslogin.proxy.service.Placeholders;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * Connection pipeline:
 * PreLogin decides online (Mojang-verified) vs offline mode per name; Login builds the player's state;
 * ChooseInitialServer / ServerPreConnect keep unauthenticated players on the authentication server.
 * If the plugin failed to start, everything that is not Mojang-verified is refused (fail closed).
 */
public final class ConnectionListener {
    private final UltrasLoginPlugin plugin;

    public ConnectionListener(UltrasLoginPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ PreLogin

    @Subscribe(order = PostOrder.LATE)
    public EventTask onPreLogin(PreLoginEvent e) {
        return EventTask.async(() -> preLogin(e));
    }

    private void preLogin(PreLoginEvent e) {
        if (!plugin.ready() || !e.getResult().isAllowed()) {
            return;                                           // proxy default applies (keep velocity.toml online-mode = true)
        }
        String name = e.getUsername();
        UUID id = e.getUniqueId();
        if (plugin.bedrock().isBedrock(id)) {
            return;                                           // Floodgate owns Bedrock connections
        }
        String prefix = plugin.bedrock().prefix();
        if (!prefix.isEmpty() && name.startsWith(prefix)) {
            return;                                           // left to Floodgate; LoginEvent classifies by Floodgate identity only
        }
        String lang = plugin.settings().defaultLanguage;
        try {
            PremiumPolicy policy = new PremiumPolicy(plugin.settings().premium);
            Account local = plugin.auth().lookupLocal(name).join();
            MojangLookup lookup = policy.needsLookup(name, local) ? plugin.premium().lookup(name).join() : null;
            PremiumPolicy.Mode mode = policy.decide(name, local, lookup);
            switch (mode) {
                case FORCE_ONLINE -> e.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                case FORCE_OFFLINE -> e.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                case DENY_INVALID_NAME -> deny(e, lang, "invalid-username");
                case DENY_CRACKED_DISABLED -> deny(e, lang, "cracked-disabled");
                case DENY_VERIFICATION_FAILED -> {
                    plugin.security().event("PREMIUM", "verification unavailable for " + name + " - connection refused (fail safe)");
                    deny(e, lang, "premium-verification-failed");
                }
            }
            plugin.security().event("PREMIUM", "pre-login " + name + " -> " + mode);
        } catch (RuntimeException ex) {
            plugin.logger().error("Pre-login check failed for {}: {}", name, ex.getClass().getSimpleName());
            deny(e, lang, "service-unavailable");
        }
    }

    private void deny(PreLoginEvent e, String lang, String key) {
        e.setResult(PreLoginEvent.PreLoginComponentResult.denied(plugin.messages().kick(lang, key, null)));
    }

    // ------------------------------------------------------------------ Login

    @Subscribe(order = PostOrder.LAST)
    public EventTask onLogin(LoginEvent e) {
        return EventTask.async(() -> login(e));
    }

    private void login(LoginEvent e) {
        if (!e.getResult().isAllowed()) {
            return;
        }
        Player p = e.getPlayer();
        String lang = plugin.settings().defaultLanguage;
        if (!plugin.ready()) {
            if (!p.isOnlineMode() || plugin.bedrock().isBedrock(p.getUniqueId())) {
                e.setResult(ResultedEvent.ComponentResult.denied(Component.text("Authentication is unavailable. Please try again later.")));
            }
            return;
        }
        Settings s = plugin.settings();
        AccountType kind = plugin.bedrock().isBedrock(p.getUniqueId()) ? AccountType.BEDROCK
                : p.isOnlineMode() ? AccountType.PREMIUM : AccountType.CRACKED;
        // The kind comes from how the connection was verified, never from the name.
        if (kind == AccountType.BEDROCK && !s.bedrockEnabled) {
            denyLogin(e, lang, "bedrock-disabled", null);
            return;
        }
        if (kind != AccountType.BEDROCK && !MojangLookup.isValidJavaName(p.getUsername())) {
            denyLogin(e, lang, "invalid-username", null);
            return;
        }
        if (kind == AccountType.CRACKED && !s.crackedEnabled) {
            denyLogin(e, lang, "cracked-disabled", null);
            return;
        }
        PlayerContext existing = plugin.registry().get(p.getUniqueId());
        if (existing != null && plugin.server().getPlayer(existing.uuid()).isEmpty()) {
            plugin.registry().remove(existing);               // stale context of a connection that never completed
            existing = null;
        }
        // DENY_NEW: a second connection can never take over (or kick) an existing session.
        Optional<Player> sameName = plugin.server().getPlayer(p.getUsername());
        if (existing != null || (sameName.isPresent() && !sameName.get().getUniqueId().equals(p.getUniqueId()))) {
            plugin.security().event("SECURITY", "second connection for " + p.getUsername() + " denied (account already online)");
            denyLogin(e, lang, "already-online", null);
            return;
        }
        try {
            PlayerContext ctx = plugin.auth().onLogin(p, kind).join();
            if (!plugin.registry().register(ctx)) {
                denyLogin(e, lang, "already-online", null);
            }
        } catch (CompletionException | LoginDeniedException ex) {
            Throwable cause = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof LoginDeniedException lde) {
                denyLogin(e, lang, lde.messageKey(), lde.placeholders());
            } else {
                plugin.logger().error("Could not prepare login of {}: {}", p.getUsername(), cause.getClass().getSimpleName());
                denyLogin(e, lang, "service-unavailable", null);
            }
        }
    }

    private void denyLogin(LoginEvent e, String lang, String key, Placeholders ph) {
        e.setResult(ResultedEvent.ComponentResult.denied(plugin.messages().kick(lang, key, ph)));
    }

    // ------------------------------------------------------------------ servers

    @Subscribe
    public void onChooseInitialServer(PlayerChooseInitialServerEvent e) {
        PlayerContext ctx = plugin.registry().get(e.getPlayer().getUniqueId());
        if (ctx == null) {
            return;
        }
        Optional<RegisteredServer> target = plugin.teleports().initialServer(ctx);
        if (target.isPresent()) {
            e.setInitialServer(target.get());
        } else {
            plugin.logger().error("Server '{}' from config.yml does not exist in velocity.toml", plugin.teleports().desired(ctx).server());
            e.getPlayer().disconnect(plugin.messages().kick(ctx.language(), "service-unavailable", null));
        }
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent e) {
        PlayerContext ctx = plugin.registry().get(e.getPlayer().getUniqueId());
        Settings s = plugin.settings();
        if (ctx == null || ctx.mayLeaveAuthServer() || !s.restrictServerSwitch) {
            return;
        }
        RegisteredServer target = e.getResult().getServer().orElse(e.getOriginalServer());
        if (!target.getServerInfo().getName().equalsIgnoreCase(s.authentication.server())) {
            e.setResult(ServerPreConnectEvent.ServerResult.denied());
            long now = System.currentTimeMillis();
            if (now - ctx.lastBlockedNotice() > 3000) {
                ctx.setLastBlockedNotice(now);
                e.getPlayer().sendMessage(plugin.messages().component(ctx.language(), "server-switch-blocked", null));
            }
        }
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent e) {
        PlayerContext ctx = plugin.registry().get(e.getPlayer().getUniqueId());
        if (ctx == null) {
            return;
        }
        plugin.bridge().expectHello(ctx);                      // the bridge on this server must answer, otherwise restrictions cannot be enforced
        if (e.getPreviousServer() == null) {
            plugin.auth().onFirstServerJoin(ctx, e.getPlayer());
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent e) {
        PlayerContext ctx = plugin.registry().get(e.getPlayer().getUniqueId());
        if (ctx != null) {
            plugin.auth().onDisconnect(ctx);
        }
    }
}
