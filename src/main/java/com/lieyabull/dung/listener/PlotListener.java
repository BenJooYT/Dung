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
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;

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

    /** Prevent players from trampling crops on plots they don't own.
     *  EntityChangeBlockEvent fires when farmland turns to dirt from trampling. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        PlotManager pm = plugin.plotManager();
        Location loc = e.getBlock().getLocation();
        PlotManager.PlotCoord coord = pm.plotAt(loc);
        if (coord == null) return;
        if (!pm.ownsPlot(p, coord)) {
            e.setCancelled(true);
        }
    }

    /** True if the given location lies within the buildable area of the plot at {@code origin}
     *  (same plot, not on its border or the paths between plots). */
    private boolean withinPlot(PlotManager pm, PlotManager.PlotCoord origin, Location loc) {
        PlotManager.PlotCoord c = pm.plotAt(loc);
        return c != null && c.equals(origin) && pm.isBuildableArea(loc);
    }

    /** Saplings growing into trees can push trunk/leaves beyond the plot's buildable area (onto
     *  the border slabs or the paths/neighbouring plots). Prune every grown block that protrudes
     *  out of bounds so a tree can't extend past the player's manipulatable bounds. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent e) {
        PlotManager pm = plugin.plotManager();
        PlotManager.PlotCoord coord = pm.plotAt(e.getLocation());
        if (coord == null) return;
        e.getBlocks().removeIf(bs -> !withinPlot(pm, coord, bs.getLocation()));
    }

    /** Bone meal can force a sapling into a tree or spread tall growth (bamboo, etc.). Same pruning
     *  as {@link #onStructureGrow} — drop any fertilized block outside the origin plot's bounds. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent e) {
        PlotManager pm = plugin.plotManager();
        PlotManager.PlotCoord coord = pm.plotAt(e.getBlock().getLocation());
        if (coord == null) return;
        e.getBlocks().removeIf(bs -> !withinPlot(pm, coord, bs.getLocation()));
    }

    /** Pumpkins/melons grow into an adjacent block; sugar cane/bamboo grow upward. If the grown
     *  block would sit outside the origin plant's plot (border, path, or neighbour), cancel the
     *  growth so nothing protrudes past the manipulatable bounds. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent e) {
        PlotManager pm = plugin.plotManager();
        PlotManager.PlotCoord coord = pm.plotAt(e.getBlock().getLocation());
        if (coord == null) return;
        if (!withinPlot(pm, coord, e.getNewState().getLocation())) {
            e.setCancelled(true);
        }
    }
}