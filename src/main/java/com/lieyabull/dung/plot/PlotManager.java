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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    // Surface Y level of the flat world (grass at y=51, dirt at y=1-50, stone at y=0)
    // Layers are specified top-to-bottom: grass_block(1) + dirt(50) + stone(1) = 52 blocks (y=0..51)
    private static final int SURFACE_Y = 51;

    // Plot origin: plots start at x=0, z=0. Plot (0,0) occupies x=[0..19], z=[0..19]
    // Buildable area: x=[0..17], z=[0..17] (includes the oak slab border)
    // Border: x=0, x=17, z=0, z=17 (oak slabs) — now breakable
    // Path: x=18..19, z=18..19 (between plots) — still protected

    public static final int CLAIM_SHARD_COST = 250;
    public static final int CLAIM_COIN_COST = 150;

    private final Dung plugin;
    private final File plotsFile;
    private final YamlConfiguration plotsData = new YamlConfiguration();
    private final Map<PlotCoord, PlotInfo> plots = new LinkedHashMap<>();
    private final Map<UUID, PlotCoord> playerPlots = new LinkedHashMap<>();
    private final Map<String, PlotCoord> nameToPlot = new LinkedHashMap<>();
    private World plotsWorld;

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
                plotsWorld.setTime(6000); // noon
                // Set world spawn to the path cross-section between the four corner plots
                plotsWorld.setSpawnLocation(-1, SURFACE_Y + 1, -1);
                // Pre-generate borders and paths for the entire grid
                preGenerateGrid();
            }
        }
        return plotsWorld;
    }

    /** Wipe all plot data from memory and disk. */
    public void clearAll() {
        plots.clear();
        playerPlots.clear();
        nameToPlot.clear();
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
        p.sendMessage("§aWelcome to the Plots! Use §f/plot claim§a to claim a plot.");
    }

    // ==================== PLOT COORDINATES ====================

    /** Represents a plot's grid position (column, row). */
    public record PlotCoord(int x, int z) {}

    /** Represents a claimed plot's owner and metadata. */
    public static final class PlotInfo {
        public UUID owner;
        public long claimedAt;
        public String name; // nullable — set when player names their plot

        public PlotInfo(UUID owner, long claimedAt) {
            this.owner = owner;
            this.claimedAt = claimedAt;
            this.name = null;
        }

        public PlotInfo(UUID owner, long claimedAt, String name) {
            this.owner = owner;
            this.claimedAt = claimedAt;
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
        if (playerPlots.containsKey(p.getUniqueId())) {
            p.sendMessage("§cYou already own a plot! Use §f/plot home§c to teleport to it.");
            return;
        }

        // Check if the player is standing on an unclaimed plot in the plots world
        PlotCoord standingOn = standingOnUnclaimedPlot(p);
        if (standingOn != null) {
            p.sendMessage("");
            p.sendMessage("§6§lClaim Plot at §f(" + standingOn.x() + ", " + standingOn.z() + ")");
        } else {
            p.sendMessage("");
            p.sendMessage("§6§lClaim a Plot");
        }

        MetaManager.MetaProfile profile = plugin.meta().profile(p.getUniqueId());
        p.sendMessage("§7  Your balance: §e" + profile.shards + " shards§7, §6" + profile.persistentCoins + " coins");
        p.sendMessage("");

        boolean canShards = profile.shards >= CLAIM_SHARD_COST;
        boolean canCoins = profile.persistentCoins >= CLAIM_COIN_COST;

        if (!canShards && !canCoins) {
            p.sendMessage("§cYou need §e" + CLAIM_SHARD_COST + " shards§c or §6" + CLAIM_COIN_COST + " coins§c to claim a plot.");
            return;
        }

        // Clickable shard option
        net.kyori.adventure.text.Component shardOpt = net.kyori.adventure.text.Component.text("[ ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(net.kyori.adventure.text.Component.text("Buy with " + CLAIM_SHARD_COST + " Shards",
                        canShards ? net.kyori.adventure.text.format.NamedTextColor.YELLOW : net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY,
                        net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(net.kyori.adventure.text.Component.text(" ]", net.kyori.adventure.text.format.NamedTextColor.GRAY));
        if (canShards) {
            shardOpt = shardOpt.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                    net.kyori.adventure.text.Component.text("Click to claim with " + CLAIM_SHARD_COST + " shards", net.kyori.adventure.text.format.NamedTextColor.GRAY)))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/plot claim shards"));
        }
        p.sendMessage(shardOpt);

        // Clickable coin option
        net.kyori.adventure.text.Component coinOpt = net.kyori.adventure.text.Component.text("[ ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(net.kyori.adventure.text.Component.text("Buy with " + CLAIM_COIN_COST + " Coins",
                        canCoins ? net.kyori.adventure.text.format.NamedTextColor.GOLD : net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY,
                        net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(net.kyori.adventure.text.Component.text(" ]", net.kyori.adventure.text.format.NamedTextColor.GRAY));
        if (canCoins) {
            coinOpt = coinOpt.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                    net.kyori.adventure.text.Component.text("Click to claim with " + CLAIM_COIN_COST + " coins", net.kyori.adventure.text.format.NamedTextColor.GRAY)))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/plot claim coins"));
        }
        p.sendMessage(coinOpt);
        p.sendMessage("");
    }

    /** Claim a plot for a player using the specified payment method. Returns a message (null = success).
     *  If the player is standing on an unclaimed plot in the plots world, that plot is claimed.
     *  Otherwise falls back to the spiral-assignment logic. */
    public String claimPlot(Player p, String paymentMethod) {
        // Check if player already owns a plot
        if (playerPlots.containsKey(p.getUniqueId())) {
            return "§cYou already own a plot! Use §f/plot home§c to teleport to it.";
        }

        MetaManager.MetaProfile profile = plugin.meta().profile(p.getUniqueId());

        boolean useShards;
        if (paymentMethod.equalsIgnoreCase("shards")) {
            if (profile.shards < CLAIM_SHARD_COST) {
                return "§cYou need §e" + CLAIM_SHARD_COST + " shards§c, but you only have §e" + profile.shards + "§c.";
            }
            useShards = true;
        } else if (paymentMethod.equalsIgnoreCase("coins")) {
            if (profile.persistentCoins < CLAIM_COIN_COST) {
                return "§cYou need §6" + CLAIM_COIN_COST + " coins§c, but you only have §6" + profile.persistentCoins + "§c.";
            }
            useShards = false;
        } else {
            return "§cInvalid payment method. Use §f/plot claim shards§c or §f/plot claim coins§c.";
        }

        // First check if the player is standing on an unclaimed plot in the plots world
        PlotCoord coord = standingOnUnclaimedPlot(p);
        if (coord == null) {
            // Fallback: find the next available plot via spiral search
            coord = findAvailablePlot();
            if (coord == null) {
                return "§cNo available plots! This shouldn't happen.";
            }
        }

        // Charge the player
        if (useShards) {
            profile.shards -= CLAIM_SHARD_COST;
        } else {
            profile.persistentCoins -= CLAIM_COIN_COST;
        }

        // Claim the plot
        plots.put(coord, new PlotInfo(p.getUniqueId(), System.currentTimeMillis()));
        playerPlots.put(p.getUniqueId(), coord);

        // Build the plot (border, path, starter chest) BEFORE persisting so a crash
        // mid-claim can't leave a plot owned but never built.
        buildPlot(coord);
        save();
        // Persist the shard/coin charge immediately so it can't roll back on a crash.
        plugin.meta().save();

        // Teleport player to their new plot
        Location origin = plotOrigin(coord);
        Location home = new Location(origin.getWorld(), origin.getBlockX() + 8.5, SURFACE_Y + 1, origin.getBlockZ() + 8.5);
        p.teleport(home);

        String costStr = useShards ? "§e" + CLAIM_SHARD_COST + " shards" : "§6" + CLAIM_COIN_COST + " coins";
        p.sendMessage("§aPlot claimed for " + costStr + "!");
        p.sendMessage("§aName your plot with §f/plot name <name>§a, or use §f/plot home§a to return later.");
        return null; // success
    }

    /** Remove a player's plot claim and free it for re-claiming. Returns a message (null = success). */
    public String unclaimPlot(Player p) {
        PlotCoord coord = playerPlots.remove(p.getUniqueId());
        if (coord == null) {
            return "§cYou don't have a plot to unclaim.";
        }
        PlotInfo info = plots.remove(coord);
        if (info != null && info.name != null) {
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

    /** Teleport a player to their claimed plot. */
    public boolean teleportToPlot(Player p) {
        PlotCoord coord = playerPlots.get(p.getUniqueId());
        if (coord == null) return false;
        return teleportToCoord(p, coord);
    }

    /** Teleport a player to a specific plot coordinate. */
    private boolean teleportToCoord(Player p, PlotCoord coord) {
        Location origin = plotOrigin(coord);
        if (origin == null) return false;
        Location home = new Location(origin.getWorld(), origin.getBlockX() + 8.5, SURFACE_Y + 1, origin.getBlockZ() + 8.5);
        p.teleport(home);
        return true;
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
        PlotCoord coord = playerPlots.get(p.getUniqueId());
        if (coord == null) {
            return "§cYou don't have a plot yet. Use §f/plot claim§c first.";
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

    /** Check if a player owns any plot. */
    public boolean hasPlot(Player p) {
        return playerPlots.containsKey(p.getUniqueId());
    }

    // ==================== PLOT GENERATION ====================

    /** Find the next available plot coordinate using a spiral search from (0,0). */
    private PlotCoord findAvailablePlot() {
        // Search in an expanding spiral
        int radius = 0;
        while (radius < 50) { // limit search radius
            // Top row (z = -radius), left to right
            for (int x = -radius; x <= radius; x++) {
                PlotCoord c = new PlotCoord(x, -radius);
                if (!plots.containsKey(c)) return c;
            }
            // Right column (x = radius), top to bottom
            for (int z = -radius + 1; z <= radius; z++) {
                PlotCoord c = new PlotCoord(radius, z);
                if (!plots.containsKey(c)) return c;
            }
            // Bottom row (z = radius), right to left
            for (int x = radius - 1; x >= -radius; x--) {
                PlotCoord c = new PlotCoord(x, radius);
                if (!plots.containsKey(c)) return c;
            }
            // Left column (x = -radius), bottom to top
            for (int z = radius - 1; z > -radius; z--) {
                PlotCoord c = new PlotCoord(-radius, z);
                if (!plots.containsKey(c)) return c;
            }
            radius++;
        }
        return null; // shouldn't happen with a 50-radius spiral
    }

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
        // Top path (z = -2 to -1)
        for (int x = 0; x <= PLOT_SIZE + 1 + PATH_WIDTH; x++) {
            for (int pzOff = 1; pzOff <= PATH_WIDTH; pzOff++) {
                setBlock(w, ox + x, baseY, oz - pzOff, Material.STONE_BRICKS);
                setBlock(w, ox + x, baseY + 1, oz - pzOff, Material.AIR);
                setBlock(w, ox + x, baseY + 2, oz - pzOff, Material.AIR);
            }
        }
        // Bottom path (z = 18 to 19)
        for (int x = 0; x <= PLOT_SIZE + 1 + PATH_WIDTH; x++) {
            for (int pzOff = 1; pzOff <= PATH_WIDTH; pzOff++) {
                setBlock(w, ox + x, baseY, oz + PLOT_SIZE + 1 + pzOff, Material.STONE_BRICKS);
                setBlock(w, ox + x, baseY + 1, oz + PLOT_SIZE + 1 + pzOff, Material.AIR);
                setBlock(w, ox + x, baseY + 2, oz + PLOT_SIZE + 1 + pzOff, Material.AIR);
            }
        }
        // Left path (x = -2 to -1)
        for (int z = 0; z <= PLOT_SIZE + 1 + PATH_WIDTH; z++) {
            for (int px2 = 1; px2 <= PATH_WIDTH; px2++) {
                setBlock(w, ox - px2, baseY, oz + z, Material.STONE_BRICKS);
                setBlock(w, ox - px2, baseY + 1, oz + z, Material.AIR);
                setBlock(w, ox - px2, baseY + 2, oz + z, Material.AIR);
            }
        }
        // Right path (x = 18 to 19)
        for (int z = 0; z <= PLOT_SIZE + 1 + PATH_WIDTH; z++) {
            for (int px2 = 1; px2 <= PATH_WIDTH; px2++) {
                setBlock(w, ox + PLOT_SIZE + 1 + px2, baseY, oz + z, Material.STONE_BRICKS);
                setBlock(w, ox + PLOT_SIZE + 1 + px2, baseY + 1, oz + z, Material.AIR);
                setBlock(w, ox + PLOT_SIZE + 1 + px2, baseY + 2, oz + z, Material.AIR);
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
                plots.put(coord, info);
                playerPlots.put(owner, coord);
                if (info.name != null) {
                    String nk = info.name.toLowerCase();
                    if (!nameToPlot.containsKey(nk)) {
                        nameToPlot.put(nk, coord);
                    }
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
                if (e.getValue().name != null) {
                    plotsData.set(key + ".name", e.getValue().name);
                }
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

    /** Get the plot info for a player's plot, or null. */
    public PlotInfo getPlayerPlot(Player p) {
        PlotCoord coord = playerPlots.get(p.getUniqueId());
        if (coord == null) return null;
        return plots.get(coord);
    }

    /** Get the plot coordinate for a player's plot, or null. */
    public PlotCoord getPlayerPlotCoord(Player p) {
        return playerPlots.get(p.getUniqueId());
    }
}