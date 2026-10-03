package me.uc_hussein.ultraslogin.common.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A YAML file on disk backed by the bundled default of the same name. Missing or wrongly typed values
 * fall back to the bundled default (wrong types add a warning); a broken file never throws.
 * NOTE: YAML 1.1 turns unquoted on/off/yes/no into booleans - never use them as keys or values.
 */
public final class Cfg {
    private final String name;
    private final Map<String, Object> disk;
    private final Map<String, Object> defaults;
    private final List<String> warnings;

    private Cfg(String name, Map<String, Object> disk, Map<String, Object> defaults, List<String> warnings) {
        this.name = name;
        this.disk = disk == null ? Map.of() : disk;
        this.defaults = defaults == null ? Map.of() : defaults;
        this.warnings = warnings;
    }

    /** For tests and programmatic use. */
    public static Cfg ofMaps(String name, Map<String, Object> disk, Map<String, Object> defaults, List<String> warnings) {
        return new Cfg(name, disk, defaults, warnings);
    }

    /**
     * Loads {@code file}. If it does not exist the bundled default is copied. If the bundled
     * {@code file-version} is newer, the old file is backed up and replaced.
     */
    public static Cfg load(Path file, Supplier<InputStream> bundled, String name, List<String> warnings) {
        Map<String, Object> defaults = parse(bundled.get(), name + " (bundled)", warnings);
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            if (!Files.exists(file)) {
                try (InputStream in = bundled.get()) {
                    if (in != null) {
                        Files.copy(in, file);
                    }
                }
            }
        } catch (IOException e) {
            warnings.add(name + ": could not create file (" + e.getMessage() + ")");
        }
        Map<String, Object> disk = Map.of();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                disk = parse(in, name, warnings);
            } catch (IOException e) {
                warnings.add(name + ": could not be read (" + e.getMessage() + ") - using defaults");
            }
            int bundledVersion = asInt(defaults.get("file-version"));
            if (bundledVersion > asInt(disk.get("file-version"))) {
                try {
                    Path backup = file.resolveSibling(file.getFileName() + ".old-" + System.currentTimeMillis());
                    Files.move(file, backup);
                    try (InputStream in = bundled.get()) {
                        Files.copy(in, file);
                    }
                    disk = defaults;
                    warnings.add(name + " was updated to version " + bundledVersion + " (old file saved as " + backup.getFileName() + ")");
                } catch (IOException e) {
                    warnings.add(name + ": could not update to the new version (" + e.getMessage() + ")");
                }
            }
        }
        return new Cfg(name, disk, defaults, warnings);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(InputStream in, String name, List<String> warnings) {
        if (in == null) {
            return Map.of();
        }
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Object o = new Yaml(new SafeConstructor(new LoaderOptions())).load(r);
            return o instanceof Map ? (Map<String, Object>) o : Map.of();
        } catch (Exception e) {
            String first = String.valueOf(e.getMessage()).split("\n")[0];
            warnings.add(name + ": invalid YAML (" + first + ") - using defaults");
            return Map.of();
        }
    }

    private static int asInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private static Object walk(Map<String, Object> root, String path) {
        Object cur = root;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof Map)) {
                return null;
            }
            cur = ((Map<String, Object>) cur).get(part);
            if (cur == null) {
                return null;
            }
        }
        return cur;
    }

    private void warn(String path, String problem) {
        String m = name + ": '" + path + "' " + problem + " - using default";
        if (!warnings.contains(m)) {
            warnings.add(m);
        }
    }

    public void addWarning(String text) {
        String m = name + ": " + text;
        if (!warnings.contains(m)) {
            warnings.add(m);
        }
    }

    public boolean has(String path) {
        return walk(disk, path) != null || walk(defaults, path) != null;
    }

    public Object raw(String path) {
        Object v = walk(disk, path);
        return v != null ? v : walk(defaults, path);
    }

    public String string(String path) {
        Object v = walk(disk, path);
        if (v instanceof String || v instanceof Number || v instanceof Boolean) {
            return String.valueOf(v);
        }
        if (v != null && !(v instanceof Map)) {
            warn(path, "expected text");
        }
        Object d = walk(defaults, path);
        return d == null ? "" : String.valueOf(d);
    }

    public boolean bool(String path, boolean fallback) {
        Object v = walk(disk, path);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s && (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false"))) {
            return Boolean.parseBoolean(s);
        }
        if (v != null) {
            warn(path, "expected true/false");
        }
        Object d = walk(defaults, path);
        return d instanceof Boolean b ? b : fallback;
    }

    public long number(String path, long fallback) {
        Object v = walk(disk, path);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        if (v != null) {
            warn(path, "expected a whole number");
        }
        Object d = walk(defaults, path);
        return d instanceof Number n ? n.longValue() : fallback;
    }

    public int integer(String path, int fallback) {
        return (int) number(path, fallback);
    }

    public double decimal(String path, double fallback) {
        Object v = walk(disk, path);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            warn(path, "expected a number");
        }
        Object d = walk(defaults, path);
        return d instanceof Number n ? n.doubleValue() : fallback;
    }

    public List<String> stringList(String path) {
        Object v = walk(disk, path);
        if (!(v instanceof List)) {
            if (v != null) {
                warn(path, "expected a list");
            }
            v = walk(defaults, path);
        }
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object o : l) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public Set<String> keys(String path) {
        Object v = path.isEmpty() ? disk : walk(disk, path);
        if (!(v instanceof Map) || ((Map<String, Object>) v).isEmpty()) {
            v = path.isEmpty() ? defaults : walk(defaults, path);
        }
        return v instanceof Map ? new LinkedHashSet<>(((Map<String, Object>) v).keySet()) : new LinkedHashSet<>();
    }

    /** All dotted leaf keys (lists count as leaves) from bundled default and disk. */
    public Set<String> leafKeys() {
        Set<String> out = new LinkedHashSet<>();
        collect("", defaults, out);
        collect("", disk, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void collect(String prefix, Map<String, Object> map, Set<String> out) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = prefix.isEmpty() ? String.valueOf(e.getKey()) : prefix + "." + e.getKey();
            if (e.getValue() instanceof Map) {
                collect(key, (Map<String, Object>) e.getValue(), out);
            } else {
                out.add(key);
            }
        }
    }
}
