package com.lieyabull.dung.plot.potion;

import com.lieyabull.dung.Dung;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Random;

/**
 * Animates a potion's block transformation in waves.
 * <p>
 * Each wave is a group of blocks at the same Manhattan distance from the
 * impact point. The animation runs as a Bukkit scheduled task, sending
 * particles and transforming blocks wave by wave.
 */
public final class PotionAnimation {

    private static final int TICKS_PER_WAVE = 3; // 3 ticks (~150ms) between waves
    private static final int PARTICLE_COUNT = 5;

    private PotionAnimation() {}

    /**
     * Start animating a potion transformation.
     *
     * @param plugin     the plugin instance
     * @param result     the pre-computed propagation result
     * @param definition the potion definition (for particle/sound effects)
     * @param player     the player who threw the potion (for sound)
     */
    public static void animate(Dung plugin, PropagationResult result,
                               PotionDefinition definition, Player player) {
        if (!result.hasTransformed()) return;

        World world = result.origin().getWorld();
        if (world == null) return;

        List<List<Block>> waves = result.waves();
        Random rng = new Random();

        // Determine particle and sound based on potion type
        Particle particle = getParticle(definition);
        Sound sound = getSound(definition);
        float soundPitch = 1.0f;

        // Use a single runnable that steps through waves
        final int[] waveIndex = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (waveIndex[0] >= waves.size()) {
                    // Play final sound
                    world.playSound(result.origin(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.4f);
                    return;
                }

                List<Block> wave = waves.get(waveIndex[0]);

                // Transform all blocks in this wave
                for (Block block : wave) {
                    Material newMaterial = PropagationEngine.resolveTransformation(block, definition, rng);
                    if (newMaterial != block.getType()) {
                        block.setType(newMaterial, false);
                    }

                    // Spawn particles at each transformed block
                    Location loc = block.getLocation().add(0.5, 0.5, 0.5);
                    world.spawnParticle(particle, loc, PARTICLE_COUNT, 0.3, 0.3, 0.3, 0.02);
                }

                // Play sound for this wave
                world.playSound(result.origin(), sound, 0.5f, soundPitch);

                waveIndex[0]++;
            }
        }, 5L, TICKS_PER_WAVE);

        // Schedule task cancellation after all waves
        int totalTicks = 5 + waves.size() * TICKS_PER_WAVE + 1;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }, totalTicks);
    }

    private static Particle getParticle(PotionDefinition def) {
        return switch (def.id()) {
            case "forest" -> Particle.HAPPY_VILLAGER;
            case "stone" -> Particle.ASH;
            default -> Particle.INSTANT_EFFECT;
        };
    }

    private static Sound getSound(PotionDefinition def) {
        return switch (def.id()) {
            case "forest" -> Sound.BLOCK_GRASS_BREAK;
            case "stone" -> Sound.BLOCK_STONE_BREAK;
            default -> Sound.ENTITY_SPLASH_POTION_BREAK;
        };
    }
}