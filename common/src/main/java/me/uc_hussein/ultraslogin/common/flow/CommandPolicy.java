package me.uc_hussein.ultraslogin.common.flow;

import java.util.Locale;
import java.util.Set;

/** Which commands an unauthenticated player may run. */
public final class CommandPolicy {
    private CommandPolicy() {
    }

    /** Returns the lowercase command label (first word, no leading slash). */
    public static String label(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        int sp = s.indexOf(' ');
        return (sp < 0 ? s : s.substring(0, sp)).toLowerCase(Locale.ROOT);
    }

    /** Namespaced forms like "plugin:login" are only allowed if listed explicitly. */
    public static boolean allowed(String raw, Set<String> allowedLowercase) {
        String label = label(raw);
        return !label.isEmpty() && allowedLowercase.contains(label);
    }
}
