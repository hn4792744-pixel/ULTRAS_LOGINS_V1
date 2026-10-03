package me.uc_hussein.ultraslogin.bridge;

import me.uc_hussein.ultraslogin.common.bridge.RestrictionFlags;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player restriction state on this backend. Fail-closed: a player the proxy has not released yet is
 * restricted with the safe default flags (when {@code restrict-until-synced} is true).
 */
public final class BridgeState {
    public record Entry(boolean restricted, int flags) {
    }

    private static final Entry RELEASED = new Entry(false, 0);

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final Set<UUID> internalTeleports = ConcurrentHashMap.newKeySet();
    private final Set<UUID> synced = ConcurrentHashMap.newKeySet();
    private volatile boolean restrictUntilSynced = true;
    private volatile boolean secretOk = true;

    public void configure(boolean restrictUntilSynced, boolean secretOk) {
        this.restrictUntilSynced = restrictUntilSynced;
        this.secretOk = secretOk;
    }

    /** True while the proxy has never answered for this player (used to keep sending Hello). */
    public boolean isSynced(UUID id) {
        return synced.contains(id);
    }

    public void apply(UUID id, boolean restricted, int flags) {
        synced.add(id);
        entries.put(id, restricted ? new Entry(true, flags) : RELEASED);
    }

    public Entry of(UUID id) {
        Entry e = entries.get(id);
        if (e != null) {
            return e;
        }
        // No answer from the proxy yet (or no valid secret): fail closed.
        if (restrictUntilSynced || !secretOk) {
            return new Entry(true, RestrictionFlags.safeDefault());
        }
        return RELEASED;
    }

    public boolean blocks(UUID id, int flag) {
        Entry e = of(id);
        return e.restricted() && RestrictionFlags.has(e.flags(), flag);
    }

    public void forget(UUID id) {
        entries.remove(id);
        synced.remove(id);
        internalTeleports.remove(id);
    }

    public void allowNextTeleport(UUID id) {
        internalTeleports.add(id);
    }

    public boolean consumeInternalTeleport(UUID id) {
        return internalTeleports.remove(id);
    }
}
