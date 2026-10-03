package me.uc_hussein.ultraslogin.proxy.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import me.uc_hussein.ultraslogin.proxy.PlayerContext;
import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared helpers for the commands. */
abstract class PlayerCommand implements SimpleCommand {
    protected final UltrasLoginPlugin plugin;

    PlayerCommand(UltrasLoginPlugin plugin) {
        this.plugin = plugin;
    }

    /** Returns the player's context, or null after telling the sender why the command cannot run. */
    protected PlayerContext requirePlayer(Invocation inv) {
        if (!plugin.ready()) {
            inv.source().sendMessage(net.kyori.adventure.text.Component.text("Authentication is unavailable."));
            return null;
        }
        if (!(inv.source() instanceof Player p)) {
            plugin.messages().send(inv.source(), "player-only", null);
            return null;
        }
        PlayerContext ctx = plugin.registry().get(p.getUniqueId());
        if (ctx == null) {
            return null;
        }
        if (!plugin.rateLimits().allowCommand(p.getUniqueId())) {
            p.sendMessage(plugin.messages().component(ctx.language(), "rate-limited", null));
            return null;
        }
        return ctx;
    }

    protected List<String> onlineNames(String prefix) {
        String low = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player p : plugin.server().getAllPlayers()) {
            if (p.getUsername().toLowerCase(Locale.ROOT).startsWith(low)) {
                out.add(p.getUsername());
            }
        }
        return out;
    }
}
