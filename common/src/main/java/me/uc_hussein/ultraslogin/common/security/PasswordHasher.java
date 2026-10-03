package me.uc_hussein.ultraslogin.common.security;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Argon2id password hashing (pure Java, Bouncy Castle). Hashes are stored as PHC strings
 * {@code $argon2id$v=19$m=..,t=..,p=..$salt$hash}; salt and parameters travel with the hash.
 * Plaintext passwords are never stored or logged and never appear in exception messages.
 */
public final class PasswordHasher {
    public record Params(int memoryKb, int iterations, int parallelism) {
        public Params {
            if (memoryKb < 8 || iterations < 1 || parallelism < 1) {
                throw new IllegalArgumentException("invalid Argon2 parameters");
            }
        }
    }

    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Params params;

    public PasswordHasher(Params params) {
        this.params = params;
    }

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] out = derive(password, salt, params.memoryKb(), params.iterations(), params.parallelism(), HASH_BYTES);
        Base64.Encoder enc = Base64.getEncoder().withoutPadding();
        return "$argon2id$v=19$m=" + params.memoryKb() + ",t=" + params.iterations() + ",p=" + params.parallelism()
                + "$" + enc.encodeToString(salt) + "$" + enc.encodeToString(out);
    }

    /** Constant-time verification; returns false for any malformed hash. */
    public boolean verify(String password, String phc) {
        Parsed p = parse(phc);
        if (p == null || password == null) {
            return false;
        }
        try {
            byte[] actual = derive(password, p.salt, p.memoryKb, p.iterations, p.parallelism, p.hash.length);
            return MessageDigest.isEqual(actual, p.hash);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True if the stored hash uses weaker parameters than currently configured (rehash after login). */
    public boolean needsRehash(String phc) {
        Parsed p = parse(phc);
        return p == null || p.memoryKb < params.memoryKb() || p.iterations < params.iterations()
                || p.parallelism != params.parallelism();
    }

    private static byte[] derive(String password, byte[] salt, int memoryKb, int iterations, int parallelism, int length) {
        Argon2Parameters ap = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(iterations)
                .withMemoryAsKB(memoryKb)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build();
        Argon2BytesGenerator gen = new Argon2BytesGenerator();
        gen.init(ap);
        byte[] pw = password.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[length];
        gen.generateBytes(pw, out);
        java.util.Arrays.fill(pw, (byte) 0);
        return out;
    }

    private record Parsed(int memoryKb, int iterations, int parallelism, byte[] salt, byte[] hash) {
    }

    private static Parsed parse(String phc) {
        if (phc == null) {
            return null;
        }
        String[] parts = phc.split("\\$");
        // "", "argon2id", "v=19", "m=..,t=..,p=..", salt, hash
        if (parts.length != 6 || !parts[1].equals("argon2id") || !parts[2].equals("v=19")) {
            return null;
        }
        try {
            int m = -1;
            int t = -1;
            int par = -1;
            for (String kv : parts[3].split(",")) {
                String[] x = kv.split("=");
                switch (x[0]) {
                    case "m" -> m = Integer.parseInt(x[1]);
                    case "t" -> t = Integer.parseInt(x[1]);
                    case "p" -> par = Integer.parseInt(x[1]);
                    default -> { return null; }
                }
            }
            if (m < 8 || t < 1 || par < 1 || m > 4_194_304 || t > 100 || par > 64) {
                return null;                        // refuse absurd parameters from a tampered database
            }
            Base64.Decoder dec = Base64.getDecoder();
            byte[] salt = dec.decode(parts[4]);
            byte[] hash = dec.decode(parts[5]);
            if (salt.length < 8 || hash.length < 16) {
                return null;
            }
            return new Parsed(m, t, par, salt, hash);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
