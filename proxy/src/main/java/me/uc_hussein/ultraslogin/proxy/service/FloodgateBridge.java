package me.uc_hussein.ultraslogin.proxy.service;

import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

/** The only class that touches Floodgate classes, so the plugin also runs without Floodgate installed. */
final class FloodgateBridge {
    private FloodgateBridge() {
    }

    static boolean isFloodgatePlayer(UUID uuid) {
        return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
    }

    static String prefix() {
        return FloodgateApi.getInstance().getPlayerPrefix();
    }
}
