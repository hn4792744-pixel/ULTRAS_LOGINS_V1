package me.uc_hussein.ultraslogin.proxy.service;

import me.uc_hussein.ultraslogin.common.model.Account;
import me.uc_hussein.ultraslogin.common.model.LanguageCode;
import me.uc_hussein.ultraslogin.proxy.PlayerContext;
import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;

/** Per-player language. Changing one player's language never touches anyone else or the server default. */
public final class LanguageService {
    private final UltrasLoginPlugin plugin;

    public LanguageService(UltrasLoginPlugin plugin) {
        this.plugin = plugin;
    }

    public String defaultLanguage() {
        return plugin.settings().defaultLanguage;
    }

    /** Stores the language on the player's context and account; returns false for unsupported codes. */
    public boolean set(PlayerContext ctx, String code) {
        String lang = LanguageCode.normalize(code);
        if (lang == null) {
            return false;
        }
        ctx.setLanguage(lang);
        Account a = ctx.account();
        if (a != null) {
            a.setLanguage(lang);
            plugin.accounts().save(a).exceptionally(t -> {
                plugin.logger().warn("Could not save the language of {}: {}", ctx.username(), t.getMessage());
                return null;
            });
        }
        return true;
    }

    public String displayName(String code) {
        return plugin.config().messages().languageName(code);
    }
}
