package com.lieyabull.dung.structure;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.dungeon.RoomGen;
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
    /** Sidecar file for each disk-loaded structure (defaults have no entry). */
    private final Map<String, File> files = new LinkedHashMap<>();

    public StructureRegistry(Dung plugin) {
        this.plugin = plugin;
        load();
    }

    /** (Re)load all structures from disk, then fill any gaps with defaults. */
    public synchronized void load() {
        byId.clear();
        files.clear();
        File dir = new File(plugin.getDataFolder(), STRUCTURES_DIR);
        if (!dir.exists()) dir.mkdirs();
        // Keep older installs working: pre-WorldEdit room JSON assets live in rooms/.
        LegacyRoomImporter.importAll(plugin, dir);
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
                files.put(r.definition().id, meta.toFile());
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
            // Structures authored before /dung room gen's sign stripping (or saved by hand) still
            // carry their marker signs in the schematic. Remove them on load so they never paste.
            int strippedSigns = SignScanner.stripMarkerSigns(clipboard);
            if (strippedSigns > 0) {
                plugin.getLogger().info("[structures] Removed " + strippedSigns
                        + " marker sign(s) from '" + def.id + "'.");
            }
            boolean metadataChanged = migrateLegacyCoordinates(def, clipboard);
            boolean padded = false;
            if (StructurePadder.needsPadding(def)) {
                StructurePadder.Result fit = StructurePadder.fit(def, clipboard);
                def = fit.definition();
                clipboard = fit.clipboard();
                padded = true;
                plugin.getLogger().info("[structures] Padding '" + def.id + "' to a "
                        + def.total().width() + "x" + def.total().depth()
                        + " fixed-line frame so its doors align with the corridors.");
            }
            if (!def.doorsConfigured) {
                def.doors = StructureAuthoring.detectDoors(
                        StructureWorldEdit.blockLookup(clipboard), def.total(), RoomGen.PERP_CENTER);
                def.doorsConfigured = true;
                metadataChanged = true;
                plugin.getLogger().info("[structures] Detected door levels for '" + def.id + "'.");
            }
            boolean rewrite = padded || strippedSigns > 0;
            if (metadataChanged && !rewrite) persistMetadata(def, metaFile);
            StructureValidator.Result res = StructureValidator.validate(def, StructureWorldEdit.blockLookup(clipboard));
            if (res.hasErrors()) {
                plugin.getLogger().warning("[structures] Rejected invalid structure '" + def.id + "':");
                for (RoomValidationIssue i : res.issues)
                    if (i.level == RoomValidationIssue.Level.ERROR) plugin.getLogger().warning("  " + i);
                return null;
            }
            if (rewrite) persistRewritten(def, clipboard, metaFile, schemFile, padded);
            return new Registered(def, clipboard);
        } catch (Exception e) {
            plugin.getLogger().warning("[structures] Failed to load " + metaFile.getAbsolutePath() + ": " + e.getMessage());
            return null;
        }
    }

    /** Older {@code /dung room gen} builds wrote absolute clipboard coordinates into the sidecar.
     *  Detect that exact shape and rewrite it relative to the clipboard minimum. */
    private boolean migrateLegacyCoordinates(StructureDefinition def, Clipboard clipboard) {
        var min = clipboard.getMinimumPoint();
        var max = clipboard.getMaximumPoint();
        StructureAuthoring.ClipboardFrame frame = StructureAuthoring.frame(
                min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
        if (!StructureAuthoring.migrateLegacy(def, frame)) return false;
        plugin.getLogger().info("[structures] Migrated legacy absolute coordinates to relative for '"
                + def.id + "'.");
        return true;
    }

    private void persistMetadata(StructureDefinition def, File metaFile) {
        try {
            Files.write(metaFile.toPath(), StructureMetadata.dump(def).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("[structures] Could not rewrite " + metaFile.getName()
                    + " after migration/door detection: " + e.getMessage());
        }
    }

    /** Write a padded/sign-cleaned structure back once. The authored files are copied to
     *  {@code plugins/Dung/structure-backups/<id>/} first, so the original build is never lost. */
    private void persistRewritten(StructureDefinition def, Clipboard clipboard,
                                  File metaFile, File schemFile, boolean padded) {
        File backupDir = new File(plugin.getDataFolder(), "structure-backups/" + def.id);
        try {
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                plugin.getLogger().warning("[structures] Could not create backup directory for '"
                        + def.id + "'; leaving its files unchanged.");
                return;
            }
            File backupSchem = new File(backupDir, schemFile.getName());
            File backupMeta = new File(backupDir, metaFile.getName());
            if (!backupSchem.exists()) Files.copy(schemFile.toPath(), backupSchem.toPath());
            if (!backupMeta.exists()) Files.copy(metaFile.toPath(), backupMeta.toPath());
            if (!StructureWorldEdit.save(clipboard, schemFile)) {
                plugin.getLogger().warning("[structures] Could not rewrite schematic for '"
                        + def.id + "'; leaving its files unchanged.");
                return;
            }
            Files.write(metaFile.toPath(), StructureMetadata.dump(def).getBytes(StandardCharsets.UTF_8));
            String action = padded
                    ? "Padded '" + def.id + "' to " + def.total().width() + "x" + def.total().depth()
                    : "Rewrote '" + def.id + "' without marker signs";
            plugin.getLogger().info("[structures] " + action + "; original saved in "
                    + backupDir.getAbsolutePath() + ".");
        } catch (IOException e) {
            plugin.getLogger().warning("[structures] Could not persist '" + def.id
                    + "': " + e.getMessage());
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

    /** Persist an edited definition back to its disk sidecar. Defaults and unknown ids fail. */
    public boolean save(StructureDefinition def) {
        if (def == null) return false;
        File file = files.get(def.id);
        if (file == null) return false;
        try {
            Files.write(file.toPath(), StructureMetadata.dump(def).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("[structures] Could not save " + file.getName() + ": " + e.getMessage());
            return false;
        }
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
