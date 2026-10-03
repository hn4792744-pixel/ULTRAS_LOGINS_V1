package me.uc_hussein.ultraslogin.proxy.service;

import me.uc_hussein.ultraslogin.common.security.RateLimiter;
import me.uc_hussein.ultraslogin.proxy.config.Settings;

import java.util.UUID;

/** Login/register/command spam protection and a global cap on Mojang API requests. */
public final class RateLimitService {
    private volatile RateLimiter auth;
    private volatile RateLimiter register;
    private volatile RateLimiter commands;
    private volatile RateLimiter mojang;
    private Settings.Limit lastAuth;
    private Settings.Limit lastRegister;
    private Settings.Limit lastCommands;
    private int lastMojang = -1;

    public synchronized void reconfigure(Settings s) {
        // limiters are only rebuilt when their numbers change, so a reload does not reset counters needlessly
        if (!s.authLimit.equals(lastAuth)) {
            auth = new RateLimiter(s.authLimit.limit(), s.authLimit.windowMillis());
            lastAuth = s.authLimit;
        }
        if (!s.registerLimit.equals(lastRegister)) {
            register = new RateLimiter(s.registerLimit.limit(), s.registerLimit.windowMillis());
            lastRegister = s.registerLimit;
        }
        if (!s.commandLimit.equals(lastCommands)) {
            commands = new RateLimiter(s.commandLimit.limit(), s.commandLimit.windowMillis());
            lastCommands = s.commandLimit;
        }
        if (s.mojangPerMinute != lastMojang) {
            mojang = new RateLimiter(s.mojangPerMinute, 60_000L);
            lastMojang = s.mojangPerMinute;
        }
    }

    public boolean allowLogin(UUID id) {
        return auth.tryAcquire(id.toString(), System.currentTimeMillis());
    }

    public boolean allowRegister(UUID id, String ipHash) {
        long now = System.currentTimeMillis();
        return register.tryAcquire("u:" + id, now) && register.tryAcquire("ip:" + ipHash, now);
    }

    public boolean allowCommand(UUID id) {
        return commands.tryAcquire(id.toString(), System.currentTimeMillis());
    }

    public boolean allowMojangRequest() {
        return mojang.tryAcquire("global", System.currentTimeMillis());
    }
}
