package me.uc_hussein.ultraslogin.proxy.command;

import com.velocitypowered.api.proxy.Player;
import me.uc_hussein.ultraslogin.proxy.PlayerContext;
import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;

import java.util.List;

/** /register ‹password› ‹password›  and  /register setting. */
public final class RegisterCommand extends PlayerCommand {
    public RegisterCommand(UltrasLoginPlugin plugin) {
        super(plugin);
    }

    @Override
    public void execute(Invocation inv) {
        PlayerContext ctx = requirePlayer(inv);
        if (ctx == null) {
            return;
        }
        Player p = (Player) inv.source();
        String[] args = inv.arguments();
        if (args.length == 1 && args[0].equalsIgnoreCase("setting")) {
            if (!ctx.bridgeReady()) {
                p.sendMessage(plugin.messages().component(ctx.language(), "gui-unavailable", null));
                return;
            }
            plugin.gui().openFor(ctx, true);
            return;
        }
        if (args.length != 2) {
            p.sendMessage(plugin.messages().component(ctx.language(), "register-usage", null));
            return;
        }
        plugin.auth().register(ctx, p, args[0], args[1]);
    }

    @Override
    public List<String> suggest(Invocation inv) {
        String[] a = inv.arguments();
        return a.length <= 1 && "setting".startsWith(a.length == 0 ? "" : a[0].toLowerCase()) ? List.of("setting") : List.of();
    }
}
