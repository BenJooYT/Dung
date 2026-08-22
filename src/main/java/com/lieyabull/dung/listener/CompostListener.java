package com.lieyabull.dung.listener;

import com.lieyabull.dung.Dung;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Wires the custom composter mechanic: dropping a compostable item while looking at a composter
 * feeds it (using items to fill it up, leaving the rest inside), and right-clicking a full
 * composter collects a bone meal and keeps it filling from what's left.
 */
public final class CompostListener implements Listener {
    private final Dung plugin;

    public CompostListener(Dung plugin) {
        this.plugin = plugin;
    }

    /** Look at a composter and drop (Q) a compostable item to feed it. The dropped amount is consumed
     *  from the player's inventory and goes into the composter's buffer. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        ItemStack dropped = e.getItemDrop().getItemStack();
        if (dropped == null || !plugin.compost().isCompostable(dropped)) return;
        Block composter = p.getTargetBlockExact(4);
        if (composter == null || composter.getType() != Material.COMPOSTER) return;
        e.setCancelled(true); // stop the physical drop (the drop already left the inventory)
        plugin.compost().addAndFill(composter, dropped);
        // Cancelling makes the server put the dropped stack back into the player's inventory, so
        // consume it for real — deferred a tick so that re-add has already happened.
        final ItemStack fed = dropped.clone();
        Bukkit.getScheduler().runTask(plugin, () -> p.getInventory().removeItemAnySlot(fed));
    }

    /** Right-click a full composter to collect its bone meal. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.COMPOSTER) return;
        if (plugin.compost().collectBoneMeal(e.getPlayer(), b)) {
            e.setCancelled(true);
        }
    }

    /** Destroying a composter returns its buffered material. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() == Material.COMPOSTER) {
            plugin.compost().removeState(e.getBlock());
        }
    }
}