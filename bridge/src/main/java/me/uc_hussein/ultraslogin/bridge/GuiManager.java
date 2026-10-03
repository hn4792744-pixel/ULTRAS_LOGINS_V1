package me.uc_hussein.ultraslogin.bridge;

import me.uc_hussein.ultraslogin.common.bridge.GuiItemModel;
import me.uc_hussein.ultraslogin.common.bridge.GuiModel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Draws menus described by the proxy and reports clicks. All menu logic lives on the proxy. */
public final class GuiManager implements Listener {
    private static final GsonComponentSerializer JSON = GsonComponentSerializer.gson();
    private static final long CLICK_COOLDOWN_MS = 150;

    /** Holder that marks an inventory as one of ours. */
    static final class Holder implements InventoryHolder {
        final long id;
        final boolean closable;
        final Map<Integer, String> actions = new HashMap<>();
        volatile boolean closedByProxy;
        Inventory inventory;

        Holder(long id, boolean closable) {
            this.id = id;
            this.closable = closable;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private final UltrasBridgePlugin plugin;
    private final Map<UUID, GuiModel> current = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public GuiManager(UltrasBridgePlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p, GuiModel model) {
        Bukkit.getScheduler().runTask(plugin, () -> render(p, model));
    }

    private void render(Player p, GuiModel model) {
        if (!p.isOnline()) {
            return;
        }
        int rows = Math.max(1, Math.min(6, model.rows()));
        current.put(p.getUniqueId(), model);
        // Refresh in place when the same menu is already open (keeps the cursor, avoids flicker).
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder h && h.id == model.id() && h.inventory.getSize() == rows * 9) {
            fill(h, model);
            return;
        }
        Holder holder = new Holder(model.id(), model.closable());
        Component title = parse(model.title());
        holder.inventory = Bukkit.createInventory(holder, rows * 9, title);
        fill(holder, model);
        p.openInventory(holder.inventory);
    }

    private void fill(Holder h, GuiModel model) {
        h.inventory.clear();
        h.actions.clear();
        int size = h.inventory.getSize();
        for (GuiItemModel it : model.items()) {
            if (it.slot() < 0 || it.slot() >= size) {
                continue;
            }
            Material m = Material.matchMaterial(it.material());
            ItemStack stack = new ItemStack(m == null || !m.isItem() ? Material.STONE : m);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                if (it.name() != null) {
                    meta.displayName(parse(it.name()).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
                }
                List<Component> lore = new ArrayList<>();
                for (String l : it.lore()) {
                    lore.add(parse(l).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
                }
                meta.lore(lore);
                meta.addItemFlags(ItemFlag.values());
                if (it.glow()) {
                    meta.setEnchantmentGlintOverride(true);
                }
                stack.setItemMeta(meta);
            }
            h.inventory.setItem(it.slot(), stack);
            if (it.actionId() != null && !it.actionId().isEmpty()) {
                h.actions.put(it.slot(), it.actionId());
            }
        }
    }

    private static Component parse(String json) {
        try {
            return JSON.deserialize(json);
        } catch (RuntimeException e) {
            return Component.empty();
        }
    }

    public void closeById(Player p, long id) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            current.remove(p.getUniqueId());
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder h && h.id == id) {
                h.closedByProxy = true;
                p.closeInventory();
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true); // nothing can ever be taken, moved or shift-clicked in a menu
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }
        String action = h.actions.get(e.getSlot());
        if (action == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastClick.put(p.getUniqueId(), now);
        if (last != null && now - last < CLICK_COOLDOWN_MS) {
            return;
        }
        plugin.link().sendClick(p, h.id, action);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h) || !(e.getPlayer() instanceof Player p)) {
            return;
        }
        if (h.closable || h.closedByProxy || e.getReason() != InventoryCloseEvent.Reason.PLAYER) {
            return;
        }
        GuiModel model = current.get(p.getUniqueId());
        if (model != null && model.id() == h.id && p.isOnline()) {
            // A mandatory menu (language selection) cannot be dismissed: reopen it on the next tick.
            Bukkit.getScheduler().runTask(plugin, () -> {
                GuiModel again = current.get(p.getUniqueId());
                if (again != null && again.id() == h.id) {
                    render(p, again);
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        current.remove(e.getPlayer().getUniqueId());
        lastClick.remove(e.getPlayer().getUniqueId());
    }
}
