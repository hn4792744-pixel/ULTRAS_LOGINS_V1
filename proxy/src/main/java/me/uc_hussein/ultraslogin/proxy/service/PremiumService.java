package me.uc_hussein.ultraslogin.proxy.service;

import me.uc_hussein.ultraslogin.common.flow.MojangLookup;
import me.uc_hussein.ultraslogin.proxy.UltrasLoginPlugin;
import me.uc_hussein.ultraslogin.proxy.config.Settings;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asks Mojang whether a name belongs to a Premium account. Only used to decide the login mode: ownership
 * itself is proven by Velocity's forced online mode (Mojang session check), never by this lookup.
 * Results are cached; failures are never treated as "premium" and never as "cracked" unless configured.
 */
public final class PremiumService {
    private record Entry(MojangLookup lookup, long expiresAt) {
    }

    private final UltrasLoginPlugin plugin;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private volatile HttpClient client = HttpClient.newHttpClient();
    private volatile int timeout = -1;

    public PremiumService(UltrasLoginPlugin plugin) {
        this.plugin = plugin;
    }

    private HttpClient client(Settings s) {
        if (timeout != s.premiumTimeoutSeconds) {
            client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(s.premiumTimeoutSeconds)).build();
            timeout = s.premiumTimeoutSeconds;
        }
        return client;
    }

    public CompletableFuture<MojangLookup> lookup(String name) {
        Settings s = plugin.settings();
        if (!MojangLookup.isValidJavaName(name)) {
            return CompletableFuture.completedFuture(new MojangLookup(MojangLookup.Status.NOT_PREMIUM, null));
        }
        String key = name.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        Entry cached = cache.get(key);
        if (cached != null && cached.expiresAt() > now) {
            return CompletableFuture.completedFuture(cached.lookup());
        }
        if (!plugin.rateLimits().allowMojangRequest()) {
            plugin.security().event("PREMIUM", "Mojang request budget exhausted - verification treated as unavailable");
            return CompletableFuture.completedFuture(new MojangLookup(MojangLookup.Status.ERROR, null));
        }
        String url = s.premiumLookupUrl.replace("%name%", name);       // name is validated: [A-Za-z0-9_]{1,16}
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(s.premiumTimeoutSeconds))
                    .header("User-Agent", "Ultras_login_v1").GET().build();
        } catch (IllegalArgumentException e) {
            plugin.security().event("PREMIUM", "premium.lookup-url is invalid");
            return CompletableFuture.completedFuture(new MojangLookup(MojangLookup.Status.ERROR, null));
        }
        return client(s).sendAsync(req, HttpResponse.BodyHandlers.ofString()).handle((resp, err) -> {
            MojangLookup result;
            long ttl;
            if (err != null || resp == null) {
                result = new MojangLookup(MojangLookup.Status.ERROR, null);
                ttl = s.premiumErrorCacheMillis;
                plugin.security().event("PREMIUM", "Mojang lookup failed for a login attempt (service unavailable)");
            } else {
                result = MojangLookup.fromResponse(resp.statusCode(), resp.body());
                ttl = switch (result.status()) {
                    case PREMIUM -> s.premiumCacheMillis;
                    case NOT_PREMIUM -> s.premiumNegativeCacheMillis;
                    case ERROR -> s.premiumErrorCacheMillis;
                };
            }
            if (ttl > 0) {
                cache.put(key, new Entry(result, System.currentTimeMillis() + ttl));
            }
            if (cache.size() > 5000) {
                long t = System.currentTimeMillis();
                cache.values().removeIf(e -> e.expiresAt() < t);
            }
            return result;
        });
    }

    public void clearCache() {
        cache.clear();
    }
}
