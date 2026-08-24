package com.lieyabull.dung.plot.potion;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PotionDefinition}.
 */
class PotionDefinitionTest {

    @Test
    void forestDefinitionHasTargets() {
        assertEquals("forest", PotionDefinition.FOREST.id());
        assertEquals("§aForest Transmutation Elixir", PotionDefinition.FOREST.displayName());
        assertFalse(PotionDefinition.FOREST.targetMaterials().isEmpty());
        assertFalse(PotionDefinition.FOREST.transformationPool().isEmpty());
        assertEquals(12, PotionDefinition.FOREST.maxRange());
        assertEquals(64, PotionDefinition.FOREST.maxTransformedBlocks());
        assertEquals(0.65, PotionDefinition.FOREST.spreadProbability(), 0.001);
    }

    @Test
    void stoneDefinitionHasTargets() {
        assertEquals("stone", PotionDefinition.STONE.id());
        assertEquals("§7Stone Transmutation Elixir", PotionDefinition.STONE.displayName());
        assertFalse(PotionDefinition.STONE.targetMaterials().isEmpty());
        assertFalse(PotionDefinition.STONE.transformationPool().isEmpty());
        assertEquals(10, PotionDefinition.STONE.maxRange());
        assertEquals(48, PotionDefinition.STONE.maxTransformedBlocks());
        assertEquals(0.55, PotionDefinition.STONE.spreadProbability(), 0.001);
    }

    @Test
    void forestIsTargetLogsAndLeaves() {
        // Should target all log and leaf types
        assertTrue(PotionDefinition.FOREST.isTarget(org.bukkit.Material.OAK_LOG));
        assertTrue(PotionDefinition.FOREST.isTarget(org.bukkit.Material.SPRUCE_LOG));
        assertTrue(PotionDefinition.FOREST.isTarget(org.bukkit.Material.OAK_LEAVES));
        assertTrue(PotionDefinition.FOREST.isTarget(org.bukkit.Material.CHERRY_LEAVES));
        // Should NOT target other blocks
        assertFalse(PotionDefinition.FOREST.isTarget(org.bukkit.Material.STONE));
        assertFalse(PotionDefinition.FOREST.isTarget(org.bukkit.Material.DIRT));
    }

    @Test
    void stoneIsTargetStoneVariants() {
        assertTrue(PotionDefinition.STONE.isTarget(org.bukkit.Material.STONE));
        assertTrue(PotionDefinition.STONE.isTarget(org.bukkit.Material.COBBLESTONE));
        assertTrue(PotionDefinition.STONE.isTarget(org.bukkit.Material.DEEPSLATE));
        assertTrue(PotionDefinition.STONE.isTarget(org.bukkit.Material.TUFF));
        // Should NOT target non-stone blocks
        assertFalse(PotionDefinition.STONE.isTarget(org.bukkit.Material.OAK_LOG));
        assertFalse(PotionDefinition.STONE.isTarget(org.bukkit.Material.DIRT));
    }

    @Test
    void forestRollTransformationNeverNull() {
        Random rng = new Random(42);
        for (int i = 0; i < 100; i++) {
            assertNotNull(PotionDefinition.FOREST.rollTransformation(rng));
        }
    }

    @Test
    void stoneRollTransformationNeverNull() {
        Random rng = new Random(42);
        for (int i = 0; i < 100; i++) {
            assertNotNull(PotionDefinition.STONE.rollTransformation(rng));
        }
    }

    @Test
    void forestRollTransformationReturnsPoolMaterialsOnly() {
        Random rng = new Random(42);
        var pool = PotionDefinition.FOREST.transformationPool();
        for (int i = 0; i < 1000; i++) {
            org.bukkit.Material mat = PotionDefinition.FOREST.rollTransformation(rng);
            assertTrue(pool.containsKey(mat),
                    "Rolled material " + mat + " is not in the pool");
        }
    }

    @Test
    void stoneRollTransformationReturnsOresRarely() {
        Random rng = new Random(42);
        boolean foundOre = false;
        for (int i = 0; i < 10000; i++) {
            org.bukkit.Material mat = PotionDefinition.STONE.rollTransformation(rng);
            String name = mat.name();
            if (name.contains("ORE")) {
                foundOre = true;
                break;
            }
        }
        assertTrue(foundOre, "Ore materials should appear in 10000 rolls");
    }

    @Test
    void definitionEqualityById() {
        PotionDefinition def1 = new PotionDefinition("test", "Test",
                java.util.Set.of(org.bukkit.Material.STONE),
                java.util.Map.of(org.bukkit.Material.STONE, 1.0),
                10, 10, 0.5);
        PotionDefinition def2 = new PotionDefinition("test", "Different Name",
                java.util.Set.of(org.bukkit.Material.DIRT),
                java.util.Map.of(org.bukkit.Material.DIRT, 1.0),
                5, 5, 0.3);
        assertEquals(def1, def2);
        assertEquals(def1.hashCode(), def2.hashCode());
    }

    @Test
    void definitionEqualityDifferentIds() {
        PotionDefinition def1 = new PotionDefinition("a", "A",
                java.util.Set.of(org.bukkit.Material.STONE),
                java.util.Map.of(org.bukkit.Material.STONE, 1.0),
                10, 10, 0.5);
        PotionDefinition def2 = new PotionDefinition("b", "A",
                java.util.Set.of(org.bukkit.Material.STONE),
                java.util.Map.of(org.bukkit.Material.STONE, 1.0),
                10, 10, 0.5);
        assertNotEquals(def1, def2);
    }

    @Test
    void transformPoolReturnsNullForEmptyPool() {
        PotionDefinition empty = new PotionDefinition("empty", "Empty",
                java.util.Set.of(org.bukkit.Material.STONE),
                java.util.Map.of(),
                10, 10, 0.5);
        assertNull(empty.rollTransformation(new Random()));
    }
}