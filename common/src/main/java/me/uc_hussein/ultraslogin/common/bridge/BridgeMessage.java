package me.uc_hussein.ultraslogin.common.bridge;

import java.util.UUID;

/** Messages exchanged between the Velocity plugin and the backend bridge. */
public sealed interface BridgeMessage {
    UUID player();

    /** backend -> proxy: the bridge is ready for this player. */
    record Hello(UUID player, int protocol) implements BridgeMessage {
    }

    /** proxy -> backend: restrict (or release) the player. */
    record State(UUID player, boolean restricted, int flags) implements BridgeMessage {
    }

    /** proxy -> backend: show / refresh a menu. */
    record GuiOpen(UUID player, GuiModel gui) implements BridgeMessage {
    }

    /** proxy -> backend: close a menu. */
    record GuiClose(UUID player, long guiId) implements BridgeMessage {
    }

    /** proxy -> backend: teleport inside this backend. */
    record Teleport(UUID player, String world, double x, double y, double z, float yaw, float pitch) implements BridgeMessage {
    }

    /** proxy -> backend: play a quiet sound. */
    record Sound(UUID player, String key, float volume, float pitch) implements BridgeMessage {
    }

    /** backend -> proxy: the player clicked an action item. */
    record GuiClick(UUID player, long guiId, String actionId) implements BridgeMessage {
    }
}
