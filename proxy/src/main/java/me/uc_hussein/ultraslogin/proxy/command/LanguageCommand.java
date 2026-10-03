package me.uc_hussein.ultraslogin.proxy.command;

import com.velocitypowered.api.proxy.Player;
import me.uc_hussein.ultraslogin.common.model.AuthState;
import me.uc_hussein.ultraslogin.common.model.LanguageCode;
import me.uc_hussein.ultraslogin.proxy.PlayerContext;
import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;
import me.uc_hussein.ultraslogin.proxy.service.Placeholders;

import java.util.List;

/** /language  (opens the language menu)  or  /language en|ar. */
public final class LanguageCommand extends PlayerCommand {
    public LanguageCommand(UltrasLoginPlugin plugin) {
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
        if (args.length == 0) {
            if (!ctx.bridgeReady()) {
                p.sendMessage(plugin.messages().component(ctx.language(), "gui-unavailable", null));
                return;
            }
            plugin.gui().openFor(ctx, false);
            return;
        }
        String code = LanguageCode.normalize(args[0]);
        if (code == null || !plugin.languages().set(ctx, code)) {
            p.sendMessage(plugin.messages().component(ctx.language(), "language-usage", null));
            return;
        }
        boolean pending = ctx.state() == AuthState.LANGUAGE_SELECTION;
        if (pending) {
            plugin.auth().finishLanguage(ctx);
        }
        p.sendMessage(plugin.messages().component(ctx.language(), "language-changed",
                Placeholders.of().text("language", plugin.languages().displayName(code))));
        plugin.gui().refresh(ctx);
    }

    @Override
    public List<String> suggest(Invocation inv) {
        return inv.arguments().length <= 1 ? LanguageCode.SUPPORTED : List.of();
    }
}
