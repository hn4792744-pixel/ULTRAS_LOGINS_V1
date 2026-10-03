package me.uc_hussein.ultraslogin.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Loads (or creates) a random secret stored hex-encoded in a file readable only by the owner where supported. */
public final class SecretStore {
    private SecretStore() {
    }

    public static byte[] loadOrCreate(Path file, int bytes) throws IOException {
        if (Files.exists(file)) {
            String hex = Files.readString(file, StandardCharsets.UTF_8).trim();
            try {
                byte[] data = HexFormat.of().parseHex(hex);
                if (data.length >= 16) {
                    return data;
                }
            } catch (IllegalArgumentException ignored) {
                // regenerate below only if the file is unusable
            }
            throw new IOException(file.getFileName() + " is corrupt; delete it to generate a new one (stored IP hashes / bridge signing will change)");
        }
        byte[] data = new byte[bytes];
        new SecureRandom().nextBytes(data);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, HexFormat.of().formatHex(data) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // non-POSIX file system
        }
        return data;
    }
}
