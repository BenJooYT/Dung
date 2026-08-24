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

    /** Look at a composter and drop (Q) a compostable item to feed it. The fed amount is consumed
     *  from the player's currently held item (main hand) and goes into the composter's buffer. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        ItemStack dropped = e.getItemDrop().getItemStack();
        if (dropped == null || !plugin.compost().isCompostable(dropped)) return;
        Block composter = p.getTargetBlockExact(4);
        if (composter == null || composter.getType() != Material.COMPOSTER) return;
        // Consume from the held item instead of scanning the inventory: if the main hand can't
        // cover the dropped amount, neither cancel nor feed.
        ItemStack held = p.getInventory().getItemInMainHand();
        int amount = dropped.getAmount();
        if (held == null || held.getType() != dropped.getType() || held.getAmount() < amount) return;
        held.setAmount(held.getAmount() - amount);
        e.setCancelled(true); // stop the physical drop only after deducting from the hand
        plugin.compost().addAndFill(composter, dropped);
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