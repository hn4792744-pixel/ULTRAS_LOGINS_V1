package me.uc_hussein.ultraslogin.proxy.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;
import me.uc_hussein.ultraslogin.proxy.service.BridgeService;

/**
 * Receives bridge messages. Messages sent by a PLAYER on the bridge channel are never forwarded and never
 * processed (a forged payload from a client is simply dropped and logged).
 */
public final class BridgeListener {
    private final UltrasLoginPlugin plugin;

    public BridgeListener(UltrasLoginPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent e) {
        if (!e.getIdentifier().equals(BridgeService.CHANNEL)) {
            return;
        }
        e.setResult(PluginMessageEvent.ForwardResult.handled());     // never forwarded to anyone
        if (!plugin.ready()) {
            return;
        }
        if (e.getSource() instanceof ServerConnection from) {
            plugin.bridge().handle(from, e.getData());
        } else if (e.getSource() instanceof Player p) {
            plugin.bridge().rateLimitedLog("client " + p.getUsername() + " sent data on the bridge channel (ignored)");
        }
    }
}
