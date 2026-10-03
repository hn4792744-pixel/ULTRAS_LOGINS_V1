package me.uc_hussein.ultraslogin.common.bridge;

import me.uc_hussein.ultraslogin.common.config.Cfg;

/** Bit mask of the restrictions the backend bridge enforces on a not-yet-authenticated player. */
public final class RestrictionFlags {
    public static final int MOVEMENT = 1;
    public static final int JUMPING = 1 << 1;
    public static final int SPRINTING = 1 << 2;
    public static final int INTERACTION = 1 << 3;
    public static final int BLOCK_BREAK = 1 << 4;
    public static final int BLOCK_PLACE = 1 << 5;
    public static final int DAMAGE = 1 << 6;
    public static final int INVENTORY = 1 << 7;
    public static final int ITEM_DROP = 1 << 8;
    public static final int ITEM_PICKUP = 1 << 9;
    public static final int CHAT = 1 << 10;
    public static final int COMMANDS = 1 << 11;
    public static final int TELEPORT = 1 << 12;

    private static final String[] KEYS = {"movement", "jumping", "sprinting", "interaction", "block-break", "block-place",
            "damage", "inventory", "item-drop", "item-pickup", "chat", "commands", "teleport"};
    private static final boolean[] DEFAULTS = {true, true, true, true, true, true, true, true, true, true, false, true, true};

    private RestrictionFlags() {
    }

    public static int all() {
        return (1 << KEYS.length) - 1;
    }

    /** Safe default used by the bridge before the proxy says anything: everything except chat. */
    public static int safeDefault() {
        int f = 0;
        for (int i = 0; i < KEYS.length; i++) {
            if (DEFAULTS[i]) {
                f |= 1 << i;
            }
        }
        return f;
    }

    public static boolean has(int flags, int bit) {
        return (flags & bit) != 0;
    }

    /** Reads restrictions.* (all disabled when security.restrict-before-login is false). */
    public static int fromConfig(Cfg c) {
        if (!c.bool("security.restrict-before-login", true)) {
            return 0;
        }
        int f = 0;
        for (int i = 0; i < KEYS.length; i++) {
            if (c.bool("restrictions." + KEYS[i], DEFAULTS[i])) {
                f |= 1 << i;
            }
        }
        return f;
    }
}
