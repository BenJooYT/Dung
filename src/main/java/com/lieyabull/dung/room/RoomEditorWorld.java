package com.lieyabull.dung.room;

import com.lieyabull.dung.Dung;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

/**
 * The isolated room-editor world. A dedicated flat world so authors build room templates without
 * touching the dungeon world or server config. Purely a build sandbox - never used for real runs,
 * and never torn down like a dungeon instance.
 */
public final class RoomEditorWorld {
    public static final String WORLD_NAME = "dung_editor";
    private final Dung plugin;
    private World world;

    public RoomEditorWorld(Dung plugin) {
        this.plugin = plugin;
    }

    public World getEditorWorld() {
        if (world != null && world.isChunkLoaded(world.getSpawnLocation().getChunk())) return world;
        world = Bukkit.getWorld(WORLD_NAME);
        if (world == null) {
            WorldCreator wc = new WorldCreator(WORLD_NAME);
            wc.generator(new RoomChunkGenerator());
            wc.generateStructures(false);
            wc.type(WorldType.FLAT);
            wc.environment(World.Environment.NORMAL);
            world = wc.createWorld();
            if (world != null) {
                world.setSpawnLocation(0, RoomChunkGenerator.SURFACE_Y + 1, 0);
                world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                world.setTime(1000);
            }
        }
        return world;
    }

    public boolean isEditorWorld(World w) {
        return w != null && w.getName().equals(WORLD_NAME);
    }

    /** Wipe a volume to air so a test instantiation starts from a clean slate. */
    public void clearRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        World w = getEditorWorld();
        if (w == null) return;
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                for (int y = minY; y <= maxY; y++)
                    w.getBlockAt(x, y, z).setType(org.bukkit.Material.AIR, false);
    }
}