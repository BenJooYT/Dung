package com.lieyabull.dung.structure;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The thin WorldEdit integration layer. Dung never parses a schematic itself and never runs
 * WorldEdit commands; it drives WorldEdit's clipboard / schematic / EditSession APIs directly.
 *
 * <ul>
 *   <li>{@link #load} reads a {@code .schem} file into a {@link Clipboard} (Sponge format).</li>
 *   <li>{@link #blockLookup} exposes the clipboard's solid/air occupancy for validation.</li>
 *   <li>{@link #paste} pastes a clipboard into a world at an origin with a clockwise rotation
 *       (0..3 = 0&deg;/90&deg;/180&deg;/270&deg;). Air blocks are pasted too, so a room's carved
 *       doorways are produced by its own schematic (matching the author's build), and the
 *       generator validates the target footprint is clear before pasting.</li>
 * </ul>
 *
 * <p>This class needs a live WorldEdit, so it is deliberately thin and not unit-tested; the
 * rotation/metadata math it depends on lives in {@link StructureTransform} and is fully tested.
 */
public final class StructureWorldEdit {

    private StructureWorldEdit() {}

    /** Load a {@code .schem} file into a clipboard, or null if the format is unsupported/unreadable. */
    public static Clipboard load(File file) {
        ClipboardFormat fmt = ClipboardFormats.findByFile(file);
        if (fmt == null) return null;
        try (InputStream is = new FileInputStream(file)) {
            ClipboardReader reader = fmt.getReader(is);
            return reader.read();
        } catch (IOException e) {
            return null;
        }
    }

    /** Save a clipboard to a {@code .schem} file (Sponge format). Returns false on failure. */
    public static boolean save(Clipboard clipboard, File file) {
        ClipboardFormat fmt = ClipboardFormats.findByAlias("schem");
        if (fmt == null) return false;
        try (ClipboardWriter writer = fmt.getWriter(new FileOutputStream(file))) {
            writer.write(clipboard);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * A {@link BlockLookup} over a clipboard using structure-relative coordinates (a block at
     * structure (x, y, z) is at clipboard minimum corner + (x, y, z)). Solid = anything that is
     * not air.
     */
    public static BlockLookup blockLookup(Clipboard clipboard) {
        BlockVector3 min = clipboard.getMinimumPoint();
        return (x, y, z) -> {
            BlockState st = clipboard.getBlock(min.add(x, y, z));
            return st != null && st.getBlockType() != BlockTypes.AIR;
        };
    }

    /**
     * Paste a clipboard into {@code world} with the structure's (0,0,0) — i.e. the clipboard's minimum
     * corner — at (ox, oy, oz), rotated by {@code rotationSteps} (0..3) clockwise. Returns true on
     * success. The paste anchor is adjusted by the clipboard's origin so an author schematic whose
     * stored origin differs from its minimum corner still lands structure (0,0,0) exactly on
     * (ox, oy, oz).
     */
    public static boolean paste(World world, Clipboard clipboard, int ox, int oy, int oz, int rotationSteps) {
        try (EditSession session = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world))
                .build()) {
            ClipboardHolder holder = new ClipboardHolder(clipboard);
            if (rotationSteps != 0) {
                holder.setTransform(new AffineTransform().rotateY(90.0 * rotationSteps));
            }
            BlockVector3 origin = clipboard.getOrigin();
            BlockVector3 min = clipboard.getMinimumPoint();
            // The transform rotates each clipboard-local position around (0,0,0); the paste then
            // translates so the clipboard origin lands on `to`. To make structure (0,0,0) (the min
            // corner) land on (ox,oy,oz) we must therefore anchor `to` at the rotated min offset:
            //   world(min) = to + R(min) - origin  ==  (ox,oy,oz)
            int[] rm = rotatePoint(min.getBlockX(), min.getBlockZ(), rotationSteps);
            BlockVector3 to = BlockVector3.at(
                    ox - rm[0] + origin.getBlockX(),
                    oy - min.getBlockY() + origin.getBlockY(),
                    oz - rm[1] + origin.getBlockZ());
            Operation op = holder.createPaste(session)
                    .to(to)
                    .copyEntities(false)
                    .build();
            Operations.complete(op);
            session.commit();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Rotate a (x,z) point clockwise by `steps` 90-degree turns around the origin — the same mapping
     *  `AffineTransform.rotateY` applies to clipboard-local positions. */
    private static int[] rotatePoint(int x, int z, int steps) {
        switch (steps % 4) {
            case 1: return new int[]{z, -x};
            case 2: return new int[]{-x, -z};
            case 3: return new int[]{-z, x};
            default: return new int[]{x, z};
        }
    }
}