package com.lieyabull.dung.plot.potion;

import org.bukkit.Material;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Data definition for a transformation potion type.
 * Each potion defines which blocks it can target and what it can transform them into.
 */
public final class PotionDefinition {

    private final String id;
    private final String displayName;
    private final Set<Material> targetMaterials;
    private final Map<Material, Double> transformationPool; // target material -> weight (higher = more likely)
    private final int maxRange;
    private final int maxTransformedBlocks;
    private final double spreadProbability; // 0.0-1.0 — probability each eligible block is transformed

    public PotionDefinition(String id, String displayName,
                            Set<Material> targetMaterials,
                            Map<Material, Double> transformationPool,
                            int maxRange, int maxTransformedBlocks,
                            double spreadProbability) {
        this.id = id;
        this.displayName = displayName;
        this.targetMaterials = Collections.unmodifiableSet(targetMaterials);
        this.transformationPool = Collections.unmodifiableMap(transformationPool);
        this.maxRange = maxRange;
        this.maxTransformedBlocks = maxTransformedBlocks;
        this.spreadProbability = spreadProbability;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public Set<Material> targetMaterials() { return targetMaterials; }
    public Map<Material, Double> transformationPool() { return transformationPool; }
    public int maxRange() { return maxRange; }
    public int maxTransformedBlocks() { return maxTransformedBlocks; }
    public double spreadProbability() { return spreadProbability; }

    /** True if the given material is a valid target for this potion. */
    public boolean isTarget(Material mat) {
        return targetMaterials.contains(mat);
    }

    /**
     * Roll a weighted random material from the transformation pool.
     * Returns null if the pool is empty.
     */
    public Material rollTransformation(java.util.Random rng) {
        if (transformationPool.isEmpty()) return null;
        double totalWeight = 0;
        for (double w : transformationPool.values()) totalWeight += w;
        double roll = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (Map.Entry<Material, Double> e : transformationPool.entrySet()) {
            cumulative += e.getValue();
            if (roll <= cumulative) return e.getKey();
        }
        // Fallback: return the last entry
        return transformationPool.keySet().iterator().next();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PotionDefinition that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** A potion definition that matches both logs and leaves, with linked wood-family transformations. */
    public static final PotionDefinition FOREST = new PotionDefinition(
            "forest",
            "§aForest Transmutation Elixir",
            // Target both logs and leaves
            Set.of(
                    Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
                    Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
                    Material.MANGROVE_LOG, Material.CHERRY_LOG,
                    Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
                    Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
                    Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES
            ),
            // Transformation pool: wood-type pairs (log -> corresponding log, leaves -> corresponding leaves)
            createForestPool(),
            12,  // maxRange
            64,  // maxTransformedBlocks
            0.65 // spreadProbability
    );

    private static Map<Material, Double> createForestPool() {
        Map<Material, Double> pool = new LinkedHashMap<>();
        // Logs — weighted equally
        pool.put(Material.OAK_LOG, 1.0);
        pool.put(Material.SPRUCE_LOG, 1.0);
        pool.put(Material.BIRCH_LOG, 1.0);
        pool.put(Material.JUNGLE_LOG, 1.0);
        pool.put(Material.ACACIA_LOG, 1.0);
        pool.put(Material.DARK_OAK_LOG, 1.0);
        pool.put(Material.MANGROVE_LOG, 1.0);
        pool.put(Material.CHERRY_LOG, 1.0);
        // Leaves — weighted equally
        pool.put(Material.OAK_LEAVES, 1.0);
        pool.put(Material.SPRUCE_LEAVES, 1.0);
        pool.put(Material.BIRCH_LEAVES, 1.0);
        pool.put(Material.JUNGLE_LEAVES, 1.0);
        pool.put(Material.ACACIA_LEAVES, 1.0);
        pool.put(Material.DARK_OAK_LEAVES, 1.0);
        pool.put(Material.MANGROVE_LEAVES, 1.0);
        pool.put(Material.CHERRY_LEAVES, 1.0);
        return pool;
    }

    /** A potion that transforms stone into various stone/ore variants with weighted probabilities. */
    public static final PotionDefinition STONE = new PotionDefinition(
            "stone",
            "§7Stone Transmutation Elixir",
            // Target stone and related natural blocks
            Set.of(
                    Material.STONE, Material.COBBLESTONE, Material.ANDESITE,
                    Material.DIORITE, Material.GRANITE, Material.TUFF,
                    Material.DEEPSLATE, Material.COBBLED_DEEPSLATE,
                    Material.CALCITE, Material.DRIPSTONE_BLOCK,
                    Material.SMOOTH_BASALT
            ),
            createStonePool(),
            10,  // maxRange
            48,  // maxTransformedBlocks
            0.55 // spreadProbability
    );

    private static Map<Material, Double> createStonePool() {
        Map<Material, Double> pool = new LinkedHashMap<>();
        // Common stone variants — very high weight
        pool.put(Material.STONE, 15.0);
        pool.put(Material.ANDESITE, 8.0);
        pool.put(Material.DIORITE, 8.0);
        pool.put(Material.GRANITE, 8.0);
        pool.put(Material.COBBLESTONE, 10.0);
        // Common deep variants
        pool.put(Material.DEEPSLATE, 8.0);
        pool.put(Material.COBBLED_DEEPSLATE, 6.0);
        pool.put(Material.TUFF, 5.0);
        pool.put(Material.CALCITE, 3.0);
        pool.put(Material.DRIPSTONE_BLOCK, 3.0);
        pool.put(Material.SMOOTH_BASALT, 3.0);
        // Ores — very low weight
        pool.put(Material.COAL_ORE, 2.0);
        pool.put(Material.IRON_ORE, 1.5);
        pool.put(Material.COPPER_ORE, 1.5);
        pool.put(Material.GOLD_ORE, 0.5);
        pool.put(Material.REDSTONE_ORE, 0.5);
        pool.put(Material.LAPIS_ORE, 0.4);
        pool.put(Material.DIAMOND_ORE, 0.1);
        pool.put(Material.EMERALD_ORE, 0.05);
        // Deepslate ore variants
        pool.put(Material.DEEPSLATE_COAL_ORE, 1.5);
        pool.put(Material.DEEPSLATE_IRON_ORE, 1.0);
        pool.put(Material.DEEPSLATE_COPPER_ORE, 1.0);
        pool.put(Material.DEEPSLATE_GOLD_ORE, 0.3);
        pool.put(Material.DEEPSLATE_REDSTONE_ORE, 0.3);
        pool.put(Material.DEEPSLATE_LAPIS_ORE, 0.25);
        pool.put(Material.DEEPSLATE_DIAMOND_ORE, 0.05);
        pool.put(Material.DEEPSLATE_EMERALD_ORE, 0.02);
        return pool;
    }
}