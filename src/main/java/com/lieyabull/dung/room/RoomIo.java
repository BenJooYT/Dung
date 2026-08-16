package com.lieyabull.dung.room;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Deterministic JSON serialization for {@link RoomTemplate}. Output is pretty-printed, blocks are
 * emitted in a fixed (x, z, y) order so re-serialization is byte-stable and Git-diffs stay minimal,
 * and on load everything is normalized (bounds clamped/normalized, blocks sorted, ids trimmed).
 */
public final class RoomIo {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RoomIo() {}

    public static String toJson(RoomTemplate tpl) {
        normalize(tpl);
        return GSON.toJson(tpl);
    }

    public static RoomTemplate fromJson(String json) {
        RoomTemplate tpl = GSON.fromJson(json, RoomTemplate.class);
        if (tpl == null) throw new IllegalArgumentException("room JSON parsed to null");
        normalize(tpl);
        return tpl;
    }

    /** Ensure a template is internally consistent (id trim, bounds normalized, blocks sorted). */
    public static void normalize(RoomTemplate tpl) {
        if (tpl.id == null) tpl.id = "";
        tpl.id = tpl.id.trim().toLowerCase();
        if (tpl.types == null) tpl.types = new java.util.ArrayList<>();
        if (tpl.bounds == null) tpl.bounds = new java.util.ArrayList<>();
        if (tpl.connectors == null) tpl.connectors = new java.util.ArrayList<>();
        if (tpl.spawnFloors == null) tpl.spawnFloors = new java.util.ArrayList<>();
        if (tpl.markers == null) tpl.markers = new java.util.ArrayList<>();
        if (tpl.blocks == null) tpl.blocks = new java.util.ArrayList<>();

        for (RoomBounds b : tpl.bounds) b.normalize();
        for (SpawnFloor s : tpl.spawnFloors) s.normalize();
        if (tpl.markers != null) for (RoomMarker m : tpl.markers) if (m.region != null) m.region.normalize();
        tpl.blocks.sort((a, b) -> {
            int c = Integer.compare(a.x, b.x);
            if (c != 0) return c;
            c = Integer.compare(a.z, b.z);
            if (c != 0) return c;
            return Integer.compare(a.y, b.y);
        });
    }
}