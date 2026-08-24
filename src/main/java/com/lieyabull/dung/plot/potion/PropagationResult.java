package com.lieyabull.dung.plot.potion;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * The result of a potion propagation operation.
 * Contains the pre-computed wave data for animation.
 */
public final class PropagationResult {

    private final Location origin;
    private final List<List<Block>> waves; // blocks to transform in each wave (outer = by distance)
    private final int totalTransformed;
    private final boolean noValidTargets;
    private final String potionId;

    public PropagationResult(Location origin, List<List<Block>> waves,
                             int totalTransformed, boolean noValidTargets,
                             String potionId) {
        this.origin = origin.clone();
        this.waves = waves;
        this.totalTransformed = totalTransformed;
        this.noValidTargets = noValidTargets;
        this.potionId = potionId;
    }

    /** Create a "no valid targets" result. */
    public static PropagationResult noTargets(Location origin, String potionId) {
        return new PropagationResult(origin, List.of(), 0, true, potionId);
    }

    public Location origin() { return origin.clone(); }
    public List<List<Block>> waves() { return waves; }
    public int totalTransformed() { return totalTransformed; }
    public boolean noValidTargets() { return noValidTargets; }
    public String potionId() { return potionId; }
    public boolean hasTransformed() { return totalTransformed > 0; }
}