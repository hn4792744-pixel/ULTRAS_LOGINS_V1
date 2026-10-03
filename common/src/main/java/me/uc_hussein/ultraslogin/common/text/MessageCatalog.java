package me.uc_hussein.ultraslogin.common.text;

import me.uc_hussein.ultraslogin.common.config.Cfg;
import me.uc_hussein.ultraslogin.common.model.LanguageCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** All message templates of all languages. A message is a list of lines (first line carries the prefix). */
public final class MessageCatalog {
    private final Map<String, Map<String, List<String>>> byLang;
    private final Map<String, Boolean> smallCaps;
    private final Map<String, String> names;

    private MessageCatalog(Map<String, Map<String, List<String>>> byLang, Map<String, Boolean> smallCaps, Map<String, String> names) {
        this.byLang = byLang;
        this.smallCaps = smallCaps;
        this.names = names;
    }

    /** @param files language code -> loaded messages_xx.yml */
    public static MessageCatalog of(Map<String, Cfg> files) {
        Map<String, Map<String, List<String>>> all = new HashMap<>();
        Map<String, Boolean> sc = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (Map.Entry<String, Cfg> e : files.entrySet()) {
            Map<String, List<String>> flat = new HashMap<>();
            for (String key : e.getValue().leafKeys()) {
                if (key.equals("file-version") || key.startsWith("meta.")) {
                    continue;
                }
                Object raw = e.getValue().raw(key);
                if (raw instanceof List<?> l) {
                    List<String> lines = new ArrayList<>();
                    for (Object o : l) {
                        lines.add(String.valueOf(o));
                    }
                    flat.put(key, lines);
                } else if (raw != null) {
                    flat.put(key, List.of(String.valueOf(raw)));
                }
            }
            all.put(e.getKey(), flat);
            sc.put(e.getKey(), e.getValue().bool("meta.small-caps", false));
            names.put(e.getKey(), e.getValue().string("meta.name"));
        }
        return new MessageCatalog(all, sc, names);
    }

    /** Lines for {@code key} in {@code lang}, falling back to English, then to the key itself. */
    public List<String> lines(String lang, String key) {
        List<String> l = byLang.getOrDefault(lang, Map.of()).get(key);
        if (l == null) {
            l = byLang.getOrDefault("en", Map.of()).get(key);
        }
        return l == null ? List.of(key) : l;
    }

    public String text(String lang, String key) {
        return String.join("\n", lines(lang, key));
    }

    public boolean hasKey(String lang, String key) {
        return byLang.getOrDefault(lang, Map.of()).containsKey(key);
    }

    public boolean smallCaps(String lang) {
        return smallCaps.getOrDefault(lang, false);
    }

    public String languageName(String lang) {
        String n = names.get(lang);
        return n == null || n.isEmpty() ? lang : n;
    }

    public java.util.Set<String> keys(String lang) {
        return byLang.getOrDefault(lang, Map.of()).keySet();
    }

    public static List<String> languages() {
        return LanguageCode.SUPPORTED;
    }
}
