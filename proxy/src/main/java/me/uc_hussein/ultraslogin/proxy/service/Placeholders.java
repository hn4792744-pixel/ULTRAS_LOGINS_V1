package me.uc_hussein.ultraslogin.proxy.service;

import net.kyori.adventure.text.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Values for %placeholders% in message templates. Values are components, so they are never parsed as markup. */
public final class Placeholders {
    private final Map<String, Component> values = new LinkedHashMap<>();

    public static Placeholders of() {
        return new Placeholders();
    }

    public Placeholders text(String key, String value) {
        values.put(key, Component.text(value == null ? "" : value));
        return this;
    }

    public Placeholders put(String key, Component value) {
        values.put(key, value);
        return this;
    }

    Map<String, Component> map() {
        return values;
    }
}
