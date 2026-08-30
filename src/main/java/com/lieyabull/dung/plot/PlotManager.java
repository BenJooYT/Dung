package com.lieyabull.dung.plot;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.meta.MetaManager;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages the Plots system: a separate flat world with 16x16 plots separated by
 * oak slab borders and 2-block-wide paths. Players can claim plots for 250 shards
 * or 150 persistent coins.
 */
public final class PlotManager {

    public static final String PLOTS_WORLD_NAME = "dung_plots";
    private static final int PLOT_SIZE = 16;          // 16x16 buildable area per plot
    private static final int PATH_WIDTH = 2;           // 2-block-wide paths between plots
    private static final int BORDER_WIDTH = 1;         // 1-block border (oak slab) per side
    private static final int CELL_SIZE = PLOT_SIZE + BORDER_WIDTH * 2 + PATH_WIDTH;
    // CELL_SIZE = 16 + 2 + 2 = 20

    // Surface Y level of the flat world (grass at y=51, dirt at y=31-50,
    // stone at y=1-30, bedrock at y=0).
    // Layers: grass_block(1) + dirt(20) + stone(30) + bedrock(1) = 52 blocks (y=0..51)
    private static final int SURFACE_Y = 51;
    /** The highest Y of the stone layer (inclusive). */
    private static final int STONE_TOP_Y = 30;
    /** The lowest Y of the stone layer (inclusive). */
    private static final int STONE_BOTTOM_Y = 1;

    // Plot origin: plots start at x=0, z=0. Plot (0,0) occupies x=[0..19], z=[0..19]
    // Buildable area: x=[0..17], z=[0..17] (includes the oak slab border)
    // Border: x=0, x=17, z=0, z=17 (oak slabs) — now breakable
    // Path: x=18..19, z=18..19 (between plots) — still protected

    public static final int CLAIM_SHARD_COST = 250;
    public static final int CLAIM_COIN_COST = 150;
    /** Each plot a player already owns multiplies the price of their next plot by this factor. */
    public static final double PRICE_MULTIPLIER = 1.25;

    /** World tick where daylight ends and night begins (sunset). */
    private static final long DAY_END_TICK = 13000L;
    /** Real seconds for a full 24h day/night cycle (20 min = vanilla total). */
    private static final int DAY_CYCLE_SECONDS = 1200;

    private final Dung plugin;
    private final File plotsFile;
    private final YamlConfiguration plotsData = new YamlConfiguration();
    private final Map<PlotCoord, PlotInfo> plots = new LinkedHashMap<>();
    private final Map<String, PlotCoord> nameToPlot = new LinkedHashMap<>();
    private long nextPlotId = 1;
    private World plotsWorld;
    private boolean daylightTaskStarted;

    public PlotManager(Dung plugin) {
        this.plugin = plugin;
        this.plotsFile = new File(plugin.getDataFolder(), "plots.yml");
        load();
    }

    // ==================== WORLD MANAGEMENT ====================

    /** Get or create the plots flat world. */
    public World getPlotsWorld() {
        if (plotsWorld != null) return plotsWorld;
        plotsWorld = Bukkit.getWorld(PLOTS_WORLD_NAME);
        if (plotsWorld == null) {
            WorldCreator wc = new WorldCreator(PLOTS_WORLD_NAME);
            wc.generator(new PlotChunkGenerator());
            wc.generateStructures(false);
            plotsWorld = wc.createWorld();
            if (plotsWorld != null) {
                plotsWorld.setGameRule(GameRules.SPAWN_MOBS, true);
                plotsWorld.setGameRule(GameRules.ADVANCE_TIME, false);
                plotsWorld.setGameRule(GameRules.ADVANCE_WEATHER, false);
                plotsWorld.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
                plotsWorld.setGameRule(GameRules.KEEP_INVENTORY, true);
                plotsWorld.setTime(6000); // noon
                // Set world spawn to the path cross-section between the four corner plots
                plotsWorld.setSpawnLocation(-1, SURFACE_Y + 1, -1);
                // Pre-generate borders and paths for the entire grid
                preGenerateGrid();
            }
        }
        if (plotsWorld != null) startDaylightCycle(plotsWorld);
        return plotsWorld;
    }

    /**
     * Start the custom daylight cycle for the plots world. A full 24h cycle takes
     * {@link #DAY_CYCLE_SECONDS} real seconds (the same 20-minute total as vanilla), but the
     * daylight portion is stretched so it lasts twice as long as the night.
     * <p>Minecraft's day runs for world-ticks {@code 0..13000} (daylight) and night for
     * {@code 13000..24000}. We advance the clock manually (ADVANCE_TIME is off) and use a larger
     * step at night so day occupies 2/3 of the cycle and night 1/3.
     */
    private void startDaylightCycle(World w) {
        if (daylightTaskStarted) return;
        daylightTaskStarted = true;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long t = w.getTime();
            long step = t < DAY_END_TICK
                    ? Math.round((double) DAY_END_TICK / (DAY_CYCLE_SECONDS * 2.0 / 3.0))
                    : Math.round((double) (24000L - DAY_END_TICK) / (DAY_CYCLE_SECONDS / 3.0));
            w.setTime((t + step) % 24000);
        }, 20L, 20L);
    }

    /** Wipe all plot data from memory and disk. */
    public void clearAll() {
        plots.clear();
        nameToPlot.clear();
        nextPlotId = 1;
        for (String key : plotsData.getKeys(false)) {
            plotsData.set(key, null);
        }
        try {
            plotsData.save(plotsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Teleport a player to the plots world spawn. */
    public void teleportToPlots(Player p) {
        World w = getPlotsWorld();
        if (w == null) {
            p.sendMessage("§cCould not load the plots world.");
            return;
        }
        // Spawn at the path cross-section between the four corner plots (0,0), (-1,0), (0,-1), (-1,-1)
        // The cross-section center is at x=-1.5, z=-1.5
        Location spawn = new Location(w, -1.5, SURFACE_Y + 1, -1.5);
        p.teleport(spawn);
        p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§aWelcome to the Plots! Use §f/plot claim§a to claim a plot."));
    }

    // ==================== PLOT COORDINATES ====================

    /** Represents a plot's grid position (column, row). */
    public record PlotCoord(int x, int z) {}

    /** Represents a claimed plot's owner and metadata. */
    public static final class PlotInfo {
        public UUID owner;
        public long claimedAt;
        public long id; // globally unique, monotonically increasing plot id (never reused)
        public String name; // nullable — set when player names their plot
        public boolean pvp;         // PVP allowed on this plot (default off)
        public boolean fireSpread;  // fire may spread/burn on this plot (default off)
        public boolean isPublic;    // anyone may build & open containers (default off)
        public boolean mobKill;     // anyone may kill mobs on this plot (default off — only owner)
        public final Set<UUID> buildTrust = new LinkedHashSet<>();      // may build
        public final Set<UUID> containerTrust = new LinkedHashSet<>();  // may open containers
        public final Set<UUID> pickupTrust = new LinkedHashSet<>();     // may pick up dropped items
        public final Set<UUID> mobKillTrust = new LinkedHashSet<>();    // may kill mobs even with mobKill off

        public PlotInfo(UUID owner, long claimedAt) {
            this.owner = owner;
            this.claimedAt = claimedAt;
            this.name = null;
        }

        public PlotInfo(UUID owner, long claimedAt, String name) {
            this(owner, claimedAt);
            this.name = name;
        }
    }

    /** Get the plot coordinate for a given world location. */
    public PlotCoord plotAt(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(plotsWorld)) return null;
        int gx = loc.getBlockX();
        int gz = loc.getBlockZ();
        // Normalize negative coordinates
        if (gx < 0) gx -= CELL_SIZE - 1;
        if (gz < 0) gz -= CELL_SIZE - 1;
        int px = gx / CELL_SIZE;
        int pz = gz / CELL_SIZE;
        return new PlotCoord(px, pz);
    }

    /** Get the origin (minimum corner) of a plot in world coordinates. */
    public Location plotOrigin(PlotCoord coord) {
        World w = getPlotsWorld();
        if (w == null) return null;
        int ox = coord.x() * CELL_SIZE;
        int oz = coord.z() * CELL_SIZE;
        return new Location(w, ox, SURFACE_Y, oz);
    }

    /** Check if a location is within the buildable area of its plot (includes border, excludes path). */
    public boolean isBuildableArea(Location loc) {
        PlotCoord coord = plotAt(loc);
        if (coord == null) return false;
        Location origin = plotOrigin(coord);
        if (origin == null) return false;
        int dx = loc.getBlockX() - origin.getBlockX();
        int dz = loc.getBlockZ() - origin.getBlockZ();
        // Buildable area: x=[0..17], z=[0..17] (includes the oak slab border, excludes the path at 18-19)
        return dx >= 0 && dx <= PLOT_SIZE + 1 && dz >= 0 && dz <= PLOT_SIZE + 1;
    }

    /** True if the location lies in a 2-wide path band between plots (i.e. NOT buildable area). */
    public boolean isPathLocation(Location loc) {
        PlotCoord coord = plotAt(loc);
        if (coord == null) return false;
        Location origin = plotOrigin(coord);
        if (origin == null) return false;
        int dx = loc.getBlockX() - origin.getBlockX();
        int dz = loc.getBlockZ() - origin.getBlockZ();
        return (dx >= PLOT_SIZE + 2 && dx <= PLOT_SIZE + 3) || (dx >= -2 && dx <= -1)
                || (dz >= PLOT_SIZE + 2 && dz <= PLOT_SIZE + 3) || (dz >= -2 && dz <= -1);
    }

    /** The two plots on either side of a path location, IF both are owned by the given player
     *  (i.e. it's their shared path). Empty list otherwise. */
    public List<PlotCoord> sharedPathPlots(Player p, Location loc) {
        List<PlotCoord> out = new ArrayList<>();
        if (!isPathLocation(loc)) return out;
        PlotCoord coord = plotAt(loc);
        PlotCoord neighbor = neighborAcrossPath(loc);
        if (coord == null || neighbor == null) return out;
        PlotInfo a = plots.get(coord);
        PlotInfo b = plots.get(neighbor);
        UUID uid = p.getUniqueId();
        if (a != null && b != null && uid.equals(a.owner) && uid.equals(b.owner)) {
            out.add(coord);
            out.add(neighbor);
        }
        return out;
    }

    /** The plot on the far side of the path band that {@code loc} sits on, or null if the location
     *  is not on a path. */
    private PlotCoord neighborAcrossPath(Location loc) {
        PlotCoord coord = plotAt(loc);
        if (coord == null) return null;
        Location origin = plotOrigin(coord);
        if (origin == null) return null;
        int dx = loc.getBlockX() - origin.getBlockX();
        int dz = loc.getBlockZ() - origin.getBlockZ();
        if (dx >= PLOT_SIZE + 2 && dx <= PLOT_SIZE + 3) return new PlotCoord(coord.x() + 1, coord.z());
        if (dx >= -2 && dx <= -1) return new PlotCoord(coord.x() - 1, coord.z());
        if (dz >= PLOT_SIZE + 2 && dz <= PLOT_SIZE + 3) return new PlotCoord(coord.x(), coord.z() + 1);
        if (dz >= -2 && dz <= -1) return new PlotCoord(coord.x(), coord.z() - 1);
        return null;
    }

    /** True if {@code loc} lies on a path shared between two adjacent plots owned by the same
     *  player, where {@code origin} is one of the two plots. */
    public boolean isSharedPathBetween(PlotCoord origin, Location loc) {
        if (!isPathLocation(loc)) return false;
        PlotInfo info = plots.get(origin);
        if (info == null) return false;
        UUID owner = info.owner;
        PlotCoord self = plotAt(loc);
        PlotCoord neighbor = neighborAcrossPath(loc);
        if (self == null || neighbor == null) return false;
        if (!origin.equals(self) && !origin.equals(neighbor)) return false;
        PlotInfo a = plots.get(self);
        PlotInfo b = plots.get(neighbor);
        return a != null && b != null && owner.equals(a.owner) && owner.equals(b.owner);
    }

    /** True if {@code a} and {@code b} are edge-adjacent plots owned by the same player. */
    public boolean isSameOwnerNeighbor(PlotCoord a, PlotCoord b) {
        if (Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()) != 1) return false;
        PlotInfo ai = plots.get(a);
        PlotInfo bi = plots.get(b);
        return ai != null && bi != null && ai.owner.equals(bi.owner);
    }

    /** True if the location is on the path between two plots BOTH owned by the given player.
     *  Owning two neighbouring plots grants the owner build/access rights to the path between them. */
    public boolean canUseSharedPath(Player p, Location loc) {
        return !sharedPathPlots(p, loc).isEmpty();
    }

    /** True if {@code loc} lies on a path separating two plots that do NOT share the same owner
     *  (a plot beside an unclaimed cell counts too, since it has no owner to protect). These are
     *  public thoroughfares: item pickup and PvP are allowed there for everyone by default. */
    public boolean isPublicPath(Location loc) {
        if (!isPathLocation(loc)) return false;
        PlotCoord self = plotAt(loc);
        PlotCoord neighbor = neighborAcrossPath(loc);
        if (self == null || neighbor == null) return false;
        PlotInfo a = plots.get(self);
        PlotInfo b = plots.get(neighbor);
        if (a == null || b == null) return true;
        return !a.owner.equals(b.owner);
    }

    /** True if both plots are claimed and share the same owner. */
    private boolean sharedPathBetween(PlotCoord a, PlotCoord b) {
        PlotInfo ai = plots.get(a);
        PlotInfo bi = plots.get(b);
        return ai != null && bi != null && ai.owner.equals(bi.owner);
    }

    /**
     * If the player is standing on an unclaimed plot in the plots world, return that plot's
     * coordinate. Otherwise return null.
     */
    private PlotCoord standingOnUnclaimedPlot(Player p) {
        Location loc = p.getLocation();
        if (loc.getWorld() == null || !loc.getWorld().equals(plotsWorld)) return null;
        PlotCoord coord = plotAt(loc);
        if (coord == null) return null;
        // Return the plot only if it's not already claimed
        if (plots.containsKey(coord)) return null;
        return coord;
    }

    // ==================== CLAIMING ====================

    /** Show the player their balances and clickable options to claim a plot with shards or coins.
     *  If the player is standing on an unclaimed plot in the plots world, that plot will be
     *  claimed. Otherwise falls back to the spiral-assignment logic. */
    public void showClaimOptions(Player p) {
        // Require the player to be standing on an unclaimed plot in the plots world.
        PlotCoord standingOn = standingOnUnclaimedPlot(p);
        if (standingOn == null) {
            p.sendMessage("§cStand on an unclaimed plot in the plots world to claim it.");
            return;
        }
        p.sendMessage("");
        p.sendMessage("§6§lClaim Plot at §f(" + standingOn.x() + ", " + standingOn.z() + ")");

        int owned = ownedPlotCount(p.getUniqueId());
        int shardCost = claimShardCost(p.getUniqueId());
        int coinCost = claimCoinCost(p.getUniqueId());
        MetaManager.MetaProfile profile = plugin.meta().profile(p.getUniqueId());
        p.sendMessage("§7  Your balance: §e" + profile.shards + " shards§7, §6" + profile.persistentCoins + " coins");
        p.sendMessage("");
        if (owned == 0) {
            p.sendMessage("§7  Price: §e" + CLAIM_SHARD_COST + " shards§7 / §6" + CLAIM_COIN_COST + " coins§7 (×" + PRICE_MULTIPLIER + " per plot you own).");
        } else {
            p.sendMessage("§7  You own §e" + owned + " plot(s)§7. This plot costs §e" + shardCost + " shards§7 / §6" + coinCost + " coins§7.");
        }

        boolean canShards = profile.shards >= shardCost;
        boolean canCoins = profile.persistentCoins >= coinCost;

        if (!canShards && !canCoins) {
            p.sendMessage("§cYou need §e" + shardCost + " shards§c or §6" + coinCost + " coins§c to claim a plot.");
            return;
        }

        // Clickable shard option
        net.kyori.adventure.text.Component shardOpt = net.kyori.adventure.text.Component.text("[ ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(net.kyori.adventure.text.Component.text("Buy with " + shardCost + " Shards",
                        canShards ? net.kyori.adventure.text.format.NamedTextColor.YELLOW : net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY,
                        net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(net.kyori.adventure.text.Component.text(" ]", net.kyori.adventure.text.format.NamedTextColor.GRAY));
        if (canShards) {
            shardOpt = shardOpt.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                    net.kyori.adventure.text.Component.text("Click to claim with " + shardCost + " shards", net.kyori.adventure.text.format.NamedTextColor.GRAY)))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/plot claim shards"));
        }
        p.sendMessage(shardOpt);

        // Clickable coin option
        net.kyori.adventure.text.Component coinOpt = net.kyori.adventure.text.Component.text("[ ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(net.kyori.adventure.text.Component.text("Buy with " + coinCost + " Coins",
                        canCoins ? net.kyori.adventure.text.format.NamedTextColor.GOLD : net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY,
                        net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(net.kyori.adventure.text.Component.text(" ]", net.kyori.adventure.text.format.NamedTextColor.GRAY));
        if (canCoins) {
            coinOpt = coinOpt.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                    net.kyori.adventure.text.Component.text("Click to claim with " + coinCost + " coins", net.kyori.adventure.text.format.NamedTextColor.GRAY)))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/plot claim coins"));
        }
        p.sendMessage(coinOpt);
        p.sendMessage("");
    }

    /** Claim a plot for a player using the specified payment method. Returns a message (null = success).
     *  If the player is standing on an unclaimed plot in the plots world, that plot is claimed.
     *  Otherwise falls back to the spiral-assignment logic. */
    public String claimPlot(Player p, String paymentMethod) {
        MetaManager.MetaProfile profile = plugin.meta().profile(p.getUniqueId());
        int shardCost = claimShardCost(p.getUniqueId());
        int coinCost = claimCoinCost(p.getUniqueId());

        boolean useShards;
        if (paymentMethod.equalsIgnoreCase("shards")) {
            if (profile.shards < shardCost) {
                return "§cYou need §e" + shardCost + " shards§c, but you only have §e" + profile.shards + "§c.";
            }
            useShards = true;
        } else if (paymentMethod.equalsIgnoreCase("coins")) {
            if (profile.persistentCoins < coinCost) {
                return "§cYou need §6" + coinCost + " coins§c, but you only have §6" + profile.persistentCoins + "§c.";
            }
            useShards = false;
        } else {
            return "§cInvalid payment method. Use §f/plot claim shards§c or §f/plot claim coins§c.";
        }

        // Require the player to be standing on an unclaimed plot in the plots world.
        PlotCoord coord = standingOnUnclaimedPlot(p);
        if (coord == null) {
            return "§cStand on an unclaimed plot in the plots world to claim it.";
        }

        // Charge the player
        if (useShards) {
            profile.shards -= shardCost;
        } else {
            profile.persistentCoins -= coinCost;
        }

        // Build the plot (border, path, starter chest) BEFORE marking it claimed so the
        // claimed-plot regen guard in buildPlotBordersAndPaths doesn't skip this initial build,
        // and a crash mid-claim can't leave a plot owned but never built.
        PlotInfo info = new PlotInfo(p.getUniqueId(), System.currentTimeMillis());
        info.id = nextPlotId++;
        buildPlot(coord);
        plots.put(coord, info);
        save();
        // Persist the shard/coin charge immediately so it can't roll back on a crash.
        plugin.meta().save();

        // Teleport player to their new plot
        p.teleport(homeLocation(coord));

        String costStr = useShards ? "§e" + shardCost + " shards" : "§6" + coinCost + " coins";
        p.sendMessage("§aPlot claimed for " + costStr + "!");
        p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§aName your plot with §f/plot name <name>§a, or use §f/plot home§a to return later."));
        return null; // success
    }

    /** Remove the player's claim on the plot they are currently standing on and free it for
     *  re-claiming. Returns a message (null = success). */
    public String unclaimPlot(Player p) {
        Location loc = p.getLocation();
        if (loc.getWorld() == null || !loc.getWorld().equals(plotsWorld)) {
            return "§cStand on the plot you want to unclaim.";
        }
        PlotCoord coord = plotAt(loc);
        if (coord == null) {
            return "§cStand on the plot you want to unclaim.";
        }
        PlotInfo info = plots.get(coord);
        if (info == null || !info.owner.equals(p.getUniqueId())) {
            return "§cYou don't own this plot.";
        }
        plots.remove(coord);
        if (info.name != null) {
            nameToPlot.remove(info.name.toLowerCase());
        }
        // Remove the starter chest so the plot is clean for the next owner
        World w = getPlotsWorld();
        if (w != null) {
            Location origin = plotOrigin(coord);
            if (origin != null) {
                int cx = origin.getBlockX() + PLOT_SIZE / 2 + 1;
                int cz = origin.getBlockZ() + PLOT_SIZE / 2 + 1;
                Block b = w.getBlockAt(cx, origin.getBlockY() + 1, cz);
                if (b.getType() == Material.CHEST) {
                    b.setType(Material.AIR, false);
                }
            }
        }
        save();
        return null; // success
    }

    /** Teleport a player to one of their claimed plots (the first one they own). */
    public boolean teleportToPlot(Player p) {
        List<PlotCoord> owned = ownedPlots(p.getUniqueId());
        if (owned.isEmpty()) return false;
        return teleportToCoord(p, owned.get(0));
    }

    /** Teleport a player to a specific plot coordinate. */
    private boolean teleportToCoord(Player p, PlotCoord coord) {
        Location home = homeLocation(coord);
        if (home == null) return false;
        p.teleport(home);
        return true;
    }

    /** The home point of a plot (center of its buildable area), where a player lands on claim,
     *  teleport, or respawn. */
    public Location homeLocation(PlotCoord coord) {
        Location origin = plotOrigin(coord);
        if (origin == null) return null;
        return new Location(origin.getWorld(), origin.getBlockX() + 8.5, SURFACE_Y + 1, origin.getBlockZ() + 8.5);
    }

    /** The home of the owner's nearest owned plot to {@code from}, or null if the owner has no
     *  plots. Used to respawn players who die in the plots world at their own plot. */
    public Location nearestOwnedPlotHome(UUID owner, Location from) {
        PlotCoord nearest = null;
        double best = Double.MAX_VALUE;
        for (PlotCoord c : ownedPlots(owner)) {
            Location origin = plotOrigin(c);
            if (origin == null) continue;
            double d = origin.distanceSquared(from);
            if (d < best) {
                best = d;
                nearest = c;
            }
        }
        return nearest == null ? null : homeLocation(nearest);
    }

    /**
     * Warp a player to one of their named plots.
     * @return null on success, or an error message string.
     */
    public String warpToNamedPlot(Player p, String name) {
        PlotCoord coord = nameToPlot.get(name.toLowerCase());
        if (coord == null) {
            return "§cNo plot named §f" + name + "§c found.";
        }
        PlotInfo info = plots.get(coord);
        if (info == null || !info.owner.equals(p.getUniqueId())) {
            return "§cYou don't own a plot named §f" + name + "§c.";
        }
        teleportToCoord(p, coord);
        return null;
    }

    /**
     * Set the name of a player's plot.
     * @return null on success, or an error message string.
     */
    public String setNamePlot(Player p, String name) {
        if (name.isEmpty()) {
            return "§cPlot name cannot be empty.";
        }
        PlotCoord coord = standingOwnedPlot(p);
        if (coord == null) {
            return "§cStand on a plot you own to name it.";
        }
        PlotInfo info = plots.get(coord);
        if (info == null) {
            return "§cPlot data not found.";
        }
        String key = name.toLowerCase();
        // Enforce globally unique names (across ALL plots, not just this player's)
        PlotCoord existing = nameToPlot.get(key);
        if (existing != null && !existing.equals(coord)) {
            return "§cThat plot name is already taken by another player.";
        }
        // Remove old name mapping if renaming
        if (info.name != null) {
            nameToPlot.remove(info.name.toLowerCase());
        }
        info.name = name;
        nameToPlot.put(key, coord);
        save();
        return null;
    }

    /** Get a list of plot names owned by a player (for tab completion). */
    public List<String> getPlayerPlotNames(Player p) {
        return plots.values().stream()
                .filter(i -> i.name != null && i.owner.equals(p.getUniqueId()))
                .map(i -> i.name)
                .collect(Collectors.toList());
    }

    /** Check if a player owns a specific plot. */
    public boolean ownsPlot(Player p, PlotCoord coord) {
        PlotInfo info = plots.get(coord);
        return info != null && info.owner.equals(p.getUniqueId());
    }

    /** Check if a plot is owned by the given player UUID. */
    public boolean ownsPlotByOwner(PlotCoord coord, UUID owner) {
        PlotInfo info = plots.get(coord);
        return info != null && owner != null && info.owner.equals(owner);
    }

    /** Check if a player owns any plot. */
    public boolean hasPlot(Player p) {
        return !ownedPlots(p.getUniqueId()).isEmpty();
    }

    /** All plot coordinates owned by a player (in claim order). */
    public List<PlotCoord> ownedPlots(UUID owner) {
        List<PlotCoord> out = new ArrayList<>();
        for (Map.Entry<PlotCoord, PlotInfo> e : plots.entrySet()) {
            if (owner.equals(e.getValue().owner)) out.add(e.getKey());
        }
        return out;
    }

    /** Number of plots a player currently owns. */
    public int ownedPlotCount(UUID owner) {
        return ownedPlots(owner).size();
    }

    /** Shard cost to claim a plot for the given owner (rises 1.25x per plot they already own). */
    public int claimShardCost(UUID owner) {
        return (int) Math.ceil(CLAIM_SHARD_COST * Math.pow(PRICE_MULTIPLIER, ownedPlotCount(owner)));
    }

    /** Coin cost to claim a plot for the given owner (rises 1.25x per plot they already own). */
    public int claimCoinCost(UUID owner) {
        return (int) Math.ceil(CLAIM_COIN_COST * Math.pow(PRICE_MULTIPLIER, ownedPlotCount(owner)));
    }

    /** The plot the player is standing on if they own it, else null. */
    private PlotCoord standingOwnedPlot(Player p) {
        if (p.getLocation().getWorld() == null || !p.getLocation().getWorld().equals(plotsWorld)) return null;
        PlotCoord coord = plotAt(p.getLocation());
        if (coord == null) return null;
        PlotInfo info = plots.get(coord);
        if (info == null || !info.owner.equals(p.getUniqueId())) return null;
        return coord;
    }

    /** Get the PlotInfo for a coordinate, or null if unclaimed. */
    public PlotInfo getInfo(PlotCoord coord) {
        return plots.get(coord);
    }

    // ==================== PLOT SETTINGS ====================

    private static final String ON = "§aON";
    private static final String OFF = "§cOFF";

    /** Resolve a player name to their UUID (offline-safe). */
    private static UUID resolveUUID(String name) {
        return Bukkit.getOfflinePlayer(name).getUniqueId();
    }

    /** Format a set of UUIDs as a comma-separated name list, falling back to a short UUID. */
    private static String namesOf(Set<UUID> uuids) {
        if (uuids.isEmpty()) return "§7none";
        return uuids.stream()
                .map(u -> {
                    String n = Bukkit.getOfflinePlayer(u).getName();
                    return n != null ? n : u.toString().substring(0, 8);
                })
                .collect(Collectors.joining(", "));
    }

    /** Show the settings for the plot(s) the player is standing on. When on a shared path between
     *  two own plots, both are shown. Returns error msg or null. */
    public String showPlotSettings(Player p) {
        List<PlotCoord> coords = ownedPlotsForConfig(p);
        if (coords.isEmpty()) return "§cStand on a plot you own to view its settings.";
        for (PlotCoord c : coords) {
            PlotInfo info = plots.get(c);
            p.sendMessage("§6§lPlot " + plotLabel(c) + " settings");
            p.sendMessage("  §7PVP: " + (info.pvp ? ON : OFF));
            p.sendMessage("  §7Fire spread: " + (info.fireSpread ? ON : OFF));
            p.sendMessage("  §7Public: " + (info.isPublic ? ON : OFF));
            p.sendMessage("  §7Mob kill: " + (info.mobKill ? ON : OFF));
            p.sendMessage("  §7Build access: " + namesOf(info.buildTrust));
            p.sendMessage("  §7Container access: " + namesOf(info.containerTrust));
            p.sendMessage("  §7Item pickup access: " + namesOf(info.pickupTrust));
            p.sendMessage("  §7Mob kill access: " + namesOf(info.mobKillTrust));
        }
        p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§7Set these with §f/plot pvp|fire|public|mobkill on|off§7, manage access with §f/plot perm <build|container|pickup|mobkill> <player> [on|off]§7."));
        return null;
    }

    /** Show the current permissions (access lists + public flag) for the plot(s) the player is
     *  standing on. On a shared path between two own plots, both are shown. Returns error msg or null. */
    public String showPlotPerms(Player p) {
        List<PlotCoord> coords = ownedPlotsForConfig(p);
        if (coords.isEmpty()) return "§cStand on a plot you own to view its permissions.";
        for (PlotCoord c : coords) {
            PlotInfo info = plots.get(c);
            p.sendMessage("§6§lPlot " + plotLabel(c) + " permissions");
            p.sendMessage("  §7Public: " + (info.isPublic ? ON : OFF));
            p.sendMessage("  §7Build access: " + namesOf(info.buildTrust));
            p.sendMessage("  §7Container access: " + namesOf(info.containerTrust));
            p.sendMessage("  §7Item pickup access: " + namesOf(info.pickupTrust));
            p.sendMessage("  §7Mob kill access: " + namesOf(info.mobKillTrust));
        }
        p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands(
                "§7Manage with §f/plot perm <build|container|pickup|mobkill> <player> [on|off]§7."));
        return null;
    }

    /** Toggle a boolean setting on the plot(s) the player is standing on. If standing on a shared
     *  path between two own plots, the change applies to BOTH and the player is told so. */
    public String setPlotToggle(Player p, String setting, boolean value) {
        List<PlotCoord> coords = ownedPlotsForConfig(p);
        if (coords.isEmpty()) return "§cStand on a plot you own to change its settings.";
        String label;
        switch (setting) {
            case "pvp" -> label = "PVP";
            case "fire" -> label = "Fire spread";
            case "public" -> label = "Public";
            case "mobkill" -> label = "Mob kill";
            default -> { return "§cUnknown setting §f" + setting; }
        }
        for (PlotCoord c : coords) {
            PlotInfo info = plots.get(c);
            if (setting.equals("pvp")) info.pvp = value;
            else if (setting.equals("fire")) info.fireSpread = value;
            else if (setting.equals("mobkill")) info.mobKill = value;
            else info.isPublic = value;
        }
        save();
        p.sendMessage("§a" + label + " set to " + (value ? "ON" : "OFF") + " on "
                + affectedMessage(coords) + ".");
        return null;
    }

    /** Add or remove a player to/from build, container, or item-pickup access on the plot(s) the
     *  player is standing on. On a shared path this applies to both own plots. */
    public String setPlotTrust(Player p, String kind, boolean add, String targetName) {
        List<PlotCoord> coords = ownedPlotsForConfig(p);
        if (coords.isEmpty()) return "§cStand on a plot you own to manage access.";
        UUID id = resolveUUID(targetName);
        String k = kind.toLowerCase();
        String access;
        if (k.contains("container")) access = "container";
        else if (k.contains("pickup")) access = "pickup";
        else if (k.contains("mobkill") || k.contains("mob_kill")) access = "mobkill";
        else access = "build";
        for (PlotCoord c : coords) {
            PlotInfo info = plots.get(c);
            Set<UUID> set = switch (access) {
                case "container" -> info.containerTrust;
                case "pickup" -> info.pickupTrust;
                case "mobkill" -> info.mobKillTrust;
                default -> info.buildTrust;
            };
            if (add) set.add(id); else set.remove(id);
        }
        save();
        String verb = add ? "granted §f" + targetName + "§a " + access + " access"
                          : "revoked §f" + targetName + "§a from " + access + " access";
        p.sendMessage("§a" + verb + " on " + affectedMessage(coords) + ".");
        return null;
    }

    /** "plot §fName§a" (one) or "2 plots: §fA§7, §fB§a" (multiple). */
    private String affectedMessage(List<PlotCoord> coords) {
        String names = coords.stream().map(this::plotLabel).collect(Collectors.joining("§7, §f"));
        return coords.size() == 1
                ? "plot §f" + names + "§a"
                : coords.size() + " plots: §f" + names + "§a";
    }

    /** The owned plot(s) a config command should affect for this player: their standing plot, or
     *  both plots when standing on a shared path between two of their own. Empty if none. */
    private List<PlotCoord> ownedPlotsForConfig(Player p) {
        List<PlotCoord> shared = sharedPathPlots(p, p.getLocation());
        if (!shared.isEmpty()) return shared;
        if (p.getLocation().getWorld() == null || !p.getLocation().getWorld().equals(plotsWorld)) return List.of();
        PlotCoord coord = plotAt(p.getLocation());
        if (coord == null) return List.of();
        PlotInfo info = plots.get(coord);
        if (info == null || !info.owner.equals(p.getUniqueId())) return List.of();
        return List.of(coord);
    }

    /** Display label for a plot: its name if set, else its plot id. */
    private String plotLabel(PlotCoord coord) {
        PlotInfo info = plots.get(coord);
        if (info != null && info.name != null) return info.name;
        if (info != null) return "#" + info.id;
        return "(" + coord.x() + ", " + coord.z() + ")";
    }

    // ==================== PLOT GENERATION ====================

    /** Pre-generate borders and paths for a small initial grid of plots around (0,0).
     *  Only generates a small area to avoid timeout. Plots outside this area get
     *  their borders/paths generated on-demand when claimed. */
    private void preGenerateGrid() {
        World w = getPlotsWorld();
        if (w == null) return;
        int baseY = SURFACE_Y;
        int gridRadius = 3; // 3 plots in each direction = 7x7 grid (49 plots)

        plugin.getLogger().info("Pre-generating initial plot grid (" + (gridRadius * 2 + 1) + "x" + (gridRadius * 2 + 1) + ")...");

        int minC = -gridRadius;
        int maxC = gridRadius;

        for (int px = minC; px <= maxC; px++) {
            for (int pz = minC; pz <= maxC; pz++) {
                buildPlotBordersAndPaths(w, px, pz, baseY);
            }
        }

        plugin.getLogger().info("Plot grid pre-generation complete.");
    }

    /** Build borders and paths for a single plot at the given grid coordinate. */
    private void buildPlotBordersAndPaths(World w, int px, int pz, int baseY) {
        // Never regenerate borders/paths on an already-claimed plot: its breakable border and
        // shared paths may have been customized by the owner. Claimed plots keep what was built.
        if (plots.containsKey(new PlotCoord(px, pz))) {
            return;
        }
        int ox = px * CELL_SIZE;
        int oz = pz * CELL_SIZE;
        int slabY = baseY + 1; // slabs and paths sit on top of the grass surface

        // --- Border: oak slab ring ---
        // Top and bottom edges (z = 0 and z = 17)
        for (int x = 0; x <= PLOT_SIZE + 1; x++) {
            setBlock(w, ox + x, slabY, oz, Material.OAK_SLAB);
            setBlock(w, ox + x, slabY, oz + PLOT_SIZE + 1, Material.OAK_SLAB);
        }
        // Left and right edges (x = 0 and x = 17), excluding corners
        for (int z = 1; z <= PLOT_SIZE; z++) {
            setBlock(w, ox, slabY, oz + z, Material.OAK_SLAB);
            setBlock(w, ox + PLOT_SIZE + 1, slabY, oz + z, Material.OAK_SLAB);
        }

        // --- Path: 2-block-wide stone brick paths (lowered by 1 block to sit at grass level) ---
        // Paths are cleared 2 blocks above (baseY+1..baseY+2) on every regen so leftover
        // blocks from previous generations don't linger above the walkway.
        // A path side between two plots owned by the SAME player is exempt from this clear, so the
        // owner's shared path between their neighbouring plots is preserved across a reload/regen.
        PlotCoord self = new PlotCoord(px, pz);
        // Top path (z = -2 to -1)
        if (!sharedPathBetween(self, new PlotCoord(px, pz - 1))) {
        for (int x = 0; x <= PLOT_SIZE + 1 + PATH_WIDTH; x++) {
            for (int pzOff = 1; pzOff <= PATH_WIDTH; pzOff++) {
                setBlock(w, ox + x, baseY, oz - pzOff, Material.STONE_BRICKS);
                setBlock(w, ox + x, baseY + 1, oz - pzOff, Material.AIR);
                setBlock(w, ox + x, baseY + 2, oz - pzOff, Material.AIR);
            }
        }
        }
        // Bottom path (z = 18 to 19)
        if (!sharedPathBetween(self, new PlotCoord(px, pz + 1))) {
        for (int x = 0; x <= PLOT_SIZE + 1 + PATH_WIDTH; x++) {
            for (int pzOff = 1; pzOff <= PATH_WIDTH; pzOff++) {
                setBlock(w, ox + x, baseY, oz + PLOT_SIZE + 1 + pzOff, Material.STONE_BRICKS);
                setBlock(w, ox + x, baseY + 1, oz + PLOT_SIZE + 1 + pzOff, Material.AIR);
                setBlock(w, ox + x, baseY + 2, oz + PLOT_SIZE + 1 + pzOff, Material.AIR);
            }
        }
        }
        // Left path (x = -2 to -1)
        if (!sharedPathBetween(self, new PlotCoord(px - 1, pz))) {
        for (int z = 0; z <= PLOT_SIZE + 1 + PATH_WIDTH; z++) {
            for (int px2 = 1; px2 <= PATH_WIDTH; px2++) {
                setBlock(w, ox - px2, baseY, oz + z, Material.STONE_BRICKS);
                setBlock(w, ox - px2, baseY + 1, oz + z, Material.AIR);
                setBlock(w, ox - px2, baseY + 2, oz + z, Material.AIR);
            }
        }
        }
        // Right path (x = 18 to 19)
        if (!sharedPathBetween(self, new PlotCoord(px + 1, pz))) {
        for (int z = 0; z <= PLOT_SIZE + 1 + PATH_WIDTH; z++) {
            for (int px2 = 1; px2 <= PATH_WIDTH; px2++) {
                setBlock(w, ox + PLOT_SIZE + 1 + px2, baseY, oz + z, Material.STONE_BRICKS);
                setBlock(w, ox + PLOT_SIZE + 1 + px2, baseY + 1, oz + z, Material.AIR);
                setBlock(w, ox + PLOT_SIZE + 1 + px2, baseY + 2, oz + z, Material.AIR);
            }
        }
        }
    }

    /** Build the plot: place borders, paths (if not already pre-generated), and starter chest. */
    private void buildPlot(PlotCoord coord) {
        World w = getPlotsWorld();
        if (w == null) return;
        Location origin = plotOrigin(coord);
        if (origin == null) return;
        int ox = origin.getBlockX();
        int oz = origin.getBlockZ();
        int baseY = origin.getBlockY();

        // Ensure borders and paths exist for this plot (handles plots outside the pre-generated area)
        // Check if the top-left corner slab exists; if not, this plot wasn't pre-generated
        if (w.getBlockAt(ox, baseY, oz).getType() != Material.OAK_SLAB) {
            buildPlotBordersAndPaths(w, coord.x(), coord.z(), baseY);
        }

        // --- Starter chest at the center of the buildable area ---
        int cx = ox + PLOT_SIZE / 2 + 1; // center of buildable area (offset 1 for border)
        int cz = oz + PLOT_SIZE / 2 + 1;
        w.getBlockAt(cx, baseY + 1, cz).setType(Material.CHEST, false);
        Chest chest = (Chest) w.getBlockAt(cx, baseY + 1, cz).getState();
        // Add starter items: 2 saplings, 2 water buckets, 1 lava bucket
        chest.getInventory().addItem(new ItemStack(Material.OAK_SAPLING, 2));
        chest.getInventory().addItem(new ItemStack(Material.WATER_BUCKET, 2));
        chest.getInventory().addItem(new ItemStack(Material.LAVA_BUCKET, 1));
    }

    /** Set a block with physics disabled. */
    private void setBlock(World w, int x, int y, int z, Material mat) {
        w.getBlockAt(x, y, z).setType(mat, false);
    }

    // ==================== RETROACTIVE LAYER FILL ====================

    /**
     * Fill the bottom layers of every chunk in the plots world:
     * bedrock at y=0, stone at y=1..{@link #STONE_TOP_Y}.
     * <p>
     * Only touches y=0..{@link #STONE_TOP_Y}, which is below all builds
     * (surface is at y={@link #SURFACE_Y}+1). Existing builds above are untouched.
     * New chunks will already have correct layers from {@link PlotChunkGenerator}.
     * <p>
     * Iterates a generous radius from spawn to cover all explored areas. Loads
     * each chunk before filling so ungenerated areas receive the layers too.
     */
    public int fillPlotLayers() {
        World w = getPlotsWorld();
        if (w == null) return 0;

        int chunksFilled = 0;
        int radius = 15; // chunks in each direction from spawn (covers ±300 blocks)

        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) {
                // isChunkGenerated may return false for non-existent chunks
                if (!w.isChunkGenerated(cx, cz)) continue;
                org.bukkit.Chunk chunk = w.getChunkAt(cx, cz);
                fillChunkLayers(chunk);
                chunksFilled++;
            }
        }

        return chunksFilled;
    }

    /**
     * Fill the bottom layers of a single chunk with bedrock (y=0) and
     * stone (y=1..{@link #STONE_TOP_Y}). Only touches y &le;
     * {@link #STONE_TOP_Y}, well below the surface.
     */
    private void fillChunkLayers(org.bukkit.Chunk chunk) {
        int cx = chunk.getX() << 4; // chunk block X origin
        int cz = chunk.getZ() << 4;
        World w = chunk.getWorld();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int wx = cx + dx;
                int wz = cz + dz;

                // Bedrock at y=0
                w.getBlockAt(wx, 0, wz).setType(Material.BEDROCK, false);

                // Stone at y=1..STONE_TOP_Y
                for (int y = STONE_BOTTOM_Y; y <= STONE_TOP_Y; y++) {
                    w.getBlockAt(wx, y, wz).setType(Material.STONE, false);
                }
            }
        }
    }

    // ==================== PERSISTENCE ====================

    /** Load plot data from plots.yml. */
    private void load() {
        if (!plotsFile.exists()) return;
        try {
            plotsData.load(plotsFile);
            for (String key : plotsData.getKeys(false)) {
                String[] parts = key.split(",");
                if (parts.length != 2) continue;
                int px = Integer.parseInt(parts[0]);
                int pz = Integer.parseInt(parts[1]);
                PlotCoord coord = new PlotCoord(px, pz);
                UUID owner = UUID.fromString(plotsData.getString(key + ".owner"));
                long claimedAt = plotsData.getLong(key + ".claimedAt");
                String name = plotsData.getString(key + ".name");
                PlotInfo info = name != null && !name.isEmpty()
                        ? new PlotInfo(owner, claimedAt, name)
                        : new PlotInfo(owner, claimedAt);
                info.pvp = plotsData.getBoolean(key + ".pvp", false);
                info.fireSpread = plotsData.getBoolean(key + ".fireSpread", false);
                info.isPublic = plotsData.getBoolean(key + ".public", false);
                info.mobKill = plotsData.getBoolean(key + ".mobKill", false);
                info.id = plotsData.getLong(key + ".id", 0);
                for (String u : plotsData.getStringList(key + ".buildTrust")) {
                    try { info.buildTrust.add(UUID.fromString(u)); } catch (IllegalArgumentException ignored) {}
                }
                for (String u : plotsData.getStringList(key + ".containerTrust")) {
                    try { info.containerTrust.add(UUID.fromString(u)); } catch (IllegalArgumentException ignored) {}
                }
                for (String u : plotsData.getStringList(key + ".pickupTrust")) {
                    try { info.pickupTrust.add(UUID.fromString(u)); } catch (IllegalArgumentException ignored) {}
                }
                for (String u : plotsData.getStringList(key + ".mobKillTrust")) {
                    try { info.mobKillTrust.add(UUID.fromString(u)); } catch (IllegalArgumentException ignored) {}
                }
                plots.put(coord, info);
                if (info.name != null) {
                    String nk = info.name.toLowerCase();
                    if (!nameToPlot.containsKey(nk)) {
                        nameToPlot.put(nk, coord);
                    }
                }
            }
            // Compute the next id and backfill any legacy plots saved without an id.
            long maxId = plots.values().stream().mapToLong(i -> i.id).max().orElse(0);
            nextPlotId = maxId + 1;
            for (PlotInfo i : plots.values()) {
                if (i.id == 0) {
                    i.id = nextPlotId++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Back up corrupt file
            try {
                File corrupt = new File(plotsFile.getParentFile(),
                        plotsFile.getName() + ".corrupt-" + System.currentTimeMillis());
                if (plotsFile.renameTo(corrupt)) {
                    plugin.getLogger().warning("Corrupt plots.yml backed up to " + corrupt.getName());
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /** Save plot data to plots.yml. */
    public void save() {
        try {
            for (String k : plotsData.getKeys(false)) plotsData.set(k, null); // clear old keys
            for (Map.Entry<PlotCoord, PlotInfo> e : plots.entrySet()) {
                String key = e.getKey().x() + "," + e.getKey().z();
                plotsData.set(key + ".owner", e.getValue().owner.toString());
                plotsData.set(key + ".claimedAt", e.getValue().claimedAt);
                plotsData.set(key + ".id", e.getValue().id);
                if (e.getValue().name != null) {
                    plotsData.set(key + ".name", e.getValue().name);
                }
                plotsData.set(key + ".pvp", e.getValue().pvp);
                plotsData.set(key + ".fireSpread", e.getValue().fireSpread);
                plotsData.set(key + ".public", e.getValue().isPublic);
                plotsData.set(key + ".mobKill", e.getValue().mobKill);
                plotsData.set(key + ".buildTrust", e.getValue().buildTrust.stream().map(UUID::toString).collect(Collectors.toList()));
                plotsData.set(key + ".containerTrust", e.getValue().containerTrust.stream().map(UUID::toString).collect(Collectors.toList()));
                plotsData.set(key + ".pickupTrust", e.getValue().pickupTrust.stream().map(UUID::toString).collect(Collectors.toList()));
                plotsData.set(key + ".mobKillTrust", e.getValue().mobKillTrust.stream().map(UUID::toString).collect(Collectors.toList()));
            }
            plotsFile.getParentFile().mkdirs();
            File tmp = new File(plotsFile.getParentFile(), plotsFile.getName() + ".tmp");
            Files.write(tmp.toPath(), plotsData.saveToString().getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp.toPath(), plotsFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tmp.toPath(), plotsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Get the plot info for a player's first plot, or null. */
    public PlotInfo getPlayerPlot(Player p) {
        List<PlotCoord> owned = ownedPlots(p.getUniqueId());
        if (owned.isEmpty()) return null;
        return plots.get(owned.get(0));
    }

    /** Get the coordinate of a player's first plot, or null. */
    public PlotCoord getPlayerPlotCoord(Player p) {
        List<PlotCoord> owned = ownedPlots(p.getUniqueId());
        return owned.isEmpty() ? null : owned.get(0);
    }

    /** Re-home every plot reference (ownership and the trust lists) from one UUID to another after an
     *  offline-login profile migration, so the player's plots follow them to their new account UUID. */
    public void reassignPlotOwner(UUID oldOwner, UUID newOwner) {
        if (oldOwner == null || newOwner == null || oldOwner.equals(newOwner)) return;
        boolean changed = false;
        for (PlotInfo info : plots.values()) {
            if (oldOwner.equals(info.owner)) {
                info.owner = newOwner;
                changed = true;
            }
            if (info.buildTrust.remove(oldOwner)) { info.buildTrust.add(newOwner); changed = true; }
            if (info.containerTrust.remove(oldOwner)) { info.containerTrust.add(newOwner); changed = true; }
            if (info.pickupTrust.remove(oldOwner)) { info.pickupTrust.add(newOwner); changed = true; }
            if (info.mobKillTrust.remove(oldOwner)) { info.mobKillTrust.add(newOwner); changed = true; }
        }
        if (changed) save();
    }
}