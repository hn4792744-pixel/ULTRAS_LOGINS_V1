package me.uc_hussein.ultraslogin.common.flow;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Result of asking Mojang whether a name belongs to a Premium account. */
public record MojangLookup(Status status, UUID uuid) {
    public enum Status { PREMIUM, NOT_PREMIUM, ERROR }

    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
    private static final Pattern JAVA_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

    public static boolean isValidJavaName(String name) {
        return name != null && JAVA_NAME.matcher(name).matches();
    }

    /** 200 + id = premium; 204/404 = no such premium name; anything else (429, 5xx, bad body) = error. */
    public static MojangLookup fromResponse(int httpStatus, String body) {
        if (httpStatus == 200 && body != null) {
            Matcher m = ID.matcher(body);
            if (m.find()) {
                String h = m.group(1);
                UUID u = UUID.fromString(h.substring(0, 8) + "-" + h.substring(8, 12) + "-" + h.substring(12, 16)
                        + "-" + h.substring(16, 20) + "-" + h.substring(20));
                return new MojangLookup(Status.PREMIUM, u);
            }
            return new MojangLookup(Status.ERROR, null);
        }
        if (httpStatus == 204 || httpStatus == 404) {
            return new MojangLookup(Status.NOT_PREMIUM, null);
        }
        return new MojangLookup(Status.ERROR, null);
    }
}
