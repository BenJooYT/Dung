package com.lieyabull.dung.plot;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Fills the plots world with flat layered terrain: bedrock (y=0), stone
 * (y=1..30), dirt (y=31..50), grass (y=51). This mirrors PlotManager.SURFACE_Y
 * so the plot/path/chest placement code stays aligned. A custom generator is
 * used because the vanilla superflat preset settings are unreliable across
 * Paper versions.
 */
public final class PlotChunkGenerator extends ChunkGenerator {

    private static final BlockData BEDROCK = Material.BEDROCK.createBlockData();
    private static final BlockData STONE = Material.STONE.createBlockData();
    private static final BlockData DIRT = Material.DIRT.createBlockData();
    private static final BlockData GRASS = Material.GRASS_BLOCK.createBlockData();

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunkData.setBlock(x, 0, z, BEDROCK);
                for (int y = 1; y <= 30; y++) {
                    chunkData.setBlock(x, y, z, STONE);
                }
                for (int y = 31; y <= 50; y++) {
                    chunkData.setBlock(x, y, z, DIRT);
                }
                chunkData.setBlock(x, 51, z, GRASS);
            }
        }
    }
}