package me.uc_hussein.ultraslogin.proxy;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** All contexts of currently connected players. */
public final class PlayerRegistry {
    private final Map<UUID, PlayerContext> byUuid = new ConcurrentHashMap<>();

    public PlayerContext get(UUID id) {
        return byUuid.get(id);
    }

    /** Registers a context; returns false if a context for this UUID already exists. */
    public boolean register(PlayerContext ctx) {
        return byUuid.putIfAbsent(ctx.uuid(), ctx) == null;
    }

    public void remove(PlayerContext ctx) {
        byUuid.remove(ctx.uuid(), ctx);
    }

    public PlayerContext byName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (PlayerContext c : byUuid.values()) {
            if (c.username().toLowerCase(Locale.ROOT).equals(n)) {
                return c;
            }
        }
        return null;
    }

    public Collection<PlayerContext> all() {
        return byUuid.values();
    }
}
