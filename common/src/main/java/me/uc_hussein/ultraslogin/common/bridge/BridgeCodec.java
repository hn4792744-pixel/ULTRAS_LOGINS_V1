package me.uc_hussein.ultraslogin.common.bridge;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Binary codec for bridge messages. Every message is authenticated with HMAC-SHA256 over a shared secret,
 * carries a timestamp and a random nonce (replay protection). A malicious client that manages to inject a
 * custom payload cannot forge a message without the secret.
 */
public final class BridgeCodec {
    public static final String CHANNEL_NAMESPACE = "ultraslogin";
    public static final String CHANNEL_NAME = "bridge";
    public static final String CHANNEL = CHANNEL_NAMESPACE + ":" + CHANNEL_NAME;
    public static final int PROTOCOL = 1;

    private static final int MAGIC = 0x55;
    private static final int MAC_LEN = 32;
    private static final int MAX_SIZE = 30_000;
    private static final int T_HELLO = 1, T_STATE = 2, T_GUI_OPEN = 3, T_GUI_CLOSE = 4, T_TELEPORT = 5, T_SOUND = 6, T_GUI_CLICK = 7;

    private final byte[] secret;
    private final LongSupplier clock;
    private final long maxSkewMillis;
    private final SecureRandom random = new SecureRandom();
    private final Map<Long, Long> seenNonces = new HashMap<>();

    public BridgeCodec(byte[] secret, LongSupplier clock, long maxSkewMillis) {
        if (secret == null || secret.length < 16) {
            throw new IllegalArgumentException("bridge secret must be at least 16 bytes");
        }
        this.secret = secret.clone();
        this.clock = clock;
        this.maxSkewMillis = maxSkewMillis;
    }

    public BridgeCodec(byte[] secret) {
        this(secret, System::currentTimeMillis, 60_000L);
    }

    public byte[] encode(BridgeMessage m) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(256);
            DataOutputStream out = new DataOutputStream(bos);
            out.writeByte(MAGIC);
            out.writeByte(typeOf(m));
            out.writeLong(clock.getAsLong());
            out.writeLong(random.nextLong());
            writeUuid(out, m.player());
            switch (m) {
                case BridgeMessage.Hello h -> out.writeInt(h.protocol());
                case BridgeMessage.State s -> {
                    out.writeBoolean(s.restricted());
                    out.writeInt(s.flags());
                }
                case BridgeMessage.GuiOpen g -> writeGui(out, g.gui());
                case BridgeMessage.GuiClose c -> out.writeLong(c.guiId());
                case BridgeMessage.Teleport t -> {
                    out.writeUTF(t.world());
                    out.writeDouble(t.x());
                    out.writeDouble(t.y());
                    out.writeDouble(t.z());
                    out.writeFloat(t.yaw());
                    out.writeFloat(t.pitch());
                }
                case BridgeMessage.Sound s -> {
                    out.writeUTF(s.key());
                    out.writeFloat(s.volume());
                    out.writeFloat(s.pitch());
                }
                case BridgeMessage.GuiClick c -> {
                    out.writeLong(c.guiId());
                    out.writeUTF(c.actionId());
                }
            }
            out.flush();
            byte[] body = bos.toByteArray();
            byte[] mac = mac(body, body.length);
            byte[] result = new byte[body.length + MAC_LEN];
            System.arraycopy(body, 0, result, 0, body.length);
            System.arraycopy(mac, 0, result, body.length, MAC_LEN);
            if (result.length > MAX_SIZE) {
                throw new IllegalArgumentException("bridge message too large");
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public BridgeMessage decode(byte[] data) throws BridgeException {
        if (data == null || data.length < 1 + 1 + 8 + 8 + 16 + MAC_LEN || data.length > MAX_SIZE) {
            throw new BridgeException("bad length");
        }
        int bodyLen = data.length - MAC_LEN;
        byte[] expected = mac(data, bodyLen);
        byte[] actual = new byte[MAC_LEN];
        System.arraycopy(data, bodyLen, actual, 0, MAC_LEN);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BridgeException("bad signature");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 0, bodyLen));
            if (in.readUnsignedByte() != MAGIC) {
                throw new BridgeException("bad magic");
            }
            int type = in.readUnsignedByte();
            long ts = in.readLong();
            long nonce = in.readLong();
            long now = clock.getAsLong();
            if (Math.abs(now - ts) > maxSkewMillis) {
                throw new BridgeException("stale message");
            }
            checkReplay(nonce, now);
            UUID player = readUuid(in);
            BridgeMessage msg = switch (type) {
                case T_HELLO -> new BridgeMessage.Hello(player, in.readInt());
                case T_STATE -> new BridgeMessage.State(player, in.readBoolean(), in.readInt());
                case T_GUI_OPEN -> new BridgeMessage.GuiOpen(player, readGui(in));
                case T_GUI_CLOSE -> new BridgeMessage.GuiClose(player, in.readLong());
                case T_TELEPORT -> new BridgeMessage.Teleport(player, in.readUTF(), in.readDouble(), in.readDouble(),
                        in.readDouble(), in.readFloat(), in.readFloat());
                case T_SOUND -> new BridgeMessage.Sound(player, in.readUTF(), in.readFloat(), in.readFloat());
                case T_GUI_CLICK -> new BridgeMessage.GuiClick(player, in.readLong(), in.readUTF());
                default -> throw new BridgeException("unknown type");
            };
            return msg;
        } catch (IOException e) {
            throw new BridgeException("malformed");
        }
    }

    private synchronized void checkReplay(long nonce, long now) throws BridgeException {
        seenNonces.values().removeIf(exp -> exp < now);
        if (seenNonces.putIfAbsent(nonce, now + maxSkewMillis * 2) != null) {
            throw new BridgeException("replayed message");
        }
    }

    private byte[] mac(byte[] data, int len) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update(data, 0, len);
            return mac.doFinal();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static int typeOf(BridgeMessage m) {
        return switch (m) {
            case BridgeMessage.Hello x -> T_HELLO;
            case BridgeMessage.State x -> T_STATE;
            case BridgeMessage.GuiOpen x -> T_GUI_OPEN;
            case BridgeMessage.GuiClose x -> T_GUI_CLOSE;
            case BridgeMessage.Teleport x -> T_TELEPORT;
            case BridgeMessage.Sound x -> T_SOUND;
            case BridgeMessage.GuiClick x -> T_GUI_CLICK;
        };
    }

    private static void writeUuid(DataOutputStream out, UUID u) throws IOException {
        out.writeLong(u.getMostSignificantBits());
        out.writeLong(u.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeGui(DataOutputStream out, GuiModel g) throws IOException {
        out.writeLong(g.id());
        out.writeUTF(g.title());
        out.writeByte(g.rows());
        out.writeBoolean(g.closable());
        out.writeShort(g.items().size());
        for (GuiItemModel it : g.items()) {
            out.writeByte(it.slot());
            out.writeUTF(it.material());
            out.writeUTF(it.name());
            out.writeByte(it.lore().size());
            for (String l : it.lore()) {
                out.writeUTF(l);
            }
            out.writeUTF(it.actionId() == null ? "" : it.actionId());
            out.writeBoolean(it.glow());
        }
    }

    private static GuiModel readGui(DataInputStream in) throws IOException, BridgeException {
        long id = in.readLong();
        String title = in.readUTF();
        int rows = in.readUnsignedByte();
        boolean closable = in.readBoolean();
        int n = in.readUnsignedShort();
        if (rows < 1 || rows > 6 || n > rows * 9) {
            throw new BridgeException("bad gui");
        }
        List<GuiItemModel> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int slot = in.readUnsignedByte();
            String material = in.readUTF();
            String name = in.readUTF();
            int loreN = in.readUnsignedByte();
            List<String> lore = new ArrayList<>(loreN);
            for (int j = 0; j < loreN; j++) {
                lore.add(in.readUTF());
            }
            String action = in.readUTF();
            boolean glow = in.readBoolean();
            if (slot >= rows * 9) {
                throw new BridgeException("bad slot");
            }
            items.add(new GuiItemModel(slot, material, name, lore, action, glow));
        }
        return new GuiModel(id, title, rows, closable, items);
    }
}
