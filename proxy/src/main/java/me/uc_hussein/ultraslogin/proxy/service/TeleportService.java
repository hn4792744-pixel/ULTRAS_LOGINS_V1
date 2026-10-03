package me.uc_hussein.ultraslogin.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.uc_hussein.ultraslogin.proxy.PlayerContext;
import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;
import me.uc_hussein.ultraslogin.proxy.config.Settings;

import java.util.Optional;

/**
 * Moves players between the authentication location and the spawn. Different backend servers are connected
 * through Velocity; worlds/coordinates inside one backend are teleported by the bridge (Velocity cannot do that).
 */
public final class TeleportService {
    private final UltrasLoginPlugin plugin;

    public TeleportService(UltrasLoginPlugin plugin) {
        this.plugin = plugin;
    }

    public Settings.Location desired(PlayerContext ctx) {
        return ctx.mayLeaveAuthServer() ? plugin.settings().spawn : plugin.settings().authentication;
    }

    public Optional<RegisteredServer> initialServer(PlayerContext ctx) {
        return plugin.server().getServer(desired(ctx).server());
    }

    /** Makes sure the player ends up at {@link #desired}: connect to its server and/or ask the bridge to teleport. */
    public void route(PlayerContext ctx) {
        Optional<Player> opt = plugin.server().getPlayer(ctx.uuid());
        if (opt.isEmpty()) {
            return;
        }
        Player p = opt.get();
        Settings.Location loc = desired(ctx);
        Optional<RegisteredServer> target = plugin.server().getServer(loc.server());
        if (target.isEmpty()) {
            plugin.logger().error("Configured server '{}' does not exist in velocity.toml", loc.server());
            if (!ctx.mayLeaveAuthServer()) {
                p.disconnect(plugin.messages().kick(ctx.language(), "service-unavailable", null));
            }
            return;
        }
        ctx.setPendingTeleport(loc.backendTeleport());
        String current = p.getCurrentServer().map(sc -> sc.getServerInfo().getName()).orElse("");
        if (current.equalsIgnoreCase(loc.server())) {
            plugin.bridge().sync(ctx, false);
            return;
        }
        p.createConnectionRequest(target.get()).connect().whenComplete((result, err) -> {
            if (err != null || result == null || !result.isSuccessful()) {
                plugin.logger().warn("Could not connect {} to server '{}'", ctx.username(), loc.server());
            }
        });
    }
}
