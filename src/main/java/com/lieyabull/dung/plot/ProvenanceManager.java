package com.lieyabull.dung.plot;

import com.lieyabull.dung.Dung;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks which blocks in the plots world were placed by players.
 * Uses a simple set of packed block positions (long) for fast membership checks.
 * <p>
 * This is used by the potion system to determine whether a block is "natural"
 * (eligible for transformation) or player-placed (requires /convert to be enabled).
 */
public final class ProvenanceManager implements Listener {

    private final Dung plugin;
    private final File dataFile;
    private final YamlConfiguration data = new YamlConfiguration();
    /** Packed block positions: ((long)x & 0x3FFFFFF) << 38 | ((long)y & 0xFFF) << 26 | ((long)z & 0x3FFFFFF) */
    private final Set<Long> placedBlocks = new HashSet<>();

    public ProvenanceManager(Dung plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "provenance.yml");
        load();
    }

    /** Pack a block position into a single long. */
    private static long pack(Block block) {
        long x = block.getX();
        long y = block.getY();
        long z = block.getZ();
        return (x & 0x3FFFFFFL) << 38 | (y & 0xFFFL) << 26 | (z & 0x3FFFFFFL);
    }

    /** Unpack a long into a Location (must be in the plots world). */
    private static Location unpack(long packed, World world) {
        int x = (int) (packed >> 38);
        int y = (int) ((packed >> 26) & 0xFFFL);
        int z = (int) (packed & 0x3FFFFFFL);
        // Sign-extend if needed
        if ((x & 0x2000000) != 0) x |= ~0x3FFFFFF;
        if ((z & 0x2000000) != 0) z |= ~0x3FFFFFF;
        return new Location(world, x, y, z);
    }

    /** True if the block was placed by a player. */
    public boolean isPlayerPlaced(Block block) {
        if (block == null) return false;
        if (!isPlotsWorld(block)) return false;
        return placedBlocks.contains(pack(block));
    }

    /** Record a player-placed block. */
    public void markPlaced(Block block) {
        if (block == null) return;
        if (!isPlotsWorld(block)) return;
        placedBlocks.add(pack(block));
    }

    /** Remove a block from the provenance set (e.g., when broken). */
    public void unmark(Block block) {
        if (block == null) return;
        placedBlocks.remove(pack(block));
    }

    private boolean isPlotsWorld(Block block) {
        World w = block.getWorld();
        return w != null && w.getName().equals(PlotManager.PLOTS_WORLD_NAME);
    }

    // ==================== BUKKIT EVENTS ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Block block = e.getBlock();
        if (!isPlotsWorld(block)) return;
        markPlaced(block);
        save();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        if (!isPlotsWorld(block)) return;
        unmark(block);
        save();
    }

    // ==================== PERSISTENCE ====================

    private void load() {
        if (!dataFile.exists()) return;
        try {
            data.load(dataFile);
            World w = Bukkit.getWorld(PlotManager.PLOTS_WORLD_NAME);
            for (String key : data.getKeys(false)) {
                try {
                    long packed = Long.parseLong(key);
                    placedBlocks.add(packed);
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load provenance data: " + e.getMessage());
        }
    }

    public void save() {
        try {
            for (String k : data.getKeys(false)) data.set(k, null);
            for (long packed : placedBlocks) {
                data.set(String.valueOf(packed), true);
            }
            dataFile.getParentFile().mkdirs();
            File tmp = new File(dataFile.getParentFile(), dataFile.getName() + ".tmp");
            Files.write(tmp.toPath(), data.saveToString().getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp.toPath(), dataFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tmp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save provenance data: " + e.getMessage());
        }
    }
}