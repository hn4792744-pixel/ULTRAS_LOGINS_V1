package me.uc_hussein.ultraslogin.proxy;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import me.uc_hussein.ultraslogin.common.flow.AuthFlow;
import me.uc_hussein.ultraslogin.common.model.Account;
import me.uc_hussein.ultraslogin.common.model.AccountType;
import me.uc_hussein.ultraslogin.common.model.AuthMode;
import me.uc_hussein.ultraslogin.common.model.AuthState;
import me.uc_hussein.ultraslogin.proxy.config.Settings;
import net.kyori.adventure.bossbar.BossBar;

import java.util.UUID;

/** Runtime state of one connected player. All state changes go through {@link #transition}. */
public final class PlayerContext {
    public enum GuiType { LANGUAGE, FIRST_JOIN, SETTINGS }

    private final UUID uuid;
    private final String username;
    private final AccountType kind;
    private final String ipHash;
    private volatile Account account;                 // null only when the database was unavailable (premium/bedrock fail-open)
    private volatile AuthState state = AuthState.NEW;
    private volatile AuthMode mode = AuthMode.NONE;
    private volatile AuthState afterLanguage = AuthState.AUTHENTICATION_REQUIRED;
    private volatile AuthFlow.Reason reason = AuthFlow.Reason.NONE;
    private volatile boolean identityVerified;        // premium / bedrock without auth: may leave the auth server
    private volatile boolean bridgeReady;
    private volatile String language;
    private volatile Settings.Location teleportTarget;
    private volatile GuiType gui;
    private volatile long guiId;
    private volatile boolean languageChosenInGui;
    private volatile ScheduledTask reminderTask;
    private volatile ScheduledTask helloTask;
    private volatile BossBar bossBar;
    private volatile long lastBlockedNotice;
    private volatile boolean disconnected;
    private volatile boolean announced;
    private volatile boolean pendingTeleport;

    public PlayerContext(Player player, AccountType kind, String ipHash, String language) {
        this.uuid = player.getUniqueId();
        this.username = player.getUsername();
        this.kind = kind;
        this.ipHash = ipHash;
        this.language = language;
    }

    /** Applies a state change if the state machine allows it. Returns false (and changes nothing) otherwise. */
    public synchronized boolean transition(AuthState next) {
        if (!state.canTransitionTo(next)) {
            return false;
        }
        state = next;
        return true;
    }

    public UUID uuid() { return uuid; }
    public String username() { return username; }
    public AccountType kind() { return kind; }
    public String ipHash() { return ipHash; }
    public Account account() { return account; }
    public void setAccount(Account a) { this.account = a; }
    public AuthState state() { return state; }
    public AuthMode mode() { return mode; }
    public void setMode(AuthMode m) { this.mode = m; }
    public AuthState afterLanguage() { return afterLanguage; }
    public void setAfterLanguage(AuthState s) { this.afterLanguage = s; }
    public AuthFlow.Reason reason() { return reason; }
    public void setReason(AuthFlow.Reason r) { this.reason = r; }
    public boolean identityVerified() { return identityVerified; }
    public void setIdentityVerified(boolean v) { this.identityVerified = v; }
    public boolean bridgeReady() { return bridgeReady; }
    public void setBridgeReady(boolean v) { this.bridgeReady = v; }
    public String language() { return language; }
    public void setLanguage(String l) { this.language = l; }
    public Settings.Location teleportTarget() { return teleportTarget; }
    public void setTeleportTarget(Settings.Location l) { this.teleportTarget = l; }
    public GuiType gui() { return gui; }
    public void setGui(GuiType g) { this.gui = g; }
    public long guiId() { return guiId; }
    public void setGuiId(long id) { this.guiId = id; }
    public boolean languageChosenInGui() { return languageChosenInGui; }
    public void setLanguageChosenInGui(boolean v) { this.languageChosenInGui = v; }
    public ScheduledTask reminderTask() { return reminderTask; }
    public void setReminderTask(ScheduledTask t) { this.reminderTask = t; }
    public ScheduledTask helloTask() { return helloTask; }
    public void setHelloTask(ScheduledTask t) { this.helloTask = t; }
    public BossBar bossBar() { return bossBar; }
    public void setBossBar(BossBar b) { this.bossBar = b; }
    public long lastBlockedNotice() { return lastBlockedNotice; }
    public void setLastBlockedNotice(long t) { this.lastBlockedNotice = t; }
    public boolean announced() { return announced; }
    public void setAnnounced(boolean v) { this.announced = v; }
    public boolean pendingTeleport() { return pendingTeleport; }
    public void setPendingTeleport(boolean v) { this.pendingTeleport = v; }
    public boolean disconnected() { return disconnected; }
    public void markDisconnected() { this.disconnected = true; }

    public boolean isAuthenticated() {
        return state.isAuthenticated();
    }

    /** May this player be on servers other than the authentication server? */
    public boolean mayLeaveAuthServer() {
        return identityVerified || state.isAuthenticated();
    }

    /** Does this player authenticate with a password (cracked, or bedrock with authentication enabled)? */
    public boolean usesPassword() {
        return !identityVerified && kind != AccountType.PREMIUM;
    }
}
