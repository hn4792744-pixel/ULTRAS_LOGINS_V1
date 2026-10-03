package me.uc_hussein.ultraslogin.proxy.command;

import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;

import java.util.List;

/** /register_info ‹player› - safe account information (never the password, hash or tokens). */
public final class RegisterInfoCommand extends PlayerCommand {
    public RegisterInfoCommand(UltrasLoginPlugin plugin) {
        super(plugin);
    }

    @Override
    public void execute(Invocation inv) {
        if (!plugin.ready()) {
            return;
        }
        String[] args = inv.arguments();
        if (args.length != 1) {
            plugin.messages().send(inv.source(), "admin-info-usage", null);
            return;
        }
        plugin.auth().info(args[0], inv.source(), false);
    }

    @Override
    public boolean hasPermission(Invocation inv) {
        return plugin.has(inv.source(), "ultraslogin.admin.info");
    }

    @Override
    public List<String> suggest(Invocation inv) {
        return inv.arguments().length <= 1 ? onlineNames(inv.arguments().length == 0 ? "" : inv.arguments()[0]) : List.of();
    }
}
