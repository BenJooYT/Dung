package com.lieyabull.dung.dungeon;

import org.bukkit.Material;

import java.util.Random;

/** Floor themes that tune graph parameters, room shapes, and the normal-room palette. */
public enum Archetype {
    RUINS("ruins", "Ruins", 3, 1, 0, 1, RoomShape.L_SHAPED,
            Material.STONE_BRICKS, Material.COBBLESTONE, Material.GLOWSTONE),
    CAVERNS("caverns", "Caverns", 2, 1, 0, 1, RoomShape.CAVE,
            Material.DEEPSLATE, Material.STONE, Material.GLOWSTONE),
    TEMPLE("temple", "Temple", 2, 0, 0, 1, RoomShape.CROSS,
            Material.SMOOTH_QUARTZ, Material.POLISHED_ANDESITE, Material.SEA_LANTERN),
    OVERGROWN("overgrown", "Overgrown", 3, 1, 0, 0, RoomShape.CAVE,
            Material.MOSSY_STONE_BRICKS, Material.MOSS_BLOCK, Material.GLOWSTONE),
    INFERNO("inferno", "Inferno", 3, 1, 0, 0, RoomShape.ARENA,
            Material.NETHER_BRICKS, Material.BLACKSTONE, Material.SHROOMLIGHT),
    CRYPT("crypt", "Crypt", 2, 0, 0, 1, RoomShape.RING,
            Material.DEEPSLATE_BRICKS, Material.POLISHED_BLACKSTONE_BRICKS, Material.GLOWSTONE);

    private final String id;
    private final String displayName;
    private final int maxBranches;
    private final int targetLoops;
    private final int minLoops;
    private final int minBossDistance;
    private final RoomShape roomShape;
    private final Material wallMat;
    private final Material floorMat;
    private final Material lightMat;

    Archetype(String id, String displayName, int maxBranches, int targetLoops, int minLoops,
            int minBossDistance, RoomShape roomShape,
            Material wallMat, Material floorMat, Material lightMat) {
        this.id = id;
        this.displayName = displayName;
        this.maxBranches = maxBranches;
        this.targetLoops = targetLoops;
        this.minLoops = minLoops;
        this.minBossDistance = minBossDistance;
        this.roomShape = roomShape;
        this.wallMat = wallMat;
        this.floorMat = floorMat;
        this.lightMat = lightMat;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int maxBranches() {
        return maxBranches;
    }

    public int targetLoops() {
        return targetLoops;
    }

    public int minLoops() {
        return minLoops;
    }

    public int minBossDistance() {
        return minBossDistance;
    }

    public RoomShape roomShape() {
        return roomShape;
    }

    public Material wallMat() {
        return wallMat;
    }

    public Material floorMat() {
        return floorMat;
    }

    public Material lightMat() {
        return lightMat;
    }

    /** Resolve a config value: an archetype id, "random", or "none"/"classic". */
    public static Archetype pick(String value, Random rng) {
        if (value == null) return null;
        String key = value.trim().toLowerCase();
        if (key.isEmpty() || key.equals("none") || key.equals("classic")) return null;
        if (key.equals("random")) {
            Archetype[] all = values();
            return all[rng.nextInt(all.length)];
        }
        for (Archetype a : values()) {
            if (a.id.equals(key)) return a;
        }
        return null;
    }
}
