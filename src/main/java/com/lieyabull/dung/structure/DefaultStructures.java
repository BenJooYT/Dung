package com.lieyabull.dung.structure;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

import com.lieyabull.dung.room.RoomBounds;
import com.lieyabull.dung.room.RoomMarker;
import com.lieyabull.dung.room.RoomMarkerType;
import com.lieyabull.dung.room.SpawnFloor;

/**
 * Ships a small built-in structure library so the game works on a fresh install with no hand-authored
 * rooms: sealed box rooms per room type, built directly into WorldEdit {@link Clipboard}s and paired
 * with matching {@link StructureDefinition} metadata. The generator carves doorways/corridors
 * procedurally on the shared corridor line, so rooms only open the door directions the floor graph
 * actually requires. These defaults are only registered when the user has supplied no structures of
 * their own, and any user-authored {@code <id>.schem + <id>.yml} take precedence.
 *
 * <p>Authoring stays unchanged: drop your own {@code .schem} + {@code .yml} into the structures
 * directory and they replace/extend these defaults.
 */
public final class DefaultStructures {

    public record DefaultStructure(StructureDefinition definition, Clipboard clipboard) {}

    private DefaultStructures() {}

    /** Interior width/depth (odd). Footprint = interior + 2 wall blocks. */
    private static final int INTERIOR = 13;
    /** Interior air blocks above the floor (matches procedural ROOM_HEIGHT semantics). */
    private static final int AIR_HEIGHT = 4;

    public static List<DefaultStructure> generate() {
        List<DefaultStructure> out = new ArrayList<>();
        out.add(box("start_room", List.of("START"), Material.SMOOTH_STONE, Material.POLISHED_DIORITE, Material.GLOWSTONE));
        out.add(box("combat_room", List.of("COMBAT"), Material.STONE_BRICKS, Material.POLISHED_ANDESITE, Material.GLOWSTONE));
        out.add(box("treasure_room", List.of("TREASURE"), Material.QUARTZ_BLOCK, Material.GOLD_BLOCK, Material.GLOWSTONE));
        out.add(box("shop_room", List.of("SHOP"), Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.GLOWSTONE));
        out.add(box("elite_room", List.of("ELITE"), Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS, Material.GLOWSTONE));
        out.add(box("boss_room", List.of("BOSS"), Material.DEEPSLATE_BRICKS, Material.POLISHED_BLACKSTONE_BRICKS, Material.SHROOMLIGHT));
        return out;
    }

    private static DefaultStructure box(String id, List<String> types, Material wall, Material floor, Material ceil) {
        int F = INTERIOR + 2;             // footprint including 1-block walls
        int maxY = AIR_HEIGHT + 1;        // ceiling row
        int mx = 1 + INTERIOR / 2;        // interior center x (wall index offset by 1)
        int mz = 1 + INTERIOR / 2;        // interior center z

        CuboidRegion region = new CuboidRegion(BlockVector3.at(0, 0, 0), BlockVector3.at(F - 1, maxY, F - 1));
        BlockArrayClipboard cb = new BlockArrayClipboard(region);
        cb.setOrigin(BlockVector3.at(0, 0, 0));

        BlockState wallS = adapt(wall), floorS = adapt(floor), ceilS = adapt(ceil), airS = adapt(Material.AIR);
        for (int x = 0; x < F; x++) {
            for (int y = 0; y <= maxY; y++) {
                for (int z = 0; z < F; z++) {
                    boolean perimeter = x == 0 || x == F - 1 || z == 0 || z == F - 1;
                    BlockState st;
                    if (y == 0) st = floorS;
                    else if (y == maxY) st = ceilS;
                    else st = perimeter ? wallS : airS;
                    cb.setBlock(BlockVector3.at(x, y, z), st);
                }
            }
        }

        StructureDefinition def = new StructureDefinition();
        def.id = id;
        def.types = types;
        def.schematic = "structure.schem";
        def.roomHeight = AIR_HEIGHT;
        def.entryHeight = 3;
        def.exitHeight = 3;
        def.bounds.add(new RoomBounds(0, 0, 0, F - 1, maxY, F - 1));
        def.spawnFloors.add(new SpawnFloor(2, 1, 2, INTERIOR - 1, 1, INTERIOR - 1));
        def.markers.add(new RoomMarker(RoomMarkerType.PLAYER_SPAWN, mx, 1, mz, "start"));
        return new DefaultStructure(def, cb);
    }

    private static BlockState adapt(Material m) {
        return BukkitAdapter.adapt(Bukkit.createBlockData(m));
    }
}