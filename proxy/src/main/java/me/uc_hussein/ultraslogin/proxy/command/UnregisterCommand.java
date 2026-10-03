package me.uc_hussein.ultraslogin.proxy.command;

import com.velocitypowered.api.proxy.Player;
import me.uc_hussein.ultraslogin.proxy.PlayerContext;
import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;

import java.util.List;

/**
 * /un_register ‹password› (own account, only when authenticated) and /un_register ‹player› (administrator).
 * An unauthenticated player can never unregister anything. For a player with the admin permission the argument
 * is treated as a target name if it is an online player or an existing account; otherwise it is their password.
 */
public final class UnregisterCommand extends PlayerCommand {
    public UnregisterCommand(UltrasLoginPlugin plugin) {
        super(plugin);
    }

    @Override
    public void execute(Invocation inv) {
        if (!plugin.ready()) {
            return;
        }
        String[] args = inv.arguments();
        boolean admin = plugin.has(inv.source(), "ultraslogin.admin.unregister");
        if (!(inv.source() instanceof Player p)) {
            if (args.length != 1) {
                plugin.messages().send(inv.source(), "admin-unregister-usage", null);
                return;
            }
            plugin.auth().adminUnregister(args[0], inv.source());          // console
            return;
        }
        PlayerContext ctx = requirePlayer(inv);
        if (ctx == null) {
            return;
        }
        if (args.length == 0) {
            plugin.auth().unregisterSelf(ctx, p, null);
            return;
        }
        if (args.length != 1) {
            p.sendMessage(plugin.messages().component(ctx.language(), "unregister-usage-password", null));
            return;
        }
        if (admin && ctx.isAuthenticated() && !args[0].equalsIgnoreCase(p.getUsername())) {
            String target = args[0];
            if (plugin.registry().byName(target) != null) {
                plugin.auth().adminUnregister(target, p);
                return;
            }
            plugin.accounts().findByNameAny(target).thenAccept(opt -> {
                if (opt.isPresent()) {
                    plugin.auth().adminUnregister(target, p);
                } else {
                    plugin.auth().unregisterSelf(ctx, p, target);          // it was the player's own password
                }
            }).exceptionally(t -> null);
            return;
        }
        plugin.auth().unregisterSelf(ctx, p, args[0]);
    }

    @Override
    public List<String> suggest(Invocation inv) {
        if (inv.arguments().length <= 1 && plugin.has(inv.source(), "ultraslogin.admin.unregister")) {
            return onlineNames(inv.arguments().length == 0 ? "" : inv.arguments()[0]);
        }
        return List.of();
    }
}
