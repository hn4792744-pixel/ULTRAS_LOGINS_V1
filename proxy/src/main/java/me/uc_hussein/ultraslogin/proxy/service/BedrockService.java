package me.uc_hussein.ultraslogin.proxy.service;

import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Bedrock detection through the official Floodgate API. Identity always comes from Floodgate - a username
 * (even one starting with the Floodgate prefix) is never treated as proof of anything.
 */
public final class BedrockService {
    private final boolean present;
    private final Logger logger;

    public BedrockService(ProxyServer server, Logger logger) {
        this.logger = logger;
        this.present = server.getPluginManager().isLoaded("floodgate");
        if (!present) {
            logger.info("Floodgate not found: Bedrock detection disabled (all non-premium players are treated as cracked).");
        }
    }

    public boolean floodgatePresent() {
        return present;
    }

    public boolean isBedrock(UUID uuid) {
        if (!present || uuid == null) {
            return false;
        }
        try {
            return FloodgateBridge.isFloodgatePlayer(uuid);
        } catch (LinkageError | RuntimeException e) {
            logger.warn("Floodgate API call failed ({}): treating the player as non-Bedrock", e.getClass().getSimpleName());
            return false;
        }
    }

    /** Floodgate username prefix (default "."), or "" if unknown. */
    public String prefix() {
        if (!present) {
            return "";
        }
        try {
            return FloodgateBridge.prefix();
        } catch (LinkageError | RuntimeException e) {
            return "";
        }
    }
}
