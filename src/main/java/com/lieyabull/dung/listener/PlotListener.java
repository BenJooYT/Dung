package com.lieyabull.dung.listener;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.plot.PlotManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/** Enforces plot ownership: players may only modify the buildable area of their own plot. */
public final class PlotListener implements Listener {
    private final Dung plugin;

    public PlotListener(Dung plugin) {
        this.plugin = plugin;
    }

    /** True if the player may modify blocks at this location. Non-plot locations are always allowed. */
    private boolean canModify(Player p, Location loc) {
        PlotManager pm = plugin.plotManager();
        PlotManager.PlotCoord coord = pm.plotAt(loc);
        if (coord == null) return true; // not in the plots world
        return pm.ownsPlot(p, coord) && pm.isBuildableArea(loc);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!canModify(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cYou can only build on your own plot!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!canModify(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cYou can only build on your own plot!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;
        if (!canModify(p, e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!canModify(e.getPlayer(), e.getBlockClicked().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!canModify(e.getPlayer(), e.getBlockClicked().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        if (plugin.plotManager().plotAt(e.getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Block clicked = e.getClickedBlock();
        if (clicked == null || !(clicked.getState() instanceof Chest)) return;
        PlotManager pm = plugin.plotManager();
        Location loc = clicked.getLocation();
        PlotManager.PlotCoord coord = pm.plotAt(loc);
        if (coord == null) return;
        if (!pm.ownsPlot(e.getPlayer(), coord)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cThat chest belongs to someone else!");
        }
    }
}