package me.uc_hussein.ultraslogin.bridge;

import me.uc_hussein.ultraslogin.common.bridge.BridgeCodec;
import me.uc_hussein.ultraslogin.common.bridge.BridgeException;
import me.uc_hussein.ultraslogin.common.bridge.BridgeMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Signed plugin-messaging link to the Velocity plugin. Every incoming message is verified before it is acted on. */
public final class BridgeLink implements PluginMessageListener {
    private static final Pattern SOUND_KEY = Pattern.compile("^[a-z0-9_.:/-]{1,100}$");

    private final UltrasBridgePlugin plugin;
    private volatile BridgeCodec codec;
    private long lastRejectLog;

    public BridgeLink(UltrasBridgePlugin plugin) {
        this.plugin = plugin;
    }

    public void setSecret(String secret) {
        if (secret == null || secret.trim().length() < 16) {
            codec = null;
            return;
        }
        codec = new BridgeCodec(secret.trim().getBytes(StandardCharsets.UTF_8), System::currentTimeMillis,
                plugin.getConfig().getLong("max-clock-skew-seconds", 60L) * 1000L);
    }

    public boolean ready() {
        return codec != null;
    }

    public void sendHello(Player p) {
        BridgeCodec c = codec;
        if (c == null || !p.isOnline()) {
            return;
        }
        p.sendPluginMessage(plugin, BridgeCodec.CHANNEL, c.encode(new BridgeMessage.Hello(p.getUniqueId(), BridgeCodec.PROTOCOL)));
    }

    public void sendClick(Player p, long guiId, String actionId) {
        BridgeCodec c = codec;
        if (c == null) {
            return;
        }
        p.sendPluginMessage(plugin, BridgeCodec.CHANNEL, c.encode(new BridgeMessage.GuiClick(p.getUniqueId(), guiId, actionId)));
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!BridgeCodec.CHANNEL.equals(channel)) {
            return;
        }
        BridgeCodec c = codec;
        if (c == null) {
            return;
        }
        BridgeMessage m;
        try {
            m = c.decode(data);
        } catch (BridgeException e) {
            long now = System.currentTimeMillis();
            if (now - lastRejectLog > 10_000) {
                lastRejectLog = now;
                plugin.getLogger().warning("Rejected a bridge message (" + e.getMessage() + "). Check that 'secret' matches the proxy.");
            }
            return;
        }
        if (!m.player().equals(player.getUniqueId())) {
            return; // only the proxy connection of this very player may address this player
        }
        switch (m) {
            case BridgeMessage.State s -> plugin.state().apply(s.player(), s.restricted(), s.flags());
            case BridgeMessage.GuiOpen g -> plugin.guis().open(player, g.gui());
            case BridgeMessage.GuiClose g -> plugin.guis().closeById(player, g.guiId());
            case BridgeMessage.Teleport t -> teleport(player, t);
            case BridgeMessage.Sound s -> sound(player, s);
            default -> { /* Hello and GuiClick only travel towards the proxy */ }
        }
    }

    private void teleport(Player p, BridgeMessage.Teleport t) {
        World w = Bukkit.getWorld(t.world());
        if (w == null) {
            plugin.getLogger().warning("Teleport requested to unknown world '" + t.world() + "' - check locations in the proxy config.");
            return;
        }
        plugin.state().allowNextTeleport(p.getUniqueId());
        p.teleportAsync(new Location(w, t.x(), t.y(), t.z(), t.yaw(), t.pitch())).thenAccept(ok -> {
            if (!ok) {
                plugin.state().consumeInternalTeleport(p.getUniqueId());
            }
        });
    }

    private void sound(Player p, BridgeMessage.Sound s) {
        if (!SOUND_KEY.matcher(s.key()).matches()) {
            return;
        }
        float volume = Math.max(0f, Math.min(1f, s.volume()));
        float pitch = Math.max(0.5f, Math.min(2f, s.pitch()));
        p.playSound(p.getLocation(), s.key(), volume, pitch);
    }
}
