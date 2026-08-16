package com.lieyabull.dung.room;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-player room-editing session. Holds the in-progress {@link RoomTemplate}, the WorldEdit-like
 * two-corner selection, and helpers to capture block structure and gameplay metadata into
 * template-relative coordinates. The template origin maps to the min corner of the first bound
 * region the author defines; every other coordinate is stored relative to it.
 */
public final class RoomEditSession {
    private final java.util.UUID player;
    private RoomTemplate tpl;
    private int[] pos1, pos2;
    /** World block at which template (0,0,0) sits; null until the first region is captured. */
    private int[] origin;

    public RoomEditSession(java.util.UUID player) {
        this.player = player;
    }

    public java.util.UUID player() { return player; }
    public RoomTemplate template() { return tpl; }

    public void start(String id, List<String> types) {
        tpl = new RoomTemplate();
        tpl.id = id;
        if (types != null) tpl.types.addAll(types);
        tpl.validated = false;
        pos1 = pos2 = null;
        origin = null;
    }

    // ---------- selection ----------

    public void setPos1(int x, int y, int z) { pos1 = new int[]{x, y, z}; }
    public void setPos2(int x, int y, int z) { pos2 = new int[]{x, y, z}; }
    public boolean hasSelection() { return pos1 != null && pos2 != null; }

    public int[] selMin() {
        return new int[]{Math.min(pos1[0], pos2[0]), Math.min(pos1[1], pos2[1]), Math.min(pos1[2], pos2[2])};
    }
    public int[] selMax() {
        return new int[]{Math.max(pos1[0], pos2[0]), Math.max(pos1[1], pos2[1]), Math.max(pos1[2], pos2[2])};
    }

    // ---------- template-relative conversion ----------

    private int[] rel(int wx, int wy, int wz) {
        if (origin == null) throw new IllegalStateException("no template origin yet - add a bound region first");
        return new int[]{wx - origin[0], wy - origin[1], wz - origin[2]};
    }

    private void ensureOrigin(int[] selMin) {
        if (origin == null) origin = selMin.clone();
    }

    // ---------- content capture ----------

    public void addRegionFromSelection() {
        if (!hasSelection() || tpl == null) return;
        int[] mn = selMin(), mx = selMax();
        ensureOrigin(mn);
        tpl.bounds.add(new RoomBounds(rel(mn[0], mn[1], mn[2])[0], rel(mn[0], mn[1], mn[2])[1], rel(mn[0], mn[1], mn[2])[2],
                                      rel(mx[0], mx[1], mx[2])[0], rel(mx[0], mx[1], mx[2])[1], rel(mx[0], mx[1], mx[2])[2]));
    }

    public void addSpawnFloorFromSelection() {
        if (!hasSelection() || tpl == null) return;
        int[] mn = selMin(), mx = selMax();
        ensureOrigin(mn);
        tpl.spawnFloors.add(new SpawnFloor(rel(mn[0], mn[1], mn[2])[0], rel(mn[0], mn[1], mn[2])[1], rel(mn[0], mn[1], mn[2])[2],
                                           rel(mx[0], mx[1], mx[2])[0], rel(mx[0], mx[1], mx[2])[1], rel(mx[0], mx[1], mx[2])[2]));
    }

    public void addMarker(RoomMarkerType type, int wx, int wy, int wz, String name) {
        if (tpl == null || origin == null) return;
        int[] a = rel(wx, wy, wz);
        tpl.markers.add(new RoomMarker(type, a[0], a[1], a[2], name));
    }

    public void setPlayerSpawn(int wx, int wy, int wz) {
        addMarker(RoomMarkerType.PLAYER_SPAWN, wx, wy, wz, "player_spawn");
    }

    public void setShopkeeper(int wx, int wy, int wz) {
        addMarker(RoomMarkerType.SHOPKEEPER, wx, wy, wz, "shopkeeper");
    }

    /** Capture the actual block structure of every bound region from the world. */
    public void captureBlocks(World world) {
        if (tpl == null || origin == null) return;
        tpl.blocks.clear();
        List<RoomBlock> captured = new ArrayList<>();
        for (RoomBounds b : tpl.bounds) {
            for (int rx = b.minX; rx <= b.maxX; rx++) {
                for (int rz = b.minZ; rz <= b.maxZ; rz++) {
                    for (int ry = b.minY; ry <= b.maxY; ry++) {
                        int wx = origin[0] + rx, wy = origin[1] + ry, wz = origin[2] + rz;
                        BlockData data = world.getBlockAt(wx, wy, wz).getBlockData();
                        captured.add(new RoomBlock(rx, ry, rz, data.getAsString()));
                    }
                }
            }
        }
        tpl.blocks = captured;
    }

    /** Clear all authored content, keep the session's player. */
    public void reset() {
        tpl = null;
        pos1 = pos2 = null;
        origin = null;
    }

    // The connector anchor is taken from the player's feet location; the command passes it in.
    public void addConnector(Direction dir, RoomConnType type, int wx, int wy, int wz, int width, int height, int clearance) {
        if (tpl == null || origin == null) return;
        int[] a = rel(wx, wy, wz);
        tpl.connectors.add(new RoomConnector(dir, type, a[0], a[1], a[2], width, height, a[1] - 1, clearance));
    }
}