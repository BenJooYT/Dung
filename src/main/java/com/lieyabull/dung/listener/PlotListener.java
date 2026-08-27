package com.lieyabull.dung.listener;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.plot.PlotManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Enforces plot ownership: players may only modify the buildable area of their own plot. */
public final class PlotListener implements Listener {
    private final Dung plugin;

    public PlotListener(Dung plugin) {
        this.plugin = plugin;
    }

    /** True if the player may modify blocks at this location. Non-plot locations are always allowed.
     *  On a plot: the owner, a trusted builder, or anyone if the plot is public may modify the
     *  buildable area. A player owning two neighbouring plots may also modify the path between them. */
    private boolean canModify(Player p, Location loc) {
        PlotManager pm = plugin.plotManager();
        PlotManager.PlotCoord coord = pm.plotAt(loc);
        if (coord == null) return true; // not in the plots world
        // Owning two neighbouring plots opens up the path between them.
        if (pm.canUseSharedPath(p, loc)) return true;
        if (!pm.isBuildableArea(loc)) return false;
        PlotManager.PlotInfo info = pm.getInfo(coord);
        if (info == null) return false;
        UUID uid = p.getUniqueId();
        return info.owner.equals(uid) || info.isPublic || info.buildTrust.contains(uid);
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
        if (clicked == null || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        PlotManager pm = plugin.plotManager();
        Location loc = clicked.getLocation();
        PlotManager.PlotCoord coord = pm.plotAt(loc);
        if (coord == null) return;

        // Right-clicking a fully grown crop breaks it like a normal harvest (dropping all its
        // natural drops on the ground) and immediately plants its seed back in its place.
        if (CROPS.contains(clicked.getType()) && canModify(e.getPlayer(), loc)) {
            harvest(e, clicked);
            return;
        }

        // Owner, public plot, or someone granted container access may open containers.
        // Covers ALL container blocks (chests, barrels, furnaces, hoppers, dispensers,
        // droppers, shulker boxes, brewing stands) — not just chests.
        if (!(clicked.getState() instanceof org.bukkit.block.Container)) return;
        PlotManager.PlotInfo info = pm.getInfo(coord);
        if (info == null) return;
        UUID uid = e.getPlayer().getUniqueId();
        if (info.owner.equals(uid) || info.isPublic || info.containerTrust.contains(uid)) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage("§cThat container belongs to someone else!");
    }

    /** Crops that can be harvested by right-click: wheat, carrots, potatoes, beetroot. */
    private static final Set<Material> CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS);

    /** Harvest a fully grown crop by breaking it exactly like a player would (natural drops fall
     *  on the ground), then immediately plant its seed back so the block becomes a fresh age-0 crop. */
    private void harvest(PlayerInteractEvent e, Block crop) {
        BlockData data = crop.getBlockData();
        if (!(data instanceof Ageable age) || age.getAge() != age.getMaximumAge()) return;

        Player p = e.getPlayer();
        p.swingMainHand();
        Material cropMaterial = crop.getType();
        crop.breakNaturally(e.getItem());
        // Plant the seed back: the block is now air, so place a fresh age-0 crop in its place.
        crop.setType(cropMaterial, false);
        Ageable replanted = (Ageable) crop.getBlockData();
        replanted.setAge(0);
        crop.setBlockData(replanted, false);
        e.setCancelled(true);
    }

    /** Picking up dropped items on a plot is restricted to the owner and any player the owner
     *  granted pickup access. Public plots allow anyone to pick up items, and public paths between
     *  different-owner plots allow pickup for everyone too. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent e) {
        PlotManager pm = plugin.plotManager();
        Location loc = e.getItem().getLocation();
        PlotManager.PlotCoord coord = pm.plotAt(loc);
        if (coord == null) return;
        if (pm.isPublicPath(loc)) return; // paths between different owners are open to all
        PlotManager.PlotInfo info = pm.getInfo(coord);
        if (info == null) return;
        UUID uid = e.getPlayer().getUniqueId();
        if (info.owner.equals(uid) || info.isPublic || info.pickupTrust.contains(uid)) return;
        e.setCancelled(true);
    }

    /** PVP is off by default on plots. A plot with PVP disabled protects anyone standing on it
     *  from player-vs-player damage. Public paths between different-owner plots are open to PvP
     *  for everyone by default. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!(e.getDamager() instanceof Player)) return;
        PlotManager pm = plugin.plotManager();
        Location victimLoc = victim.getLocation();
        PlotManager.PlotCoord coord = pm.plotAt(victimLoc);
        if (coord == null) return;
        if (pm.isPublicPath(victimLoc)) return; // PvP allowed on public paths
        PlotManager.PlotInfo info = pm.getInfo(coord);
        if (info != null && !info.pvp) {
            e.setCancelled(true);
            ((Player) e.getDamager()).sendMessage("§cPVP is disabled on this plot.");
        }
    }

    /** Fire may only burn blocks / spread on plots where fire spread is enabled. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent e) {
        PlotManager pm = plugin.plotManager();
        PlotManager.PlotCoord coord = pm.plotAt(e.getBlock().getLocation());
        if (coord == null) return;
        PlotManager.PlotInfo info = pm.getInfo(coord);
        if (info != null && !info.fireSpread) e.setCancelled(true);
    }

    /** Fire spreading into a plot with fire spread disabled is cancelled. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent e) {
        if (e.getNewState().getType() != org.bukkit.Material.FIRE) return;
        PlotManager pm = plugin.plotManager();
        PlotManager.PlotCoord coord = pm.plotAt(e.getBlock().getLocation());
        if (coord == null) return;
        PlotManager.PlotInfo info = pm.getInfo(coord);
        if (info != null && !info.fireSpread) e.setCancelled(true);
    }

    /** Prevent crops from being trampled in the plots world — by any entity, including the plot
     *  owner. EntityChangeBlockEvent fires when farmland turns to dirt from trampling. Like the
     *  naming suggests, a player never gains the right to trample their own crops. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        PlotManager pm = plugin.plotManager();
        Location loc = e.getBlock().getLocation();
        PlotManager.PlotCoord coord = pm.plotAt(loc);
        if (coord == null) return;
        // Trampling yields the farmland block changing to dirt; block any entity (mob or player,
        // owner or not) from stepping crops into dirt anywhere in the plots world.
        if (e.getBlock().getType() == org.bukkit.Material.FARMLAND && e.getTo() == org.bukkit.Material.DIRT) {
            e.setCancelled(true);
        }
    }

    /** True if a block at {@code loc} may be occupied by growth from the tree/plant rooted at the
     *  origin plot: inside the origin's buildable area, inside the buildable area of an edge-adjacent
     *  same-owner plot, or on a path shared between two same-owner adjacent plots. Adjacent plots
     *  owned by the same player are treated as one contiguous area for tree growth, so a canopy can
     *  grow across the shared boundary instead of being cut off at the path. */
    private boolean withinPlot(PlotManager pm, PlotManager.PlotCoord origin, Location loc) {
        PlotManager.PlotCoord c = pm.plotAt(loc);
        if (c == null) return false;
        if (pm.isBuildableArea(loc)) {
            return c.equals(origin) || pm.isSameOwnerNeighbor(origin, c);
        }
        return pm.isSharedPathBetween(origin, loc);
    }

    /** Saplings growing into trees can push trunk/leaves beyond the plot's buildable area (onto
     *  the border slabs or the paths/neighbouring plots). Prune every grown block that protrudes
     *  out of bounds so a tree can't extend past the player's manipulatable bounds. Also clears
     *  the player-placed provenance of the sapling spot (and any grown blocks) — the resulting
     *  tree is server-grown and must stay transmutable by potions without /convert. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent e) {
        PlotManager pm = plugin.plotManager();
        PlotManager.PlotCoord coord = pm.plotAt(e.getLocation());
        if (coord == null) return;
        e.getBlocks().removeIf(bs -> !withinPlot(pm, coord, bs.getLocation()));
        clearGrowthProvenance(e.getLocation().getBlock(), e.getBlocks());
    }

    /** Bone meal can force a sapling into a tree or spread tall growth (bamboo, etc.). Same pruning
     *  as {@link #onStructureGrow} — drop any fertilized block outside the origin plot's bounds. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent e) {
        PlotManager pm = plugin.plotManager();
        PlotManager.PlotCoord coord = pm.plotAt(e.getBlock().getLocation());
        if (coord == null) return;
        e.getBlocks().removeIf(bs -> !withinPlot(pm, coord, bs.getLocation()));
        clearGrowthProvenance(e.getBlock(), e.getBlocks());
    }

    /** Un-mark the growth origin (e.g. a placed sapling) and all grown blocks as player-placed,
     *  so grown wood stays eligible for transformation potions. */
    private void clearGrowthProvenance(org.bukkit.block.Block origin,
                                       java.util.List<org.bukkit.block.BlockState> grown) {
        var prov = plugin.provenanceManager();
        prov.unmark(origin);
        for (var bs : grown) prov.unmark(bs.getBlock());
        prov.save();
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

    // ==================== LEAF DECAY ACCELERATION ====================

    private static boolean isLog(Material m) {
        String n = m.name();
        return n.endsWith("_LOG") || n.equals("LOG") || n.equals("LOG_2");
    }

    private static boolean isLeaves(Material m) {
        String n = m.name();
        return n.endsWith("_LEAVES") || n.equals("LEAVES") || n.equals("LEAVES_2");
    }

    private boolean inPlotsWorld(Block b) {
        World w = b.getWorld();
        return w != null && w.getName().equals(PlotManager.PLOTS_WORLD_NAME);
    }

    /** True if the leaf is within 6 blocks (Manhattan) of a log, i.e. still attached to a tree.
     *  Otherwise it has become detached and would normally decay. */
    private boolean connectedToLog(Block leaf) {
        int bx = leaf.getX(), by = leaf.getY(), bz = leaf.getZ();
        World w = leaf.getWorld();
        for (int dx = -6; dx <= 6; dx++)
            for (int dy = -6; dy <= 6; dy++)
                for (int dz = -6; dz <= 6; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 6) continue;
                    if (isLog(w.getBlockAt(bx + dx, by + dy, bz + dz).getType())) return true;
                }
        return false;
    }

    /** When a tree is cut in the plots world, make its now-detached leaves fall quickly instead of
     *  waiting on the slow vanilla random decay. Persistent (builder-placed) leaves are left alone. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLogBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        if (!isLog(block.getType()) || !inPlotsWorld(block)) return;
        scheduleLeafDecay(block.getLocation());
    }

    /** Scan once for leaves near the cut log, then over a short burst repeatedly decay any that are
     *  no longer attached to a log so the canopy visibly falls instead of lingering. */
    private void scheduleLeafDecay(Location origin) {
        World w = origin.getWorld();
        if (w == null) return;
        List<Location> leaves = new ArrayList<>();
        int bx = origin.getBlockX(), by = origin.getBlockY(), bz = origin.getBlockZ();
        for (int dx = -7; dx <= 7; dx++)
            for (int dy = -7; dy <= 7; dy++)
                for (int dz = -7; dz <= 7; dz++) {
                    Block b = w.getBlockAt(bx + dx, by + dy, bz + dz);
                    if (isLeaves(b.getType())) leaves.add(b.getLocation());
                }
        if (leaves.isEmpty()) return;

        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int passes = 0;
            @Override
            public void run() {
                passes++;
                Iterator<Location> it = leaves.iterator();
                boolean any = false;
                while (it.hasNext()) {
                    Block b = it.next().getBlock();
                    if (!isLeaves(b.getType())) { it.remove(); continue; }
                    if (b.getBlockData() instanceof Leaves lv && lv.isPersistent()) { it.remove(); continue; }
                    if (!connectedToLog(b)) {
                        it.remove();
                        b.breakNaturally(); // drops saplings/apples as the leaves fall
                        any = true;
                    }
                }
                if (leaves.isEmpty() || !any || passes >= 12) {
                    Bukkit.getScheduler().cancelTask(holder[0].getTaskId());
                }
            }
        }, 2L, 3L);
    }
}