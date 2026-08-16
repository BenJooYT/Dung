package com.lieyabull.dung.room;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Flat empty canvas for the room editor world: a single grass surface at SURFACE_Y so the author
 * can stand and build, with no over-world structures/mobs. Mirrors the plots world approach (a
 * custom generator is more reliable than Paper's superflat preset).
 */
public final class RoomChunkGenerator extends ChunkGenerator {
    public static final int SURFACE_Y = 51;

    private static final BlockData STONE = Material.STONE.createBlockData();
    private static final BlockData DIRT = Material.DIRT.createBlockData();
    private static final BlockData GRASS = Material.GRASS_BLOCK.createBlockData();

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunkData.setBlock(x, 0, z, STONE);
                for (int y = 1; y <= 50; y++) chunkData.setBlock(x, y, z, DIRT);
                chunkData.setBlock(x, SURFACE_Y, z, GRASS);
            }
        }
    }
}