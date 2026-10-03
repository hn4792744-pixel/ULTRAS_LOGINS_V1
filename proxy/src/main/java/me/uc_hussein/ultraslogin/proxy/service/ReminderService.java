package me.uc_hussein.ultraslogin.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import me.uc_hussein.ultraslogin.common.flow.AuthFlow;
import me.uc_hussein.ultraslogin.common.model.AuthMode;
import me.uc_hussein.ultraslogin.proxy.PlayerContext;
import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;
import me.uc_hussein.ultraslogin.proxy.config.Settings;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * "Login required" / "Registration required" notices. The first notice is sent once when authentication becomes
 * required; repeating reminders are optional (login-reminder.enabled) and never repeat when disabled.
 */
public final class ReminderService {
    private final UltrasLoginPlugin plugin;

    public ReminderService(UltrasLoginPlugin plugin) {
        this.plugin = plugin;
    }

    private static String key(PlayerContext ctx) {
        return ctx.mode() == AuthMode.REGISTER ? "register" : "login";
    }

    /** Sends the first notice and starts the repeating reminder if enabled. */
    public void begin(PlayerContext ctx, Player p) {
        stop(ctx);
        if (ctx.isAuthenticated() || ctx.mode() == AuthMode.NONE) {
            return;
        }
        var m = plugin.messages();
        String lang = ctx.language();
        // why authentication is needed (IP change / expired session)
        if (ctx.reason() == AuthFlow.Reason.IP_CHANGED) {
            p.sendMessage(m.component(lang, "ip-changed", null));
        } else if (ctx.reason() == AuthFlow.Reason.SESSION_EXPIRED) {
            p.sendMessage(m.component(lang, "session-expired", null));
        }
        p.sendMessage(m.component(lang, key(ctx) + "-required", null));
        if (plugin.settings().educationEnabled && ctx.account() != null && ctx.account().notificationsEnabled()) {
            p.sendMessage(m.component(lang, key(ctx) + "-edu", null));
        }
        sendExtraChannels(ctx, p);
        Settings.Reminder r = plugin.settings().reminder;
        if (!r.enabled() || (ctx.account() != null && !ctx.account().remindersEnabled())) {
            return;
        }
        ScheduledTask task = plugin.server().getScheduler().buildTask(plugin, () -> tick(ctx))
                .delay(r.intervalSeconds(), TimeUnit.SECONDS).repeat(r.intervalSeconds(), TimeUnit.SECONDS).schedule();
        ctx.setReminderTask(task);
    }

    private void tick(PlayerContext ctx) {
        Player p = plugin.server().getPlayer(ctx.uuid()).orElse(null);
        if (p == null || ctx.isAuthenticated() || ctx.mode() == AuthMode.NONE || p.getCurrentServer().isEmpty()) {
            return;
        }
        if (plugin.settings().reminder.chat()) {
            p.sendMessage(plugin.messages().component(ctx.language(), key(ctx) + "-required-short", null));
        }
        sendExtraChannels(ctx, p);
    }

    private void sendExtraChannels(PlayerContext ctx, Player p) {
        Settings s = plugin.settings();
        var m = plugin.messages();
        String lang = ctx.language();
        String k = key(ctx);
        if (s.reminder.actionbar() && s.ui.actionbarEnabled()) {
            p.sendActionBar(m.firstLine(lang, k + "-required-short", null));
        }
        if (s.reminder.title() && s.ui.titleEnabled()) {
            p.showTitle(Title.title(m.firstLine(lang, k + "-title", null), m.firstLine(lang, k + "-subtitle", null),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }
        if (s.reminder.bossbar() && s.ui.bossbarEnabled()) {
            Component name = m.firstLine(lang, k + "-required-short", null);
            BossBar bar = ctx.bossBar();
            if (bar == null) {
                bar = BossBar.bossBar(name, 1f, color(s.ui.bossbarColor()), overlay(s.ui.bossbarOverlay()));
                ctx.setBossBar(bar);
                p.showBossBar(bar);
            } else {
                bar.name(name);
            }
        }
    }

    private static BossBar.Color color(String name) {
        try {
            return BossBar.Color.valueOf(name);
        } catch (IllegalArgumentException e) {
            return BossBar.Color.RED;
        }
    }

    private static BossBar.Overlay overlay(String name) {
        try {
            return BossBar.Overlay.valueOf(name);
        } catch (IllegalArgumentException e) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    /** Stops reminders and removes any boss bar. */
    public void stop(PlayerContext ctx) {
        ScheduledTask t = ctx.reminderTask();
        if (t != null) {
            t.cancel();
            ctx.setReminderTask(null);
        }
        BossBar bar = ctx.bossBar();
        if (bar != null) {
            plugin.server().getPlayer(ctx.uuid()).ifPresent(p -> p.hideBossBar(bar));
            ctx.setBossBar(null);
        }
    }
}
