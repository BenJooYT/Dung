package com.lieyabull.dung.structure;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.dungeon.RoomType;
import com.lieyabull.dung.room.RoomValidationIssue;
import com.sk89q.worldedit.extent.clipboard.Clipboard;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The Dung structure library. Loads every metadata sidecar ({@code <id>.yml}, with its sibling
 * {@code <id>.schem}) from {@code plugins/Dung/structures/}, validates it (metadata + physical block
 * checks against the WorldEdit clipboard), and registers the validated ones under the schematic id.
 * On a fresh install with no user structures, it registers the built-in {@link DefaultStructures} so
 * the game is playable immediately; any user-authored structure overrides its default sibling. Only
 * validated structures are ever used by the dungeon generator.
 */
public final class StructureRegistry {
    private static final String STRUCTURES_DIR = "structures";

    /** A registered structure: its metadata plus its loaded WorldEdit clipboard. */
    public record Registered(StructureDefinition definition, Clipboard clipboard) {}

    private final Dung plugin;
    private final Map<String, Registered> byId = new LinkedHashMap<>();

    public StructureRegistry(Dung plugin) {
        this.plugin = plugin;
        load();
    }

    /** (Re)load all structures from disk, then fill any gaps with defaults. */
    public synchronized void load() {
        byId.clear();
        File dir = new File(plugin.getDataFolder(), STRUCTURES_DIR);
        if (!dir.exists()) dir.mkdirs();
        int loaded = scan(dir, byId);
        int defaultCount = fillDefaults(byId);
        plugin.getLogger().info("[structures] Registered " + byId.size()
                + " validated structure(s) (" + loaded + " from disk, " + defaultCount + " default).");
    }

    /** Recursively scan {@code dir} for metadata sidecars ({@code *.yml}) and register their structures.
     *  A room's id is its schematic name: the metadata file {@code <id>.yml} is paired with its sibling
     *  {@code <id>.schem} and registered under the basename {@code id}. */
    private int scan(File dir, Map<String, Registered> out) {
        int loaded = 0;
        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(dir.toPath())) {
            List<java.nio.file.Path> metas = walk
                    .filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .toList();
            for (java.nio.file.Path meta : metas) {
                Registered r = loadOne(meta.toFile());
                if (r == null) continue;
                if (out.containsKey(r.definition().id)) {
                    plugin.getLogger().warning("[structures] Duplicate structure id '" + r.definition().id
                            + "' skipped (" + meta.toAbsolutePath() + ").");
                    continue;
                }
                out.put(r.definition().id, r);
                loaded++;
            }
        } catch (IOException e) {
            plugin.getLogger().severe("[structures] Failed to scan " + dir + ": " + e.getMessage());
        }
        return loaded;
    }

    private Registered loadOne(File metaFile) {
        try {
            // The room id is the schematic name: derive it from this sidecar's basename (e.g. "stone_hall.yml"
            // + "stone_hall.schem" -> id "stone_hall"), and make the definition consistent.
            String base = metaFile.getName();
            if (base.endsWith(".yml")) base = base.substring(0, base.length() - 4);
            String id = base.trim().toLowerCase();
            String yaml = new String(Files.readAllBytes(metaFile.toPath()), StandardCharsets.UTF_8);
            StructureDefinition def = StructureMetadata.load(yaml);
            def.id = id;
            def.schematic = id + ".schem";
            File schemFile = new File(metaFile.getParentFile(), def.schematic);
            if (!schemFile.exists()) {
                plugin.getLogger().warning("[structures] '" + def.id + "' references schematic '"
                        + def.schematic + "' but " + schemFile.getAbsolutePath() + " is missing.");
                return null;
            }
            Clipboard clipboard = StructureWorldEdit.load(schemFile);
            if (clipboard == null) {
                plugin.getLogger().warning("[structures] '" + def.id + "' schematic '"
                        + schemFile.getName() + "' could not be loaded by WorldEdit.");
                return null;
            }
            StructureValidator.Result res = StructureValidator.validate(def, StructureWorldEdit.blockLookup(clipboard));
            if (res.hasErrors()) {
                plugin.getLogger().warning("[structures] Rejected invalid structure '" + def.id + "':");
                for (RoomValidationIssue i : res.issues)
                    if (i.level == RoomValidationIssue.Level.ERROR) plugin.getLogger().warning("  " + i);
                return null;
            }
            return new Registered(def, clipboard);
        } catch (Exception e) {
            plugin.getLogger().warning("[structures] Failed to load " + metaFile.getAbsolutePath() + ": " + e.getMessage());
            return null;
        }
    }

    /** Add built-in defaults for any room type that currently has no structure, and fill an empty library. */
    private int fillDefaults(Map<String, Registered> out) {
        List<DefaultStructures.DefaultStructure> defaults = DefaultStructures.generate();
        int added = 0;
        for (DefaultStructures.DefaultStructure d : defaults) {
            // Skip a default if the user already provides any structure serving the same room type.
            boolean covered = false;
            for (Registered r : out.values()) {
                if (r.definition().serves(RoomType.valueOf(d.definition().types.get(0)))) { covered = true; break; }
            }
            if (covered) continue;
            StructureValidator.Result res = StructureValidator.validate(d.definition());
            if (res.hasErrors()) continue;
            out.put(d.definition().id, new Registered(d.definition(), d.clipboard()));
            added++;
        }
        return added;
    }

    public Registered byId(String id) {
        return byId.get(id == null ? null : id.trim().toLowerCase());
    }

    public StructureDefinition definition(String id) {
        Registered r = byId(id);
        return r == null ? null : r.definition();
    }

    public List<StructureDefinition> all() {
        List<StructureDefinition> out = new ArrayList<>();
        for (Registered r : byId.values()) out.add(r.definition());
        return out;
    }

    public int size() {
        return byId.size();
    }

    /** Structures that may serve the given room type (pure, selection-friendly). */
    public static List<StructureDefinition> forType(List<StructureDefinition> all, RoomType type) {
        List<StructureDefinition> out = new ArrayList<>();
        for (StructureDefinition s : all) if (s.serves(type)) out.add(s);
        return out;
    }

    /** Pick a random validated structure for a room type, or null if none (caller falls back to procedural). */
    public StructureDefinition pick(RoomType type, Random rng) {
        List<StructureDefinition> pool = forType(all(), type);
        if (pool.isEmpty()) return null;
        return pool.get(rng.nextInt(pool.size()));
    }
}