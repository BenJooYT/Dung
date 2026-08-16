package com.lieyabull.dung.room;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;

/**
 * Stamps a {@link RoomTemplate}'s serialized block structure into a world at a given origin and
 * resolves template-relative geometry (connections, spawn floors, markers) to world coordinates.
 * This is the single instantiation path shared by the dungeon generator, the validator, and the
 * test mode, so all three exercise identical placement.
 */
public final class RoomInstantiator {

    private RoomInstantiator() {}

    /** Stamp all template blocks into `world` with origin block (ox, oy, oz). Never silently skips a
     *  bad block - an unparseable block state throws so a corrupt asset can never produce a partial room. */
    public static void instantiate(World world, RoomTemplate tpl, int ox, int oy, int oz) {
        for (RoomBlock b : tpl.blocks) {
            int wx = ox + b.x, wy = oy + b.y, wz = oz + b.z;
            BlockData data;
            try {
                data = Bukkit.createBlockData(b.b);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("room '" + tpl.id + "' has unparseable block data at "
                        + b.x + "," + b.y + "," + b.z + ": '" + b.b + "'", e);
            }
            world.getBlockAt(wx, wy, wz).setBlockData(data, false);
        }
    }

    public static Location toWorld(World world, RoomTemplate tpl, int ox, int oy, int oz, int x, int y, int z) {
        return new Location(world, ox + x + 0.5, oy + y, oz + z + 0.5);
    }

    public static Location connectorWorld(World world, RoomTemplate tpl, int ox, int oy, int oz, RoomConnector c) {
        return new Location(world, ox + c.x + 0.5, oy + c.y, oz + c.z + 0.5);
    }

    /** World-space min/max for a spawn floor (inclusive block corners). */
    public static Location[] spawnFloorWorld(World world, RoomTemplate tpl, int ox, int oy, int oz, SpawnFloor s) {
        Location min = new Location(world, ox + s.minX, oy + s.minY, oz + s.minZ);
        Location max = new Location(world, ox + s.maxX, oy + s.maxY, oz + s.maxZ);
        return new Location[] { min, max };
    }

    /** All markers of a type resolved to world positions (point centers). */
    public static List<Location> markersWorld(World world, RoomTemplate tpl, int ox, int oy, int oz, RoomMarkerType type) {
        List<Location> out = new ArrayList<>();
        for (RoomMarker m : tpl.markers) {
            if (m.type == type) {
                if (m.isPoint()) {
                    out.add(new Location(world, ox + m.x + 0.5, oy + m.y, oz + m.z + 0.5));
                } else {
                    out.add(new Location(world, ox + m.x + 0.5, oy + m.y, oz + m.z + 0.5));
                }
            }
        }
        return out;
    }

    /** The player spawn marker location, or the template's first spawn marker, or null. */
    public static Location playerSpawn(World world, RoomTemplate tpl, int ox, int oy, int oz) {
        List<Location> s = markersWorld(world, tpl, ox, oy, oz, RoomMarkerType.PLAYER_SPAWN);
        return s.isEmpty() ? null : s.get(0);
    }
}