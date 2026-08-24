package com.lieyabull.dung.plot.potion;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link PropagationEngine}'s stateless helper methods.
 * <p>
 * PropagationEngine.resolveTransformation is pure logic that takes a Material
 * (via the Block's type) and a PotionDefinition, and returns a Material.
 * These tests verify the family-preserving behavior (log→log, leaves→leaves)
 * for the Forest Potion, and the full-pool behavior for the Stone Potion.
 */
class PropagationEngineTest {

    private static Block mockBlock(Material material) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.getLocation()).thenReturn(new Location(null, 0, 0, 0));
        return block;
    }

    // ==================== Forest Potion: family-preserving ====================

    @Test
    void forestPotionPreservesLogFamily() {
        Random rng = new Random(42);
        for (int i = 0; i < 200; i++) {
            String rolled = PropagationEngine.resolveTransformation(
                    mockBlock(Material.OAK_LOG),
                    PotionDefinition.FOREST, rng).name();
            assertTrue(rolled.endsWith("_LOG"),
                    "Forest potion should transform logs into logs, got: " + rolled);
        }
    }

    @Test
    void forestPotionPreservesLeavesFamily() {
        Random rng = new Random(42);
        for (int i = 0; i < 200; i++) {
            String rolled = PropagationEngine.resolveTransformation(
                    mockBlock(Material.OAK_LEAVES),
                    PotionDefinition.FOREST, rng).name();
            assertTrue(rolled.endsWith("_LEAVES"),
                    "Forest potion should transform leaves into leaves, got: " + rolled);
        }
    }

    @Test
    void forestPotionCanTransformToOtherLogTypes() {
        Random rng = new Random(42);
        boolean foundDifferent = false;
        for (int i = 0; i < 500; i++) {
            Material rolled = PropagationEngine.resolveTransformation(
                    mockBlock(Material.SPRUCE_LOG),
                    PotionDefinition.FOREST, rng);
            if (rolled != Material.SPRUCE_LOG) {
                foundDifferent = true;
                break;
            }
        }
        assertTrue(foundDifferent, "Forest potion should sometimes change log types");
    }

    @Test
    void forestPotionCanTransformToDifferentLeaves() {
        Random rng = new Random(42);
        boolean foundDifferent = false;
        for (int i = 0; i < 500; i++) {
            Material rolled = PropagationEngine.resolveTransformation(
                    mockBlock(Material.BIRCH_LEAVES),
                    PotionDefinition.FOREST, rng);
            if (rolled != Material.BIRCH_LEAVES) {
                foundDifferent = true;
                break;
            }
        }
        assertTrue(foundDifferent, "Forest potion should sometimes change leaf types");
    }

    // ==================== Stone Potion: full-pool ====================

    @Test
    void stonePotionReturnsPoolMaterials() {
        Random rng = new Random(42);
        var pool = PotionDefinition.STONE.transformationPool();
        for (int i = 0; i < 500; i++) {
            Material rolled = PropagationEngine.resolveTransformation(
                    mockBlock(Material.STONE),
                    PotionDefinition.STONE, rng);
            assertTrue(pool.containsKey(rolled),
                    "Stone potion should only return materials from its pool, got: " + rolled);
        }
    }

    @Test
    void stonePotionCanOreTransform() {
        Random rng = new Random(42);
        boolean foundOre = false;
        for (int i = 0; i < 10000; i++) {
            Material rolled = PropagationEngine.resolveTransformation(
                    mockBlock(Material.STONE),
                    PotionDefinition.STONE, rng);
            if (rolled.name().contains("ORE")) {
                foundOre = true;
                break;
            }
        }
        assertTrue(foundOre, "Stone potion should sometimes produce ores");
    }

    @Test
    void stonePotionCanStayStone() {
        Random rng = new Random(42);
        boolean stayedStone = false;
        for (int i = 0; i < 1000; i++) {
            Material rolled = PropagationEngine.resolveTransformation(
                    mockBlock(Material.STONE),
                    PotionDefinition.STONE, rng);
            if (rolled == Material.STONE) {
                stayedStone = true;
                break;
            }
        }
        assertTrue(stayedStone, "Stone potion should sometimes stay as stone");
    }

    // ==================== Default behavior ====================

    @Test
    void unknownPotionUsesOriginalDefinitionRoll() {
        // Create a simple water-to-ice definition
        PotionDefinition iceDef = new PotionDefinition("ice", "Ice Potion",
                java.util.Set.of(Material.WATER),
                java.util.Map.of(Material.ICE, 1.0),
                10, 10, 0.5);

        Random rng = new Random(42);
        Material rolled = PropagationEngine.resolveTransformation(
                mockBlock(Material.WATER),
                iceDef, rng);
        assertEquals(Material.ICE, rolled);
    }

    // ==================== PropagationResult ====================

    @Test
    void propagationResultNoTargets() {
        PropagationResult result = PropagationResult.noTargets(
                new Location(null, 0, 0, 0), "forest");
        assertTrue(result.noValidTargets());
        assertFalse(result.hasTransformed());
        assertEquals(0, result.totalTransformed());
    }

    @Test
    void propagationResultWithWaves() {
        java.util.List<java.util.List<Block>> waves = java.util.List.of(
                java.util.List.of(mockBlock(Material.OAK_LOG))
        );
        PropagationResult result = new PropagationResult(
                new Location(null, 0, 0, 0),
                waves, 1, false, "forest");
        assertFalse(result.noValidTargets());
        assertTrue(result.hasTransformed());
        assertEquals(1, result.totalTransformed());
        assertEquals("forest", result.potionId());
    }
}