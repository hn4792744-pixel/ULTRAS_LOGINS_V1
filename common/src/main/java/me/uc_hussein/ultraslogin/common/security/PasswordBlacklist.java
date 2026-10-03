package me.uc_hussein.ultraslogin.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Optional local list of common/compromised passwords (one per line, # = comment). */
public final class PasswordBlacklist {
    private PasswordBlacklist() {
    }

    public static Set<String> load(Path file) throws IOException {
        Set<String> out = new HashSet<>();
        if (file == null || !Files.exists(file)) {
            return out;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String s = line.trim();
            if (!s.isEmpty() && !s.startsWith("#")) {
                out.add(s.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }
}
