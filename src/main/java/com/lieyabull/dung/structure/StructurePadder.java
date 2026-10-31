package com.lieyabull.dung.structure;

import com.lieyabull.dung.dungeon.RoomGen;
import com.lieyabull.dung.room.RoomBounds;
import com.lieyabull.dung.room.RoomMarker;
import com.lieyabull.dung.room.SpawnFloor;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * Fits an authored structure into the procedural long-room frame so the generator's fixed corridor
 * line ({@link RoomGen#PERP_CENTER}) actually runs through the room. A room narrower than the lane
 * (or one whose lane falls on its wall) would otherwise get its doorway carved outside the build.
 * The original room is centred in a solid square frame and each direction gets a short entry tunnel
 * behind the wall the generator opens.
 */
final class StructurePadder {
    private StructurePadder() {}

    record Result(StructureDefinition definition, Clipboard clipboard, boolean padded) {}

    static boolean needsPadding(StructureDefinition def) {
        return def != null && needsPadding(def.total());
    }

    /** True when the 3-wide fixed lane does not fit fully inside the room's perpendicular interior. */
    static boolean needsPadding(RoomBounds total) {
        return !laneFits(total.width()) || !laneFits(total.depth());
    }

    private static boolean laneFits(int size) {
        return RoomGen.PERP_CENTER - 1 >= 1
                && RoomGen.PERP_CENTER + 1 <= size - 2;
    }

    static Result fit(StructureDefinition def, Clipboard clipboard) {
        if (!needsPadding(def)) return new Result(def, clipboard, false);

        BlockVector3 min = clipboard.getMinimumPoint();
        BlockVector3 max = clipboard.getMaximumPoint();
        int width = max.x() - min.x() + 1;
        int height = max.y() - min.y() + 1;
        int depth = max.z() - min.z() + 1;
        int target = Math.max(RoomGen.LONG + 2 * RoomGen.WALL, Math.max(width, depth));
        int offX = (target - width) / 2;
        int offZ = (target - depth) / 2;

        RoomBounds bounds = new RoomBounds(0, 0, 0, width - 1, height - 1, depth - 1);
        int[] preferred = {-1, -1, -1, -1};
        if (def.doorsConfigured) {
            for (int d = 0; d < 4; d++) preferred[d] = def.doorFloorY(d);
        }
        StructureAuthoring.DoorLanePlan plan = StructureAuthoring.planFixedLineDoors(
                bounds, StructureWorldEdit.blockLookup(clipboard), preferred,
                RoomGen.PERP_CENTER - offX, RoomGen.PERP_CENTER - offZ);
        if (!plan.ok()) {
            throw new IllegalStateException("no walkable " + plan.failedDirectionName()
                    + " lane on the fixed corridor line (" + plan.reason() + ")");
        }

        StructureDefinition out = copy(def);
        out.doorsConfigured = true;
        out.doors = plan.doors();
        out.bounds = new ArrayList<>();
        out.bounds.add(new RoomBounds(0, 0, 0, target - 1, height - 1, target - 1));
        // Only the 19-block long-room frame has PERP_CENTER as its exact centre; rotating a larger
        // room would move the fixed line off the lane planned above.
        if (target != RoomGen.LONG + 2 * RoomGen.WALL) out.allowedRotations = List.of(0);
        shiftContent(out, offX, offZ);

        Clipboard padded = padClipboard(clipboard, target, offX, offZ, plan.doors(),
                width, height, depth);
        return new Result(out, padded, true);
    }

    private static StructureDefinition copy(StructureDefinition def) {
        // rotate(NONE) is a complete deep copy, including every door spec and coordinate list.
        return StructureTransform.rotate(def, StructureTransform.Rotation.NONE);
    }

    private static void shiftContent(StructureDefinition def, int offX, int offZ) {
        for (SpawnFloor f : def.spawnFloors) {
            f.minX += offX;
            f.maxX += offX;
            f.minZ += offZ;
            f.maxZ += offZ;
        }
        for (RoomMarker m : def.markers) {
            m.x += offX;
            m.z += offZ;
            if (m.region != null) {
                m.region.minX += offX;
                m.region.maxX += offX;
                m.region.minZ += offZ;
                m.region.maxZ += offZ;
            }
        }
    }

    /** Centre the original content in a solid square frame and cut a 3-wide entry tunnel from each
     *  padded wall to the original room's interior, behind the wall the generator will open. */
    private static Clipboard padClipboard(Clipboard original, int target, int offX, int offZ,
                                          StructureDefinition.DoorSpec[] doors,
                                          int width, int height, int depth) {
        BlockVector3 min = original.getMinimumPoint();
        BlockArrayClipboard padded = new BlockArrayClipboard(new CuboidRegion(
                BlockVector3.at(0, 0, 0), BlockVector3.at(target - 1, height - 1, target - 1)));

        BlockState floor = sample(original, min, width / 2, 0, depth / 2);
        BlockState ceiling = sample(original, min, width / 2, height - 1, depth / 2);
        BlockState wall = sample(original, min, 0, height / 2, depth / 2);
        for (int x = 0; x < target; x++) {
            for (int z = 0; z < target; z++) {
                padded.setBlock(BlockVector3.at(x, 0, z), floor);
                padded.setBlock(BlockVector3.at(x, height - 1, z), ceiling);
                for (int y = 1; y < height - 1; y++) {
                    padded.setBlock(BlockVector3.at(x, y, z), wall);
                }
            }
        }
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    // getFullBlock keeps tile-entity NBT (sign text, chest contents, ...).
                    padded.setBlock(BlockVector3.at(x + offX, y, z + offZ),
                            original.getFullBlock(min.add(x, y, z)));
                }
            }
        }

        for (int d = 0; d < 4; d++) {
            StructureDefinition.DoorSpec spec = doors[d];
            if (spec != null) {
                carveEntryTunnel(padded, d, spec.floorY, spec.height, offX, offZ,
                        width, depth, target, floor);
            }
        }
        padded.setOrigin(BlockVector3.at(0, 0, 0));
        return padded;
    }

    private static BlockState sample(Clipboard clipboard, BlockVector3 min, int x, int y, int z) {
        BlockState state = clipboard.getBlock(min.add(x, y, z));
        return state == null || state.getBlockType() == BlockTypes.AIR
                ? BlockTypes.STONE_BRICKS.getDefaultState() : state;
    }

    private static void carveEntryTunnel(BlockArrayClipboard padded, int direction,
                                         int floorY, int air, int offX, int offZ,
                                         int width, int depth, int target, BlockState floor) {
        int perp = RoomGen.PERP_CENTER;
        int depthLo;
        int depthHi;
        switch (direction) {
            case 0 -> { depthLo = 1; depthHi = offZ; }
            case 1 -> { depthLo = offX + width - 1; depthHi = target - 2; }
            case 2 -> { depthLo = offZ + depth - 1; depthHi = target - 2; }
            default -> { depthLo = 1; depthHi = offX; }
        }
        if (depthLo > depthHi) return;
        for (int d = depthLo; d <= depthHi; d++) {
            for (int along = perp - 1; along <= perp + 1; along++) {
                int x = direction == 0 || direction == 2 ? along : d;
                int z = direction == 0 || direction == 2 ? d : along;
                padded.setBlock(BlockVector3.at(x, floorY, z), floor);
                for (int y = floorY + 1; y <= floorY + air; y++) {
                    padded.setBlock(BlockVector3.at(x, y, z), BlockTypes.AIR.getDefaultState());
                }
            }
        }
    }
}
