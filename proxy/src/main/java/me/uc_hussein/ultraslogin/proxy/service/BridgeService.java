package me.uc_hussein.ultraslogin.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.scheduler.ScheduledTask;
import me.uc_hussein.ultraslogin.common.bridge.BridgeCodec;
import me.uc_hussein.ultraslogin.common.bridge.BridgeException;
import me.uc_hussein.ultraslogin.common.bridge.BridgeMessage;
import me.uc_hussein.ultraslogin.common.security.SecretStore;
import me.uc_hussein.ultraslogin.proxy.PlayerContext;
import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;
import me.uc_hussein.ultraslogin.proxy.config.Settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Plugin-messaging link to the backend bridge. Every message is HMAC-signed with a shared secret and bound to
 * the player of the connection it travels on. Messages that arrive from a player (instead of a server) are
 * dropped by the listener, so a malicious client cannot talk to the bridge channel.
 */
public final class BridgeService {
    public static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.create(BridgeCodec.CHANNEL_NAMESPACE, BridgeCodec.CHANNEL_NAME);

    private final UltrasLoginPlugin plugin;
    private volatile BridgeCodec codec;
    private volatile String secret;
    private volatile long lastSkew = -1;
    private volatile long lastForgeLog;

    public BridgeService(UltrasLoginPlugin plugin) {
        this.plugin = plugin;
    }

    /** Registers the channel and loads (or generates) the shared secret. */
    public void init() throws IOException {
        plugin.server().getChannelRegistrar().register(CHANNEL);
        String configured = plugin.config().raw().string("bridge.secret").trim();
        if (configured.length() >= 16 && !configured.equals("CHANGE_ME")) {
            secret = configured;
        } else {
            Path file = plugin.dataDir().resolve("bridge-secret.key");
            boolean existed = Files.exists(file);
            SecretStore.loadOrCreate(file, 32);
            secret = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (!existed) {
                plugin.logger().warn("A bridge secret was generated in {} - copy its content into 'secret:' in the bridge config.yml "
                        + "of every backend server (keep it private).", file.getFileName());
            }
        }
        rebuild();
    }

    /** Rebuilds the codec when the allowed clock skew changed (secret is kept). */
    public void rebuild() {
        long skew = plugin.settings().bridgeMaxSkewMillis;
        if (secret != null && skew != lastSkew) {
            codec = new BridgeCodec(secret.getBytes(StandardCharsets.UTF_8), System::currentTimeMillis, skew);
            lastSkew = skew;
        }
    }

    public void send(Player p, BridgeMessage m) {
        BridgeCodec c = codec;
        if (c == null) {
            return;
        }
        byte[] data = c.encode(m);
        p.getCurrentServer().ifPresent(sc -> sc.sendPluginMessage(CHANNEL, data));
    }

    /** Called for every message that arrived from a backend server connection. */
    public void handle(ServerConnection from, byte[] data) {
        BridgeMessage m;
        try {
            m = codec.decode(data);
        } catch (BridgeException e) {
            rateLimitedLog("rejected a bridge message from server " + from.getServerInfo().getName() + " (" + e.getMessage()
                    + ") - check that the bridge secret matches on proxy and backend");
            return;
        }
        Player p = from.getPlayer();
        if (!m.player().equals(p.getUniqueId())) {
            rateLimitedLog("bridge message for another player was ignored");
            return;
        }
        PlayerContext ctx = plugin.registry().get(p.getUniqueId());
        if (ctx == null) {
            return;
        }
        if (m instanceof BridgeMessage.Hello h) {
            onHello(ctx, p, h);
        } else if (m instanceof BridgeMessage.GuiClick c) {
            plugin.gui().handleClick(ctx, c.guiId(), c.actionId());
        }
    }

    /** Called from a player-sent plugin message (forgery attempt). */
    public void rateLimitedLog(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastForgeLog > 10_000) {
            lastForgeLog = now;
            plugin.security().event("BRIDGE", msg);
        }
    }

    private void onHello(PlayerContext ctx, Player p, BridgeMessage.Hello h) {
        cancelHelloTimer(ctx);
        ctx.setBridgeReady(true);
        if (h.protocol() != BridgeCodec.PROTOCOL) {
            plugin.logger().error("Bridge protocol mismatch on {} (bridge {}, proxy {}): update both jars",
                    p.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("?"), h.protocol(), BridgeCodec.PROTOCOL);
            if (!ctx.isAuthenticated()) {
                p.disconnect(plugin.messages().kick(ctx.language(), "bridge-missing", null));
                return;
            }
        }
        sync(ctx, true);
    }

    /** Pushes restriction state, a pending teleport and (optionally) the open menu to the backend. */
    public void sync(PlayerContext ctx, boolean includeGui) {
        Player p = plugin.server().getPlayer(ctx.uuid()).orElse(null);
        if (p == null || !ctx.bridgeReady()) {
            return;
        }
        Settings s = plugin.settings();
        boolean restricted = !ctx.isAuthenticated();
        send(p, new BridgeMessage.State(ctx.uuid(), restricted, restricted ? s.restrictionFlags : 0));
        Settings.Location loc = plugin.teleports().desired(ctx);
        String current = p.getCurrentServer().map(sc -> sc.getServerInfo().getName()).orElse("");
        if (ctx.pendingTeleport() && loc.server().equalsIgnoreCase(current)) {
            if (loc.backendTeleport()) {
                send(p, new BridgeMessage.Teleport(ctx.uuid(), loc.world(), loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch()));
            }
            ctx.setPendingTeleport(false);
        }
        if (includeGui && ctx.gui() != null) {
            plugin.gui().refresh(ctx);
        }
    }

    /** Starts the "bridge must answer" timer for a freshly connected server. */
    public void expectHello(PlayerContext ctx) {
        cancelHelloTimer(ctx);
        ctx.setBridgeReady(false);
        int seconds = plugin.settings().bridgeHelloTimeoutSeconds;
        ScheduledTask t = plugin.server().getScheduler().buildTask(plugin, () -> {
            if (ctx.bridgeReady() || ctx.disconnected()) {
                return;
            }
            Player p = plugin.server().getPlayer(ctx.uuid()).orElse(null);
            if (p == null) {
                return;
            }
            String server = p.getCurrentServer().map(sc -> sc.getServerInfo().getName()).orElse("?");
            if (!ctx.isAuthenticated() || plugin.settings().bridgeRequireOnAllServers) {
                plugin.security().event("BRIDGE", "no bridge answered on server '" + server + "' - " + ctx.username()
                        + " was disconnected because restrictions cannot be enforced");
                p.disconnect(plugin.messages().kick(ctx.language(), "bridge-missing", null));
            } else {
                plugin.logger().warn("Server '{}' has no Ultras Login bridge (or the secret is wrong); players there are not restricted.", server);
            }
        }).delay(seconds, TimeUnit.SECONDS).schedule();
        ctx.setHelloTask(t);
    }

    public void cancelHelloTimer(PlayerContext ctx) {
        ScheduledTask t = ctx.helloTask();
        if (t != null) {
            t.cancel();
            ctx.setHelloTask(null);
        }
    }
}
