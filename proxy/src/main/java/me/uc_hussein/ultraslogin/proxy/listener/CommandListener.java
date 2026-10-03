package me.uc_hussein.ultraslogin.proxy.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.proxy.Player;
import com.mojang.brigadier.tree.RootCommandNode;
import me.uc_hussein.ultraslogin.common.flow.CommandPolicy;
import me.uc_hussein.ultraslogin.proxy.PlayerContext;
import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;
import me.uc_hussein.ultraslogin.proxy.config.Settings;

import java.util.Locale;

/** Command blocking and per-player tab-completion filtering before authentication. */
public final class CommandListener {
    private final UltrasLoginPlugin plugin;

    public CommandListener(UltrasLoginPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onCommand(CommandExecuteEvent e) {
        if (!(e.getCommandSource() instanceof Player p)) {
            return;
        }
        PlayerContext ctx = plugin.registry().get(p.getUniqueId());
        Settings s = plugin.settings();
        if (ctx == null || ctx.isAuthenticated() || !s.restrictCommands) {
            return;
        }
        if (!CommandPolicy.allowed(e.getCommand(), s.allowedCommands)) {
            e.setResult(CommandExecuteEvent.CommandResult.denied());
            long now = System.currentTimeMillis();
            if (now - ctx.lastBlockedNotice() > 3000) {
                ctx.setLastBlockedNotice(now);
                p.sendMessage(plugin.messages().component(ctx.language(), "command-blocked", null));
            }
        }
    }

    /** Unauthenticated players only see the allowed commands in tab completion (their own tree, nobody else's). */
    @Subscribe
    public void onAvailableCommands(PlayerAvailableCommandsEvent e) {
        PlayerContext ctx = plugin.registry().get(e.getPlayer().getUniqueId());
        Settings s = plugin.settings();
        if (ctx == null || ctx.isAuthenticated() || !s.restrictCommands) {
            return;
        }
        RootCommandNode<?> root = e.getRootNode();
        root.getChildren().removeIf(node -> !s.allowedCommands.contains(node.getName().toLowerCase(Locale.ROOT)));
    }
}
