package me.uc_hussein.ultraslogin.proxy.service;

import com.velocitypowered.api.proxy.Player;
import me.uc_hussein.ultraslogin.common.bridge.BridgeMessage;
import me.uc_hussein.ultraslogin.common.bridge.GuiItemModel;
import me.uc_hussein.ultraslogin.common.bridge.GuiModel;
import me.uc_hussein.ultraslogin.common.config.Cfg;
import me.uc_hussein.ultraslogin.common.model.Account;
import me.uc_hussein.ultraslogin.common.model.AuthState;
import me.uc_hussein.ultraslogin.proxy.PlayerContext;
import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Menu logic. The proxy decides everything (layout from gui.yml, texts from messages_xx.yml, state of toggles);
 * the bridge only draws the model and reports which action id was clicked. Every click is validated against the
 * menu that is currently open for that player.
 */
public final class GuiService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UltrasLoginPlugin plugin;

    public GuiService(UltrasLoginPlugin plugin) {
        this.plugin = plugin;
    }

    private static String path(PlayerContext.GuiType t) {
        return switch (t) {
            case LANGUAGE -> "language";
            case FIRST_JOIN -> "first-join";
            case SETTINGS -> "settings";
        };
    }

    /** Opens (or replaces) the menu for a player. */
    public void open(PlayerContext ctx, PlayerContext.GuiType type) {
        ctx.setGui(type);
        ctx.setGuiId(RANDOM.nextLong());
        refresh(ctx);
    }

    /** Re-sends the current menu (same id) so that texts / toggle states update. */
    public void refresh(PlayerContext ctx) {
        PlayerContext.GuiType type = ctx.gui();
        if (type == null || !ctx.bridgeReady()) {
            return;
        }
        plugin.server().getPlayer(ctx.uuid()).ifPresent(p ->
                plugin.bridge().send(p, new BridgeMessage.GuiOpen(ctx.uuid(), build(ctx, type))));
    }

    public void close(PlayerContext ctx) {
        long id = ctx.guiId();
        ctx.setGui(null);
        if (ctx.bridgeReady()) {
            plugin.server().getPlayer(ctx.uuid()).ifPresent(p -> plugin.bridge().send(p, new BridgeMessage.GuiClose(ctx.uuid(), id)));
        }
    }

    private boolean closable(PlayerContext ctx) {
        return ctx.state() != AuthState.LANGUAGE_SELECTION;
    }

    GuiModel build(PlayerContext ctx, PlayerContext.GuiType type) {
        Cfg g = plugin.config().gui();
        String base = path(type);
        String lang = ctx.language();
        var msgs = plugin.messages();
        int rows = Math.max(1, Math.min(6, g.integer(base + ".rows", 3)));
        Account acc = ctx.account();
        List<GuiItemModel> items = new ArrayList<>();

        String fillerMat = g.string("filler.material");
        if (!fillerMat.isBlank()) {
            String fillerName = json(Component.text(" "));
            for (int i = 0; i < rows * 9; i++) {
                items.add(new GuiItemModel(i, fillerMat, fillerName, List.of(), "", false));
            }
        }
        for (String id : g.keys(base + ".items")) {
            String p = base + ".items." + id + ".";
            String showWhen = g.string(p + "show-when");
            boolean pending = ctx.state() == AuthState.LANGUAGE_SELECTION;
            if ((showWhen.equals("language-chosen") && pending) || (showWhen.equals("language-pending") && !pending)) {
                continue;
            }
            int slot = g.integer(p + "slot", -1);
            if (slot < 0 || slot >= rows * 9) {
                continue;
            }
            String action = g.string(p + "action");
            boolean glow = g.bool(p + "glow", false);
            Placeholders ph = Placeholders.of();
            String state = "";
            if (action.startsWith("toggle:") && acc != null) {
                boolean on = switch (action.substring(7)) {
                    case "notifications" -> acc.notificationsEnabled();
                    case "sounds" -> acc.soundsEnabled();
                    case "reminders" -> acc.remindersEnabled();
                    default -> false;
                };
                state = msgs.plain(lang, on ? "gui-state-enabled" : "gui-state-disabled");
                glow = on;
            }
            ph.text("state", state);
            if (action.startsWith("lang:") && action.length() == 7 && action.substring(5).equals(ctx.language())
                    && (acc != null && acc.hasLanguage())) {
                glow = true;
            }
            Component name = nameOf(g, p, lang, ph);
            List<String> lore = new ArrayList<>();
            for (Component c : loreOf(g, p, lang, ph)) {
                lore.add(json(c));
            }
            String material = g.string(p + "material").toUpperCase(Locale.ROOT);
            items.removeIf(it -> it.slot() == slot);
            items.add(new GuiItemModel(slot, material.isBlank() ? "STONE" : material, json(name), lore, action, glow));
        }
        String title = json(msgs.firstLine(lang, "gui-" + base + "-title", null));
        return new GuiModel(ctx.guiId(), title, rows, closable(ctx), items);
    }

    private Component nameOf(Cfg g, String p, String lang, Placeholders ph) {
        var msgs = plugin.messages();
        String key = g.string(p + "name-key");
        Component c = !key.isBlank() ? msgs.line(lang, plugin.config().messages().lines(lang, key).get(0), ph)
                : msgs.line(lang, g.string(p + "name"), ph);
        return c.decoration(TextDecoration.ITALIC, false);
    }

    private List<Component> loreOf(Cfg g, String p, String lang, Placeholders ph) {
        var msgs = plugin.messages();
        List<String> raw = new ArrayList<>();
        String key = g.string(p + "lore-key");
        if (!key.isBlank()) {
            raw.addAll(plugin.config().messages().lines(lang, key));
        } else {
            raw.addAll(g.stringList(p + "lore"));
        }
        List<Component> out = new ArrayList<>();
        for (String l : raw) {
            out.add(msgs.line(lang, l, ph).decoration(TextDecoration.ITALIC, false));
        }
        return out;
    }

    private static String json(Component c) {
        return GsonComponentSerializer.gson().serialize(c);
    }

    // ------------------------------------------------------------------ clicks

    /** Handles a click reported by the bridge. Anything that does not match the open menu is ignored. */
    public void handleClick(PlayerContext ctx, long guiId, String action) {
        if (ctx.gui() == null || guiId != ctx.guiId() || action == null || action.isEmpty() || action.length() > 40) {
            return;
        }
        if (!plugin.rateLimits().allowCommand(ctx.uuid())) {
            return;
        }
        Account acc = ctx.account();
        if (action.startsWith("lang:")) {
            String code = action.substring(5);
            if (code.equals("next")) {
                code = ctx.language().equals("en") ? "ar" : "en";
            }
            if (!plugin.languages().set(ctx, code)) {
                return;
            }
            plugin.sounds().play(ctx, "language");
            plugin.security().event("LANGUAGE", ctx.username() + " selected language " + ctx.language());
            onLanguageChosen(ctx);
        } else if (action.startsWith("toggle:") && acc != null) {
            switch (action.substring(7)) {
                case "notifications" -> acc.setNotificationsEnabled(!acc.notificationsEnabled());
                case "sounds" -> acc.setSoundsEnabled(!acc.soundsEnabled());
                case "reminders" -> acc.setRemindersEnabled(!acc.remindersEnabled());
                default -> {
                    return;
                }
            }
            plugin.accounts().save(acc).exceptionally(t -> {
                plugin.logger().warn("Could not save settings of {}: {}", ctx.username(), t.getMessage());
                return null;
            });
            plugin.sounds().play(ctx, "toggle");
            refresh(ctx);
        } else if ((action.equals("continue") || action.equals("close")) && ctx.state() != AuthState.LANGUAGE_SELECTION) {
            close(ctx);
        }
    }

    private void onLanguageChosen(PlayerContext ctx) {
        boolean wasPending = ctx.state() == AuthState.LANGUAGE_SELECTION;
        if (wasPending) {
            plugin.auth().finishLanguage(ctx);
        }
        if (ctx.gui() == PlayerContext.GuiType.LANGUAGE) {
            close(ctx);
        } else {
            refresh(ctx);                      // first-join / settings stay open in the new language
        }
    }

    /** Opens the right menu for the player's situation (language selection is mandatory on first join). */
    public void openFor(PlayerContext ctx, boolean settings) {
        if (ctx.state() == AuthState.LANGUAGE_SELECTION) {
            open(ctx, plugin.settings().firstJoinGui.equals("LANGUAGE")
                    ? PlayerContext.GuiType.LANGUAGE : PlayerContext.GuiType.FIRST_JOIN);
        } else {
            open(ctx, settings ? PlayerContext.GuiType.SETTINGS : PlayerContext.GuiType.LANGUAGE);
        }
    }

    public Player playerOf(PlayerContext ctx) {
        return plugin.server().getPlayer(ctx.uuid()).orElse(null);
    }
}
