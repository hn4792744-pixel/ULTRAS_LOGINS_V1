package me.uc_hussein.ultraslogin.common.model;

import java.util.Locale;
import java.util.UUID;

/**
 * One stored account. Never contains a plaintext password. Instances are plain data holders;
 * persist a {@link #copy()} so that background writes cannot race with later modifications.
 */
public final class Account {
    private long id;
    private UUID uuid;
    private String username;
    private AccountType type;
    private String passwordHash;        // Argon2id PHC string, null = not registered
    private String language;            // "en" / "ar", null = never selected
    private Long registeredAt;
    private Long lastLogin;
    private String lastIpHash;
    private String sessionTokenHash;
    private Long sessionCreatedAt;
    private Long sessionExpiresAt;
    private boolean authenticated;
    private boolean notificationsEnabled = true;
    private boolean soundsEnabled = true;
    private boolean remindersEnabled = true;
    private long createdAt;

    public Account() {
    }

    public Account(UUID uuid, String username, AccountType type, long now) {
        this.uuid = uuid;
        this.username = username;
        this.type = type;
        this.createdAt = now;
    }

    public Account copy() {
        Account a = new Account();
        a.id = id; a.uuid = uuid; a.username = username; a.type = type; a.passwordHash = passwordHash;
        a.language = language; a.registeredAt = registeredAt; a.lastLogin = lastLogin; a.lastIpHash = lastIpHash;
        a.sessionTokenHash = sessionTokenHash; a.sessionCreatedAt = sessionCreatedAt; a.sessionExpiresAt = sessionExpiresAt;
        a.authenticated = authenticated; a.notificationsEnabled = notificationsEnabled; a.soundsEnabled = soundsEnabled;
        a.remindersEnabled = remindersEnabled; a.createdAt = createdAt;
        return a;
    }

    public boolean isRegistered() { return passwordHash != null; }
    public boolean hasLanguage() { return language != null; }
    public String usernameLower() { return username.toLowerCase(Locale.ROOT); }

    public long id() { return id; }
    public void setId(long id) { this.id = id; }
    public UUID uuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }
    public String username() { return username; }
    public void setUsername(String username) { this.username = username; }
    public AccountType type() { return type; }
    public void setType(AccountType type) { this.type = type; }
    public String passwordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String language() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Long registeredAt() { return registeredAt; }
    public void setRegisteredAt(Long registeredAt) { this.registeredAt = registeredAt; }
    public Long lastLogin() { return lastLogin; }
    public void setLastLogin(Long lastLogin) { this.lastLogin = lastLogin; }
    public String lastIpHash() { return lastIpHash; }
    public void setLastIpHash(String lastIpHash) { this.lastIpHash = lastIpHash; }
    public String sessionTokenHash() { return sessionTokenHash; }
    public void setSessionTokenHash(String h) { this.sessionTokenHash = h; }
    public Long sessionCreatedAt() { return sessionCreatedAt; }
    public void setSessionCreatedAt(Long v) { this.sessionCreatedAt = v; }
    public Long sessionExpiresAt() { return sessionExpiresAt; }
    public void setSessionExpiresAt(Long v) { this.sessionExpiresAt = v; }
    public boolean authenticated() { return authenticated; }
    public void setAuthenticated(boolean authenticated) { this.authenticated = authenticated; }
    public boolean notificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean v) { this.notificationsEnabled = v; }
    public boolean soundsEnabled() { return soundsEnabled; }
    public void setSoundsEnabled(boolean v) { this.soundsEnabled = v; }
    public boolean remindersEnabled() { return remindersEnabled; }
    public void setRemindersEnabled(boolean v) { this.remindersEnabled = v; }
    public long createdAt() { return createdAt; }
    public void setCreatedAt(long v) { this.createdAt = v; }

    @Override
    public String toString() {                      // deliberately omits hashes / tokens
        return "Account{" + username + ", " + type + ", " + uuid + "}";
    }
}
