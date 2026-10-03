package me.uc_hussein.ultraslogin.proxy.service;

import me.uc_hussein.ultraslogin.common.bridge.BridgeMessage;
import me.uc_hussein.ultraslogin.proxy.PlayerContext;
import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;
import me.uc_hussein.ultraslogin.proxy.config.Settings;

/**
 * Quiet configurable sounds. Velocity cannot play sounds itself, so they are played by the backend bridge.
 * Unknown sound keys simply play nothing (the client ignores them) - they can never crash anything.
 */
public final class SoundService {
    private final UltrasLoginPlugin plugin;

    public SoundService(UltrasLoginPlugin plugin) {
        this.plugin = plugin;
    }

    public void play(PlayerContext ctx, String id) {
        Settings s = plugin.settings();
        if (!s.soundsEnabled || !ctx.bridgeReady()) {
            return;
        }
        if (ctx.account() != null && !ctx.account().soundsEnabled()) {
            return;
        }
        Settings.SoundDef def = s.sounds.get(id);
        if (def == null) {
            return;
        }
        plugin.server().getPlayer(ctx.uuid()).ifPresent(p ->
                plugin.bridge().send(p, new BridgeMessage.Sound(ctx.uuid(), def.key(), def.volume(), def.pitch())));
    }
}
