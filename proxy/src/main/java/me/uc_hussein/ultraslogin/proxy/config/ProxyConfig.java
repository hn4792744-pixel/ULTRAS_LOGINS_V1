package me.uc_hussein.ultraslogin.proxy.config;

import me.uc_hussein.ultraslogin.common.config.Cfg;
import me.uc_hussein.ultraslogin.common.model.LanguageCode;
import me.uc_hussein.ultraslogin.common.text.MessageCatalog;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads config.yml, gui.yml and messages_xx.yml; an atomic snapshot is swapped on reload. */
public final class ProxyConfig {
    public record Snapshot(Cfg config, Cfg gui, MessageCatalog messages, Settings settings, List<String> warnings) {
    }

    private final Path dir;
    private volatile Snapshot snapshot;

    public ProxyConfig(Path dir) {
        this.dir = dir;
    }

    private static InputStream res(String path) {
        return ProxyConfig.class.getResourceAsStream("/" + path);
    }

    /** (Re)loads everything. Never throws: broken files fall back to the bundled defaults. */
    public Snapshot reload() {
        List<String> warnings = new ArrayList<>();
        Cfg config = Cfg.load(dir.resolve("config.yml"), () -> res("config.yml"), "config.yml", warnings);
        Cfg gui = Cfg.load(dir.resolve("gui.yml"), () -> res("gui.yml"), "gui.yml", warnings);
        Map<String, Cfg> msgs = new HashMap<>();
        for (String lang : LanguageCode.SUPPORTED) {
            String file = "messages_" + lang + ".yml";
            msgs.put(lang, Cfg.load(dir.resolve(file), () -> res(file), file, warnings));
        }
        Settings settings = Settings.from(config);       // may add more warnings
        Snapshot s = new Snapshot(config, gui, MessageCatalog.of(msgs), settings, warnings);
        this.snapshot = s;
        return s;
    }

    public Snapshot get() {
        return snapshot;
    }

    public Settings settings() {
        return snapshot.settings();
    }

    public MessageCatalog messages() {
        return snapshot.messages();
    }

    public Cfg raw() {
        return snapshot.config();
    }

    public Cfg gui() {
        return snapshot.gui();
    }

    public Path dir() {
        return dir;
    }
}
