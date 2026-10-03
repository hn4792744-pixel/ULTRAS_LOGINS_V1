package me.uc_hussein.ultraslogin.proxy.service;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import me.uc_hussein.ultraslogin.common.config.Cfg;
import me.uc_hussein.ultraslogin.common.text.SmallCaps;
import me.uc_hussein.ultraslogin.proxy.PlayerContext;
import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Renders message templates (MiniMessage) in a player's own language. Visual identity: a regular-text
 * "UC |" prefix with a red gradient (never small caps), muted "◦" for secondary lines, small caps for English text.
 */
public final class MessageService {
    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern TAG_NAME = Pattern.compile("^[a-z0-9_]+$");

    private final UltrasLoginPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private volatile Map<String, TextColor> colors = Map.of();
    private volatile TagResolver tags = TagResolver.empty();
    private volatile Component prefix = Component.empty();
    private volatile Component dot = Component.text("\u25E6");
    private volatile boolean prefixEnabled = true;

    public MessageService(UltrasLoginPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(Cfg cfg, List<String> warnings) {
        Map<String, TextColor> fresh = new HashMap<>();
        TagResolver.Builder tb = TagResolver.builder();
        for (String key : cfg.keys("colors")) {
            TextColor c = parseColor(cfg.string("colors." + key));
            if (c == null) {
                warnings.add("config.yml: colors." + key + " is not a valid color - using white");
                c = NamedTextColor.WHITE;
            }
            fresh.put(key, c);
            if (TAG_NAME.matcher(key).matches()) {
                tb.resolver(TagResolver.resolver(key, Tag.styling(c)));
            }
        }
        tb.resolver(TagResolver.resolver("raw", Tag.styling()));          // marker for text that small caps must not touch
        colors = fresh;
        tags = tb.build();
        prefixEnabled = cfg.bool("prefix.enabled", true);
        String start = cfg.string("prefix.gradient.start");
        String end = cfg.string("prefix.gradient.end");
        if (!HEX.matcher(start).matches()) {
            warnings.add("config.yml: prefix.gradient.start must look like #rrggbb - using #ff5a4d");
            start = "#ff5a4d";
        }
        if (!HEX.matcher(end).matches()) {
            warnings.add("config.yml: prefix.gradient.end must look like #rrggbb - using #8b0000");
            end = "#8b0000";
        }
        String grad = cfg.bool("prefix.gradient.enabled", true) ? "<gradient:" + start + ":" + end + ">%s</gradient>" : "<" + start + ">%s";
        // Regular text: "UC" and "|" are separate gradients, joined by a space.
        prefix = mm.deserialize(grad.formatted(cfg.string("prefix.text")) + " " + grad.formatted(cfg.string("prefix.separator")), tags);
        dot = Component.text(cfg.string("prefix.dot"), color("muted"));
    }

    private static TextColor parseColor(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        if (s.startsWith("#")) {
            return TextColor.fromHexString(s);
        }
        return NamedTextColor.NAMES.value(s.toLowerCase().replace(' ', '_'));
    }

    public TextColor color(String name) {
        return colors.getOrDefault(name, NamedTextColor.WHITE);
    }

    public Component prefix() {
        return prefix;
    }

    // ---------------------------------------------------------------- rendering

    /** Renders one template line. */
    public Component line(String lang, String template, Placeholders ph) {
        String t = template;
        if (plugin.config().messages().smallCaps(lang)) {
            t = SmallCaps.convertTemplate(t);
        }
        if (!prefixEnabled) {
            t = t.replace("<prefix> ", "").replace("<prefix>", "");
        }
        TagResolver.Builder rb = TagResolver.builder().resolver(tags);
        if (prefixEnabled) {
            rb.resolver(Placeholder.component("prefix", prefix));
        }
        rb.resolver(Placeholder.component("dot", dot));
        if (ph != null) {
            for (Map.Entry<String, Component> e : ph.map().entrySet()) {
                t = t.replace("%" + e.getKey() + "%", "<" + e.getKey() + ">");
                rb.resolver(Placeholder.component(e.getKey(), e.getValue()));
            }
        }
        return mm.deserialize(t, rb.build());
    }

    /** Renders all lines of a message key, joined by newlines (one chat message). */
    public Component component(String lang, String key, Placeholders ph) {
        List<Component> parts = new ArrayList<>();
        for (String l : plugin.config().messages().lines(lang, key)) {
            parts.add(line(lang, l, ph));
        }
        return Component.join(JoinConfiguration.newlines(), parts);
    }

    /** First line of a message key only (action bar, boss bar, title). */
    public Component firstLine(String lang, String key, Placeholders ph) {
        return line(lang, plugin.config().messages().lines(lang, key).get(0), ph);
    }

    public String plain(String lang, String key) {
        String s = plugin.config().messages().text(lang, key);
        return plugin.config().messages().smallCaps(lang) ? SmallCaps.convert(s) : s;
    }

    // ---------------------------------------------------------------- sending

    public String langOf(CommandSource source) {
        if (source instanceof Player p) {
            PlayerContext ctx = plugin.registry().get(p.getUniqueId());
            if (ctx != null) {
                return ctx.language();
            }
        }
        return plugin.settings().defaultLanguage;
    }

    public void send(CommandSource to, String key, Placeholders ph) {
        to.sendMessage(component(langOf(to), key, ph));
    }

    public void send(PlayerContext ctx, Audience to, String key, Placeholders ph) {
        to.sendMessage(component(ctx.language(), key, ph));
    }

    public Component kick(String lang, String key, Placeholders ph) {
        return component(lang, key, ph);
    }
}
