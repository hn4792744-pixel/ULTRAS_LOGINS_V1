package me.uc_hussein.ultraslogin.bridge;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.uc_hussein.ultraslogin.common.bridge.RestrictionFlags;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Enforces the restrictions the proxy asked for. Handlers run at LOWEST so that other plugins cannot
 * un-cancel the cancellation of an unauthenticated player's action (ignoreCancelled is not used on purpose).
 */
public final class RestrictionListener implements Listener {
    private final UltrasBridgePlugin plugin;
    private final BridgeState state;

    public RestrictionListener(UltrasBridgePlugin plugin, BridgeState state) {
        this.plugin = plugin;
        this.state = state;
    }

    private boolean blocked(Entity e, int flag) {
        return e instanceof Player p && state.blocks(p.getUniqueId(), flag);
    }

    // ------------------------------------------------------------ join / quit (Hello handshake)

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // The proxy registers the channel on this connection a moment after the join; retry the Hello until it answers.
        long[] delays = {5L, 20L, 60L, 120L, 200L};
        for (long d : delays) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && !state.isSynced(p.getUniqueId())) {
                    plugin.link().sendHello(p);
                }
            }, d);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        state.forget(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------ movement

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        BridgeState.Entry en = state.of(p.getUniqueId());
        if (!en.restricted()) {
            return;
        }
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) {
            return;
        }
        boolean moved = from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
        if (!moved) {
            return; // head rotation is always allowed
        }
        if (RestrictionFlags.has(en.flags(), RestrictionFlags.MOVEMENT)) {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            e.setTo(back);
            return;
        }
        // Jumping is approximated: upward motion while standing on the ground is cancelled (documented limitation).
        if (RestrictionFlags.has(en.flags(), RestrictionFlags.JUMPING) && to.getY() > from.getY() && p.isOnGround()) {
            Location back = to.clone();
            back.setY(from.getY());
            e.setTo(back);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSprint(PlayerToggleSprintEvent e) {
        if (e.isSprinting() && state.blocks(e.getPlayer().getUniqueId(), RestrictionFlags.SPRINTING)) {
            e.setCancelled(true);
            e.getPlayer().setSprinting(false);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (state.consumeInternalTeleport(p.getUniqueId())) {
            return; // the teleport requested by the proxy itself
        }
        if (state.blocks(p.getUniqueId(), RestrictionFlags.TELEPORT)) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ world interaction

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (state.blocks(e.getPlayer().getUniqueId(), RestrictionFlags.INTERACTION)) {
            e.setCancelled(true);
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (state.blocks(e.getPlayer().getUniqueId(), RestrictionFlags.INTERACTION)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (state.blocks(e.getPlayer().getUniqueId(), RestrictionFlags.INTERACTION)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (state.blocks(e.getPlayer().getUniqueId(), RestrictionFlags.INTERACTION)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (state.blocks(e.getPlayer().getUniqueId(), RestrictionFlags.INTERACTION)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (state.blocks(e.getPlayer().getUniqueId(), RestrictionFlags.INTERACTION)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (state.blocks(e.getPlayer().getUniqueId(), RestrictionFlags.INVENTORY)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        if (state.blocks(e.getPlayer().getUniqueId(), RestrictionFlags.BLOCK_BREAK)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent e) {
        if (state.blocks(e.getPlayer().getUniqueId(), RestrictionFlags.BLOCK_PLACE)) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ damage / targeting

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (blocked(e.getEntity(), RestrictionFlags.DAMAGE)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageBy(EntityDamageByEntityEvent e) {
        if (blocked(e.getDamager(), RestrictionFlags.DAMAGE)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTarget(EntityTargetEvent e) {
        if (blocked(e.getTarget(), RestrictionFlags.DAMAGE)) {
            e.setCancelled(true); // mobs ignore players who have not logged in
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHunger(FoodLevelChangeEvent e) {
        if (blocked(e.getEntity(), RestrictionFlags.DAMAGE)) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ inventory / items

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof GuiManager.Holder) {
            return; // handled (and always cancelled) by GuiManager
        }
        if (blocked(e.getWhoClicked(), RestrictionFlags.INVENTORY)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (e.getInventory().getHolder() instanceof GuiManager.Holder) {
            return;
        }
        if (blocked(e.getPlayer(), RestrictionFlags.INVENTORY)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCraft(CraftItemEvent e) {
        if (blocked(e.getWhoClicked(), RestrictionFlags.INVENTORY)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (state.blocks(e.getPlayer().getUniqueId(), RestrictionFlags.ITEM_DROP)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent e) {
        if (blocked(e.getEntity(), RestrictionFlags.ITEM_PICKUP)) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ chat / commands

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        if (state.blocks(e.getPlayer().getUniqueId(), RestrictionFlags.CHAT)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        // /login, /register, /language are answered by the proxy and never reach this server.
        if (state.blocks(e.getPlayer().getUniqueId(), RestrictionFlags.COMMANDS)) {
            e.setCancelled(true);
        }
    }
}
