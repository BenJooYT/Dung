package com.lieyabull.dung.structure;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BaseBlock;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.enginehub.linbus.tree.LinListTag;
import org.enginehub.linbus.tree.LinStringTag;
import org.enginehub.linbus.tree.LinTagType;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads written signs out of a WorldEdit {@link Clipboard} so authors can place markers with in-world
 * signs instead of hand-writing metadata. WorldEdit schematics preserve sign text as tile-entity NBT;
 * for a clipboard the stored block is a {@link BaseBlock} whose NBT survives {@code toBaseBlock()}.
 * Each sign that holds a recognized marker becomes a {@link Sign} record (position + normalized text);
 * the author then uses those to populate markers / spawn floors, and the marker signs are removed from
 * the schematic at generation time so they never appear in the live room.
 */
public final class SignScanner {
    private SignScanner() {}

    /** A written sign found in a clipboard: its structure-relative position and its first text line. */
    public record Sign(int x, int y, int z, String text) {}

    /** Scan the whole clipboard for written signs and return their first text line (normalized). */
    public static List<Sign> scan(Clipboard clipboard) {
        List<Sign> out = new ArrayList<>();
        Region region = clipboard.getRegion();
        for (BlockVector3 pt : region) {
            BlockState bs = clipboard.getBlock(pt);
            if (bs == null) continue;
            String id = bs.getBlockType().getId();
            if (id == null || !id.endsWith("_sign")) continue;
            String text = firstLine(clipboard, pt, bs);
            if (text == null || text.isBlank()) continue;
            out.add(new Sign(pt.getBlockX(), pt.getBlockY(), pt.getBlockZ(), normalize(text)));
        }
        return out;
    }

    private static String firstLine(Clipboard clipboard, BlockVector3 pt, BlockState bs) {
        try {
            LinCompoundTag nbt = bs.toBaseBlock().getNbtReference().getValue();
            if (nbt == null) return null;
            LinCompoundTag front = nbt.getTag("front_text", LinTagType.compoundTag());
            if (front != null) {
                LinListTag<LinStringTag> messages = front.getListTag("messages", LinTagType.stringTag());
                if (messages != null) {
                    String msg = messages.get(0).value();
                    if (msg != null && !msg.isBlank()) return textFromComponent(msg);
                }
            }
            // Legacy pre-1.20 field layout.
            LinStringTag t1 = nbt.getTag("Text1", LinTagType.stringTag());
            if (t1 != null && !t1.value().isBlank()) return textFromComponent(t1.value());
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Strip a JSON text component ({@code {"text":"X"}} / {@code "X"}) down to its raw text. */
    private static String textFromComponent(String s) {
        String v = s.trim();
        int idx = v.indexOf("\"text\":");
        if (idx >= 0) {
            int start = v.indexOf('"', idx + 7);
            int end = v.indexOf('"', start + 1);
            if (start >= 0 && end > start) return v.substring(start + 1, end);
        }
        return v.replace("{", "").replace("}", "").replace("\"", "");
    }

    /** Uppercase, trimmed, and bracket-stripped marker text (e.g. {@code [PLAYER_SPAWN]} → {@code PLAYER_SPAWN}). */
    private static String normalize(String s) {
        String v = s.trim().toUpperCase();
        if (v.startsWith("[") && v.endsWith("]")) v = v.substring(1, v.length() - 1).trim();
        return v;
    }
}