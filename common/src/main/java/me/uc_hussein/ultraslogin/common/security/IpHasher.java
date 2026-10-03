package me.uc_hussein.ultraslogin.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Keyed hash of an IP address (HMAC-SHA256). Raw IPs are never stored; without the secret key the stored
 * values cannot be brute-forced from the small IPv4 space.
 */
public final class IpHasher {
    private final byte[] secret;

    public IpHasher(byte[] secret) {
        if (secret == null || secret.length < 16) {
            throw new IllegalArgumentException("IP hash secret too short");
        }
        this.secret = secret.clone();
    }

    public String hash(String ip) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(ip.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
