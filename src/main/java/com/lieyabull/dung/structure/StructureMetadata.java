package com.lieyabull.dung.structure;

import com.lieyabull.dung.room.Direction;
import com.lieyabull.dung.room.RoomBounds;
import com.lieyabull.dung.room.RoomMarker;
import com.lieyabull.dung.room.RoomMarkerType;
import com.lieyabull.dung.room.SpawnFloor;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes {@link StructureDefinition} from/to the {@code structure.yml} sidecar format using
 * SnakeYAML. The reader is lenient (missing fields default; unknown keys ignored) but reports a clear
 * exception on a malformed file. The writer emits a stable, Git-friendly layout.
 *
 * <p>Pure logic (no Bukkit / no WorldEdit), so parsing and round-tripping are unit-testable headlessly.
 */
public final class StructureMetadata {
    private StructureMetadata() {}

    public static StructureDefinition load(String yaml) {
        Yaml yamlParser = new Yaml();
        Object root = yamlParser.load(yaml);
        if (!(root instanceof Map<?, ?> map)) throw new IllegalArgumentException("structure.yml must be a YAML mapping");
        return fromMap(map);
    }

    public static String dump(StructureDefinition s) {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        Yaml yamlParser = new Yaml(opts);
        return yamlParser.dump(toMap(s));
    }

    static StructureDefinition fromMap(Map<?, ?> map) {
        StructureDefinition s = new StructureDefinition();
        s.version = int_(map, "version", StructureDefinition.CURRENT_VERSION);
        s.id = str(map, "id", "").trim().toLowerCase();
        s.types = strList(map, "types");
        s.description = str(map, "description", "");
        s.schematic = str(map, "schematic", "structure.schem");
        s.facing = dir(str(map, "facing", "NORTH"));
        s.allowedRotations = intList(map, "allowed-rotations", List.of(0, 1, 2, 3));
        s.roomHeight = int_(map, "room-height", 0);
        s.entryHeight = int_(map, "entry-height", 3);
        s.exitHeight = int_(map, "exit-height", 3);

        s.bounds = new ArrayList<>();
        for (Object o : list(map, "bounds")) {
            if (o instanceof Map<?, ?> m) s.bounds.add(bounds(m));
        }

        s.spawnFloors = new ArrayList<>();
        for (Object o : list(map, "spawn-floors")) {
            if (o instanceof Map<?, ?> m) s.spawnFloors.add(new SpawnFloor(
                    int_(m, "min-x", 0), int_(m, "min-y", 0), int_(m, "min-z", 0),
                    int_(m, "max-x", 0), int_(m, "max-y", 0), int_(m, "max-z", 0)));
        }

        s.markers = new ArrayList<>();
        for (Object o : list(map, "markers")) {
            if (o instanceof Map<?, ?> m) s.markers.add(marker(m));
        }
        return s;
    }

    private static RoomBounds bounds(Map<?, ?> m) {
        return new RoomBounds(
                int_(m, "min-x", 0), int_(m, "min-y", 0), int_(m, "min-z", 0),
                int_(m, "max-x", 0), int_(m, "max-y", 0), int_(m, "max-z", 0));
    }

    private static RoomMarker marker(Map<?, ?> m) {
        RoomMarker mk = new RoomMarker();
        mk.type = markerType(str(m, "type", "SPECIAL"));
        mk.x = int_(m, "x", 0); mk.y = int_(m, "y", 0); mk.z = int_(m, "z", 0);
        mk.name = str(m, "name", "");
        Object region = m.get("region");
        if (region instanceof Map<?, ?> rm) mk.region = bounds(rm);
        return mk;
    }

    static Map<String, Object> toMap(StructureDefinition s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", s.version);
        m.put("id", s.id);
        m.put("types", new ArrayList<>(s.types));
        m.put("description", s.description);
        m.put("schematic", s.schematic);
        m.put("facing", s.facing.name());
        m.put("allowed-rotations", new ArrayList<>(s.allowedRotations));
        m.put("room-height", s.roomHeight);
        m.put("entry-height", s.entryHeight);
        m.put("exit-height", s.exitHeight);

        List<Object> bounds = new ArrayList<>();
        for (RoomBounds b : s.bounds) bounds.add(boundsMap(b));
        m.put("bounds", bounds);

        List<Object> floors = new ArrayList<>();
        for (SpawnFloor f : s.spawnFloors) floors.add(boundsMap(f));
        m.put("spawn-floors", floors);

        List<Object> markers = new ArrayList<>();
        for (RoomMarker mk : s.markers) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("type", mk.type.name());
            mm.put("x", mk.x); mm.put("y", mk.y); mm.put("z", mk.z);
            if (mk.name != null && !mk.name.isEmpty()) mm.put("name", mk.name);
            if (mk.region != null) mm.put("region", boundsMap(mk.region));
            markers.add(mm);
        }
        m.put("markers", markers);
        return m;
    }

    private static Map<String, Object> boundsMap(RoomBounds b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("min-x", b.minX); m.put("min-y", b.minY); m.put("min-z", b.minZ);
        m.put("max-x", b.maxX); m.put("max-y", b.maxY); m.put("max-z", b.maxZ);
        return m;
    }

    // ================= lenient accessors =================

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (v == null) return List.of();
        if (v instanceof List<?> l) return (List<Object>) l;
        throw new IllegalArgumentException("'" + key + "' must be a list");
    }

    private static List<String> strList(Map<?, ?> m, String key) {
        List<String> out = new ArrayList<>();
        for (Object o : list(m, key)) if (o != null) out.add(o.toString());
        return out;
    }

    private static List<Integer> intList(Map<?, ?> m, String key, List<Integer> def) {
        Object v = m.get(key);
        if (v == null) return def;
        if (v instanceof List<?> l) {
            List<Integer> out = new ArrayList<>();
            for (Object o : l) if (o instanceof Number n) out.add(n.intValue());
            return out;
        }
        throw new IllegalArgumentException("'" + key + "' must be a list of integers");
    }

    private static int int_(Map<?, ?> m, String key, int def) {
        Object v = m.get(key);
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        throw new IllegalArgumentException("'" + key + "' must be an integer, got " + v);
    }

    private static String str(Map<?, ?> m, String key, String def) {
        Object v = m.get(key);
        return v == null ? def : v.toString();
    }

    private static Direction dir(String s) {
        try {
            return Direction.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown direction '" + s + "'");
        }
    }

    private static RoomMarkerType markerType(String s) {
        try {
            return RoomMarkerType.byName(s);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown marker type '" + s + "'");
        }
    }
}