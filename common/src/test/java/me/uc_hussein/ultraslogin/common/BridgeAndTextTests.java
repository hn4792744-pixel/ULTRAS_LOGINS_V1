package me.uc_hussein.ultraslogin.common;

import me.uc_hussein.ultraslogin.common.bridge.BridgeCodec;
import me.uc_hussein.ultraslogin.common.bridge.BridgeException;
import me.uc_hussein.ultraslogin.common.bridge.BridgeMessage;
import me.uc_hussein.ultraslogin.common.bridge.GuiItemModel;
import me.uc_hussein.ultraslogin.common.bridge.GuiModel;
import me.uc_hussein.ultraslogin.common.bridge.RestrictionFlags;
import me.uc_hussein.ultraslogin.common.config.Cfg;
import me.uc_hussein.ultraslogin.common.text.MessageCatalog;
import me.uc_hussein.ultraslogin.common.text.SmallCaps;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BridgeAndTextTests {
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes();
    private final UUID player = UUID.randomUUID();

    @Test
    void everyMessageRoundTrips() throws Exception {
        BridgeCodec c = new BridgeCodec(SECRET);
        GuiModel gui = new GuiModel(42L, "<red>UC | LANGUAGE", 3, false,
                List.of(new GuiItemModel(11, "BOOK", "<white>English", List.of("a", "b"), "lang:en", true)));
        List<BridgeMessage> all = List.of(new BridgeMessage.Hello(player, 1), new BridgeMessage.State(player, true, 77),
                new BridgeMessage.GuiOpen(player, gui), new BridgeMessage.GuiClose(player, 42L),
                new BridgeMessage.Teleport(player, "login", 0.5, 100, 0.5, 90f, -10f),
                new BridgeMessage.Sound(player, "ui.button.click", 0.3f, 1.1f), new BridgeMessage.GuiClick(player, 42L, "lang:ar"));
        for (BridgeMessage m : all) {
            assertEquals(m, new BridgeCodec(SECRET).decode(c.encode(m)));
        }
    }

    @Test
    void forgedTamperedAndWrongSecretMessagesAreRejected() {
        BridgeCodec c = new BridgeCodec(SECRET);
        byte[] ok = c.encode(new BridgeMessage.State(player, false, 0));
        byte[] tampered = ok.clone();
        tampered[20] ^= 0x01;
        assertThrows(BridgeException.class, () -> new BridgeCodec(SECRET).decode(tampered));
        assertThrows(BridgeException.class, () -> new BridgeCodec("a-completely-different-secret!!".getBytes()).decode(ok));
        assertThrows(BridgeException.class, () -> new BridgeCodec(SECRET).decode(new byte[5]));
        assertThrows(BridgeException.class, () -> new BridgeCodec(SECRET).decode("hello from a malicious client, long enough payload!!".getBytes()));
    }

    @Test
    void replayedAndStaleMessagesAreRejected() throws Exception {
        long[] now = {1_000_000L};
        BridgeCodec sender = new BridgeCodec(SECRET, () -> now[0], 60_000);
        BridgeCodec receiver = new BridgeCodec(SECRET, () -> now[0], 60_000);
        byte[] msg = sender.encode(new BridgeMessage.GuiClick(player, 1, "x"));
        assertNotNull(receiver.decode(msg));
        assertThrows(BridgeException.class, () -> receiver.decode(msg), "replay");
        byte[] old = sender.encode(new BridgeMessage.Hello(player, 1));
        now[0] += 120_000;
        assertThrows(BridgeException.class, () -> receiver.decode(old), "stale");
    }

    @Test
    void restrictionFlagsFollowTheConfig() {
        Cfg defaults = Cfg.ofMaps("t", Map.of(), Map.of(), new ArrayList<>());
        assertEquals(RestrictionFlags.safeDefault(), RestrictionFlags.fromConfig(defaults));
        assertFalse(RestrictionFlags.has(RestrictionFlags.safeDefault(), RestrictionFlags.CHAT));
        assertTrue(RestrictionFlags.has(RestrictionFlags.safeDefault(), RestrictionFlags.MOVEMENT));
        Cfg off = Cfg.ofMaps("t", Map.of("security", Map.of("restrict-before-login", false)), Map.of(), new ArrayList<>());
        assertEquals(0, RestrictionFlags.fromConfig(off));
    }

    @Test
    void smallCapsKeepsTagsPlaceholdersAndRawText() {
        String out = SmallCaps.convertTemplate("<prefix> <green>login %player% <raw>/login x</raw> done");
        assertTrue(out.startsWith("<prefix> <green>"));
        assertTrue(out.contains("%player%"));
        assertTrue(out.contains("<raw>/login x</raw>"));
        assertTrue(out.contains("\u029F\u1D0F\u0262\u026A\u0274"));          // ʟᴏɢɪɴ
        assertEquals("\u0627\u0644", SmallCaps.convert("\u0627\u0644"), "Arabic is untouched");
        assertEquals("\u1D1B\u1D07s\u1D1B", SmallCaps.convert("Test"));
    }

    @Test
    void cfgFallsBackToDefaultsAndWarnsOnWrongTypes() {
        List<String> warnings = new ArrayList<>();
        Cfg c = Cfg.ofMaps("config.yml", Map.of("sessions", Map.of("duration-minutes", "abc", "enabled", false)),
                Map.of("sessions", Map.of("duration-minutes", 5, "enabled", true)), warnings);
        assertEquals(5, c.number("sessions.duration-minutes", 1));
        assertFalse(c.bool("sessions.enabled", true));
        assertFalse(warnings.isEmpty());
        assertEquals("fallback", c.string("missing.key").isEmpty() ? "fallback" : "x");
    }

    @Test
    void messageCatalogFallsBackToEnglish() {
        List<String> w = new ArrayList<>();
        Cfg en = Cfg.ofMaps("en", Map.of("login-required", List.of("l1", "l2"), "only-en", "x", "meta", Map.of("small-caps", true, "name", "English")), Map.of(), w);
        Cfg ar = Cfg.ofMaps("ar", Map.of("login-required", List.of("a1"), "meta", Map.of("small-caps", false, "name", "Arabic")), Map.of(), w);
        MessageCatalog cat = MessageCatalog.of(Map.of("en", en, "ar", ar));
        assertEquals(List.of("a1"), cat.lines("ar", "login-required"));
        assertEquals(List.of("x"), cat.lines("ar", "only-en"));
        assertEquals(List.of("nope"), cat.lines("ar", "nope"));
        assertTrue(cat.smallCaps("en"));
        assertFalse(cat.smallCaps("ar"));
        assertEquals("English", cat.languageName("en"));
    }
}
