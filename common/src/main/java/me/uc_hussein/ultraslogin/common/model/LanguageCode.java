package me.uc_hussein.ultraslogin.common.model;

import java.util.List;
import java.util.Locale;

/** Supported player languages. */
public final class LanguageCode {
    public static final List<String> SUPPORTED = List.of("en", "ar");

    private LanguageCode() {
    }

    /** Returns "en" / "ar" for accepted spellings, otherwise null. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "en": case "english": case "en_us": case "en-us": return "en";
            case "ar": case "arabic": case "ar_sa": case "ar-sa": case "عربي": case "العربية": return "ar";
            default: return null;
        }
    }
}
