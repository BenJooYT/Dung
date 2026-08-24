package com.lieyabull.dung.plot.potion;

import com.lieyabull.dung.plot.PlotManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Reusable propagation engine for transformation potions.
 * <p>
 * The algorithm:
 * <ol>
 *   <li>Start at the impact point</li>
 *   <li>BFS outward through eligible blocks (same target family)</li>
 *   <li>For each eligible block, roll spread probability to decide if it transforms</li>
 *   <li>Group transformed blocks into waves by Manhattan distance from origin</li>
 *   <li>Respect max range, max transformed blocks, plot ownership, and player-placed rules</li>
 * </ol>
 * This class is designed so the core logic can be tested deterministically (the Bukkit
 * {@link Block} references are just handles — the engine reads Material and Location from them).
 */
public final class PropagationEngine {

    private PropagationEngine() {}

    /** How far a surface-thrown potion may drill down through cover to find a target block. */
    public static final int MAX_COVER_DEPTH = 64;

    /**
     * Find the target block a splash should propagate from.
     * <ol>
     *   <li>The impact block itself, if it already matches.</li>
     *   <li>A 5×5 horizontal × +3/−2 vertical neighborhood around it — a potion thrown at a
     *       TREE splashes into the AIR beside the trunk, and trees extend upward, so a
     *       down-only scan would never see the wood.</li>
     *   <li>Otherwise drill straight down through cover (buried /plot filllayers stone).</li>
     * </ol>
     * Returns null when no target exists anywhere near the impact.
     */
    public static Block drillToTarget(Block start, PotionDefinition definition) {
        if (definition.isTarget(start.getType())) return start;
        for (int dy = 3; dy >= -2; dy--) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block b = start.getRelative(dx, dy, dz);
                    if (definition.isTarget(b.getType())) return b;
                }
            }
        }
        Block cursor = start;
        for (int i = 0; i < MAX_COVER_DEPTH; i++) {
            cursor = cursor.getRelative(0, -1, 0);
            if (definition.isTarget(cursor.getType())) return cursor;
        }
        return null;
    }

    /**
     * Propagate a potion transformation from the impact point.
     *
     * @param world          the world
     * @param origin         the impact location
     * @param definition     the potion definition
     * @param plotOwner      the UUID of the plot owner (null if not on a plot)
     * @param convertEnabled whether player-placed blocks may be transformed
     * @param isPlayerPlaced predicate that returns true if a block was placed by a player
     * @param plotManager    the plot manager for ownership checks
     * @param rng            random number generator
     * @return the propagation result
     */
    public static PropagationResult propagate(
            World world, Location origin, PotionDefinition definition,
            UUID plotOwner, boolean convertEnabled,
            Predicate<Block> isPlayerPlaced,
            PlotManager plotManager, Random rng) {

        // Determine the plot coordinate for the origin
        PlotManager.PlotCoord originPlot = plotManager.plotAt(origin);
        if (originPlot == null) {
            return PropagationResult.noTargets(origin, definition.id());
        }

        // Check that the player owns the plot at the impact point
        if (plotOwner == null || !plotManager.ownsPlotByOwner(originPlot, plotOwner)) {
            return PropagationResult.noTargets(origin, definition.id());
        }

        // BFS propagation
        Set<Block> visited = new HashSet<>();
        List<List<Block>> waves = new ArrayList<>();
        int totalTransformed = 0;
        int maxRange = definition.maxRange();
        int maxBlocks = definition.maxTransformedBlocks();
        double spreadProb = definition.spreadProbability();

        // Start at the origin block. If the potion landed on a non-target surface (e.g. grass
        // over the /plot filllayers stone layer), drill straight down through cover to the first
        // target block so buried fill-layer stone is reachable from the surface.
        Block originBlock = drillToTarget(origin.getBlock(), definition);
        if (originBlock == null
                || !isEligible(originBlock, definition, plotManager, originPlot, convertEnabled, isPlayerPlaced)) {
            return PropagationResult.noTargets(origin, definition.id());
        }

        visited.add(originBlock);

        // BFS layered by Manhattan distance for wave grouping.
        // Use a two-queue approach: one for the current layer, one for the next.
        Queue<Block> currentLayer = new ArrayDeque<>();
        Queue<Block> nextLayer = new ArrayDeque<>();
        currentLayer.add(originBlock);
        int currentDistance = 0;

        while (!currentLayer.isEmpty() && totalTransformed < maxBlocks && currentDistance <= maxRange) {
            List<Block> wave = new ArrayList<>();

            while (!currentLayer.isEmpty() && totalTransformed < maxBlocks) {
                Block block = currentLayer.poll();

                // Check if this block should be transformed
                if (isEligible(block, definition, plotManager, originPlot, convertEnabled, isPlayerPlaced)) {
                    // Roll spread probability
                    if (rng.nextDouble() < spreadProb) {
                        wave.add(block);
                        totalTransformed++;
                        if (totalTransformed >= maxBlocks) break;
                    }
                }

                // Explore neighbors for the next layer
                for (Block neighbor : getNeighbors(block)) {
                    if (visited.contains(neighbor)) continue;
                    visited.add(neighbor);

                    // Only enqueue if neighbor is a target material
                    if (definition.isTarget(neighbor.getType())) {
                        int nDist = manhattanDist(originBlock, neighbor);
                        if (nDist <= maxRange && isInOwnedPlot(neighbor, plotManager, originPlot)) {
                            nextLayer.add(neighbor);
                        }
                    }
                }
            }

            if (!wave.isEmpty()) {
                waves.add(wave);
            }

            // Move to the next layer
            currentDistance++;
            Queue<Block> tmp = currentLayer;
            currentLayer = nextLayer;
            nextLayer = tmp;
        }

        if (totalTransformed == 0) {
            return PropagationResult.noTargets(origin, definition.id());
        }

        return new PropagationResult(origin, waves, totalTransformed, false, definition.id());
    }

    /** Check if a block is eligible for transformation. */
    private static boolean isEligible(Block block, PotionDefinition def,
                                      PlotManager pm, PlotManager.PlotCoord plot,
                                      boolean convertEnabled, Predicate<Block> isPlayerPlaced) {
        if (!def.isTarget(block.getType())) return false;
        if (!isInOwnedPlot(block, pm, plot)) return false;
        // Player-placed blocks are only eligible if convert is enabled
        if (isPlayerPlaced.test(block) && !convertEnabled) return false;
        // Don't transform plot infrastructure (border/path blocks)
        if (isPlotInfrastructure(block, pm, plot)) return false;
        return true;
    }

    /** Check if a block is within the owned plot. */
    private static boolean isInOwnedPlot(Block block, PlotManager pm, PlotManager.PlotCoord plot) {
        PlotManager.PlotCoord coord = pm.plotAt(block.getLocation());
        return plot.equals(coord) && pm.isBuildableArea(block.getLocation());
    }

    /** Check if a block is part of plot infrastructure (border slab or path). */
    private static boolean isPlotInfrastructure(Block block, PlotManager pm, PlotManager.PlotCoord plot) {
        return !pm.isBuildableArea(block.getLocation());
    }

    /** Get the 6-directional neighbors of a block. */
    private static List<Block> getNeighbors(Block block) {
        return List.of(
                block.getRelative(1, 0, 0),
                block.getRelative(-1, 0, 0),
                block.getRelative(0, 1, 0),
                block.getRelative(0, -1, 0),
                block.getRelative(0, 0, 1),
                block.getRelative(0, 0, -1)
        );
    }

    /** Manhattan distance between two blocks. */
    private static int manhattanDist(Block a, Block b) {
        return Math.abs(a.getX() - b.getX())
                + Math.abs(a.getY() - b.getY())
                + Math.abs(a.getZ() - b.getZ());
    }

    /**
     * Transform a block using the potion's weighted pool, preserving the block family
     * (log -> log, leaves -> leaves) for the Forest Potion.
     *
     * @param block      the block to transform
     * @param definition the potion definition
     * @param rng        random number generator
     * @return the new material, or the original if no match found
     */
    public static Material resolveTransformation(Block block, PotionDefinition definition, Random rng) {
        Material original = block.getType();

        // For Forest Potion: preserve wood family (log -> log, leaves -> leaves)
        if (definition.id().equals("forest")) {
            boolean isLog = original.name().endsWith("_LOG");
            boolean isLeaves = original.name().endsWith("_LEAVES");

            if (isLog || isLeaves) {
                // Roll from the pool, but filter by family
                List<Material> candidates = new ArrayList<>();
                List<Double> weights = new ArrayList<>();
                for (java.util.Map.Entry<Material, Double> e : definition.transformationPool().entrySet()) {
                    Material mat = e.getKey();
                    boolean isCandidateLog = mat.name().endsWith("_LOG");
                    boolean isCandidateLeaves = mat.name().endsWith("_LEAVES");
                    if ((isLog && isCandidateLog) || (isLeaves && isCandidateLeaves)) {
                        candidates.add(mat);
                        weights.add(e.getValue());
                    }
                }
                if (candidates.isEmpty()) return original;
                return weightedRandom(candidates, weights, rng);
            }
        }

        // For Stone Potion and others: roll from the full pool
        Material rolled = definition.rollTransformation(rng);
        return rolled != null ? rolled : original;
    }

    private static Material weightedRandom(List<Material> candidates, List<Double> weights, Random rng) {
        double total = 0;
        for (double w : weights) total += w;
        double roll = rng.nextDouble() * total;
        double cumulative = 0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights.get(i);
            if (roll <= cumulative) return candidates.get(i);
        }
        return candidates.get(candidates.size() - 1);
    }
}