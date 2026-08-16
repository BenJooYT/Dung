package com.lieyabull.dung.room;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.dungeon.RoomType;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Loads and serves production room templates bundled as JAR resources under {@code rooms/}.
 * An index file {@code rooms/index.txt} lists each asset file (one {@code <id>.json} per line) so
 * assets are enumerated deterministically without depending on runtime classpath scanning.
 *
 * Only templates that pass {@link RoomValidator} are registered - invalid assets are logged and
 * skipped, never silently used. This is the guarantee that a production template is safe to
 * instantiate.
 */
public final class RoomRegistry {
    private static final String ROOM_DIR = "rooms/";
    private static final String INDEX = ROOM_DIR + "index.txt";

    private final Dung plugin;
    private final Map<String, RoomTemplate> byId = new LinkedHashMap<>();

    public RoomRegistry(Dung plugin) {
        this.plugin = plugin;
        loadIndex();
    }

    private void loadIndex() {
        try {
            InputStream is = plugin.getResource(INDEX);
            if (is == null) {
                plugin.getLogger().info("[rooms] No room index at " + INDEX + " - no template rooms registered.");
                return;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                int loaded = 0, rejected = 0;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String file = line.endsWith(".json") ? line : line + ".json";
                    RoomTemplate tpl = loadAsset(ROOM_DIR + file);
                    if (tpl == null) { rejected++; continue; }
                    RoomValidator.Result res = RoomValidator.validate(tpl);
                    if (res.valid) {
                        byId.put(tpl.id, tpl);
                        loaded++;
                    } else {
                        rejected++;
                        plugin.getLogger().warning("[rooms] Rejected invalid room asset '" + file + "':");
                        for (RoomValidationIssue i : res.issues) plugin.getLogger().warning("  " + i);
                    }
                }
                plugin.getLogger().info("[rooms] Registered " + loaded + " validated room template(s)"
                        + (rejected > 0 ? ", rejected " + rejected + " invalid" : "") + ".");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[rooms] Failed to load room index: " + e.getMessage());
        }
    }

    private RoomTemplate loadAsset(String resource) {
        try {
            InputStream is = plugin.getResource(resource);
            if (is == null) {
                plugin.getLogger().warning("[rooms] Asset '" + resource + "' listed in index but missing.");
                return null;
            }
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            RoomTemplate tpl = RoomIo.fromJson(json);
            RoomIo.normalize(tpl);
            return tpl;
        } catch (Exception e) {
            plugin.getLogger().warning("[rooms] Failed to parse asset '" + resource + "': " + e.getMessage());
            return null;
        }
    }

    /** Programmatic registration (used by tests / author test-mode). Runs validation. */
    public boolean register(RoomTemplate tpl) {
        RoomValidator.Result res = RoomValidator.validate(tpl);
        if (res.valid) {
            byId.put(tpl.id, tpl);
            return true;
        }
        return false;
    }

    public RoomTemplate byId(String id) {
        return byId.get(id == null ? null : id.trim().toLowerCase());
    }

    public List<RoomTemplate> all() {
        return new ArrayList<>(byId.values());
    }

    /** Templates that may serve the given room type. */
    public List<RoomTemplate> forType(RoomType type) {
        List<RoomTemplate> out = new ArrayList<>();
        for (RoomTemplate t : byId.values()) {
            if (t.types.contains(type.name())) out.add(t);
        }
        return out;
    }

    /** Pick a random validated template for a room type, or null if none. */
    public RoomTemplate pick(RoomType type, Random rng) {
        List<RoomTemplate> pool = forType(type);
        if (pool.isEmpty()) return null;
        return pool.get(rng.nextInt(pool.size()));
    }
}