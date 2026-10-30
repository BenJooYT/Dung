package com.lieyabull.dung.dungeon;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.EnumMap;
import java.util.Map;

/**
 * Utility that batches block operations through a WorldEdit {@link EditSession}
 * when one is active, falling back to direct {@link World#getBlockAt} calls
 * when no session is running.
 *
 * <p>Usage: call {@link #begin(EditSession)} before starting a bulk build, then
 * call {@link #setBlock(World, int, int, int, Material)} for each block; call
 * {@link #end()} when done to close the session and flush changes.</p>
 */
public final class BlockBatcher {

    private static EditSession session;
    private static final Map<Material, BlockState> stateCache = new EnumMap<>(Material.class);

    private BlockBatcher() {}

    /** Start a batch session; subsequent calls to {@link #setBlock} route through the session. */
    public static void begin(EditSession s) {
        session = s;
    }

    /** Close the batch session and flush all changes; subsequent calls fall back to direct API. */
    public static void end() {
        if (session != null) {
            session.close();
            session = null;
        }
    }

    /**
     * Set a block at the given world coordinates. When a batch session is active,
     * the change goes through the session (bulk commit, no per-block lighting/physics);
     * otherwise it falls back to the standard Bukkit {@code getBlockAt().setType()}.
     */
    public static void setBlock(World w, int x, int y, int z, Material m) {
        if (session != null) {
            BlockState bs = stateCache.get(m);
            if (bs == null) {
                bs = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(m.createBlockData());
                stateCache.put(m, bs);
            }
            try {
                session.setBlock(BlockVector3.at(x, y, z), bs);
            } catch (MaxChangedBlocksException ignored) {
                // unreachable — no block limit is set on the session
            }
        } else {
            w.getBlockAt(x, y, z).setType(m);
        }
    }

    /** Set a block with explicit data (used for ceiling-hanging lanterns). */
    public static void setBlock(World w, int x, int y, int z, BlockData data) {
        if (session != null) {
            BlockState bs = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(data);
            try {
                session.setBlock(BlockVector3.at(x, y, z), bs);
            } catch (MaxChangedBlocksException ignored) {
                // unreachable — no block limit is set on the session
            }
        } else {
            w.getBlockAt(x, y, z).setBlockData(data, false);
        }
    }

    /** Place a lantern hanging from the block above it instead of a standing lantern. */
    public static void setHangingLantern(World w, int x, int y, int z) {
        if (org.bukkit.Bukkit.getServer() == null) {
            // Headless tests have no Bukkit server to create block data; physics is not simulated.
            setBlock(w, x, y, z, Material.LANTERN);
            return;
        }
        org.bukkit.block.data.type.Lantern lantern =
                (org.bukkit.block.data.type.Lantern) Material.LANTERN.createBlockData();
        lantern.setHanging(true);
        setBlock(w, x, y, z, lantern);
    }

    /** Convenience: set a block followed by setting another. */
    public static void setBlock(World w, int x, int y, int z, Material m, World w2, int x2, int y2, int z2, Material m2) {
        setBlock(w, x, y, z, m);
        setBlock(w2, x2, y2, z2, m2);
    }
}