package me.uc_hussein.ultraslogin.proxy.command;

import com.velocitypowered.api.proxy.Player;
import me.uc_hussein.ultraslogin.proxy.PlayerContext;
import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;

import java.util.List;

/** /login ‹password› - handled on the proxy, so the password never reaches a backend server. */
public final class LoginCommand extends PlayerCommand {
    public LoginCommand(UltrasLoginPlugin plugin) {
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
        if (args.length != 1) {
            p.sendMessage(plugin.messages().component(ctx.language(), "login-usage", null));
            return;
        }
        plugin.auth().login(ctx, p, args[0]);
    }

    @Override
    public List<String> suggest(Invocation inv) {
        return List.of();
    }
}
