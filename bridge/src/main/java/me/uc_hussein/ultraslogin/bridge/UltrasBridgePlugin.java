package me.uc_hussein.ultraslogin.bridge;

import me.uc_hussein.ultraslogin.common.bridge.BridgeCodec;
import org.bukkit.plugin.java.JavaPlugin;

/** Backend half of Ultras Login: enforces restrictions, draws menus and teleports on behalf of the Velocity plugin. */
public final class UltrasBridgePlugin extends JavaPlugin {
    private final BridgeState state = new BridgeState();
    private BridgeLink link;
    private GuiManager guis;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        link = new BridgeLink(this);
        guis = new GuiManager(this);

        String secret = getConfig().getString("secret", "").trim();
        link.setSecret(secret);
        boolean ok = link.ready();
        state.configure(getConfig().getBoolean("restrict-until-synced", true), ok);
        if (!ok) {
            getLogger().severe("No valid 'secret' in config.yml (at least 16 characters). Copy the content of "
                    + "plugins/ultras_login/bridge-secret.key from the proxy. Until then every player here stays restricted.");
        }

        getServer().getMessenger().registerIncomingPluginChannel(this, BridgeCodec.CHANNEL, link);
        getServer().getMessenger().registerOutgoingPluginChannel(this, BridgeCodec.CHANNEL);
        getServer().getPluginManager().registerEvents(new RestrictionListener(this, state), this);
        getServer().getPluginManager().registerEvents(guis, this);
        getLogger().info("Ultras Login bridge enabled (protocol " + BridgeCodec.PROTOCOL + ").");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    public BridgeState state() {
        return state;
    }

    public BridgeLink link() {
        return link;
    }

    public GuiManager guis() {
        return guis;
    }
}
