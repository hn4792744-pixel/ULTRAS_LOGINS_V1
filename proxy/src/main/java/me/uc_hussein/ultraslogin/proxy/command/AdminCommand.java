package me.uc_hussein.ultraslogin.proxy.command;

import me.uc_hussein.ultraslogin.proxy.PlayerContext;
import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;
import me.uc_hussein.ultraslogin.proxy.service.Placeholders;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /ultraslogin reload | info | unregister | force-login | force-logout | session. */
public final class AdminCommand extends PlayerCommand {
    private static final List<String> SUBS = List.of("reload", "info", "unregister", "force-login", "force-logout", "session");

    public AdminCommand(UltrasLoginPlugin plugin) {
        super(plugin);
    }

    private String permissionOf(String sub) {
        return switch (sub) {
            case "reload" -> "ultraslogin.admin.reload";
            case "info" -> "ultraslogin.admin.info";
            case "unregister" -> "ultraslogin.admin.unregister";
            case "session", "force-login", "force-logout" -> "ultraslogin.admin.session";
            default -> "ultraslogin.admin";
        };
    }

    @Override
    public boolean hasPermission(Invocation inv) {
        for (String s : SUBS) {
            if (plugin.has(inv.source(), permissionOf(s))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void execute(Invocation inv) {
        var src = inv.source();
        var m = plugin.messages();
        if (!plugin.ready()) {
            return;
        }
        String[] a = inv.arguments();
        if (a.length == 0) {
            m.send(src, "admin-help", null);
            return;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (!SUBS.contains(sub)) {
            m.send(src, "admin-help", null);
            return;
        }
        if (!plugin.has(src, permissionOf(sub))) {
            m.send(src, "no-permission", null);
            return;
        }
        if (sub.equals("reload")) {
            int warnings = plugin.reload();
            m.send(src, warnings == 0 ? "config-reloaded" : "config-reload-warnings", Placeholders.of().text("count", String.valueOf(warnings)));
            return;
        }
        if (a.length != 2) {
            m.send(src, "admin-help", null);
            return;
        }
        String target = a[1];
        switch (sub) {
            case "info" -> plugin.auth().info(target, src, false);
            case "session" -> plugin.auth().info(target, src, true);
            case "unregister" -> plugin.auth().adminUnregister(target, src);
            default -> {
                PlayerContext ctx = plugin.registry().byName(target);
                if (ctx == null) {
                    m.send(src, "admin-not-found", Placeholders.of().text("player", target));
                } else if (sub.equals("force-login")) {
                    plugin.auth().forceLogin(ctx, src);
                } else {
                    plugin.auth().forceLogout(ctx, src);
                }
            }
        }
    }

    @Override
    public List<String> suggest(Invocation inv) {
        String[] a = inv.arguments();
        List<String> out = new ArrayList<>();
        if (a.length <= 1) {
            String p = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
            for (String s : SUBS) {
                if (s.startsWith(p) && plugin.has(inv.source(), permissionOf(s))) {
                    out.add(s);
                }
            }
        } else if (a.length == 2 && !a[0].equalsIgnoreCase("reload")) {
            out.addAll(onlineNames(a[1]));
        }
        return out;
    }
}
