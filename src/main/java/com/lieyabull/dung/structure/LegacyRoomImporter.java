package com.lieyabull.dung.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lieyabull.dung.Dung;
import com.lieyabull.dung.room.RoomBounds;
import com.lieyabull.dung.room.RoomMarker;
import com.lieyabull.dung.room.RoomMarkerType;
import com.lieyabull.dung.room.SpawnFloor;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/** Imports the pre-WorldEdit JSON room assets from plugins/Dung/rooms. */
final class LegacyRoomImporter {
    private LegacyRoomImporter() {}

    static int importAll(Dung plugin, File structuresDir) {
        File roomsDir = new File(plugin.getDataFolder(), "rooms");
        File[] files = roomsDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files == null) return 0;

        int imported = 0;
        for (File json : files) {
            try {
                if (importOne(plugin, structuresDir, json)) imported++;
            } catch (Exception e) {
                plugin.getLogger().warning("[structures] Failed to import legacy room "
                        + json.getName() + ": " + e.getMessage());
            }
        }
        if (imported > 0) {
            plugin.getLogger().info("[structures] Imported " + imported + " legacy room JSON asset(s).");
        }
        return imported;
    }

    private static boolean importOne(Dung plugin, File structuresDir, File jsonFile) throws Exception {
        JsonObject root = JsonParser.parseString(Files.readString(jsonFile.toPath(), StandardCharsets.UTF_8))
                .getAsJsonObject();
        String id = root.get("id").getAsString().trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) throw new IllegalArgumentException("missing id");

        File outDir = new File(structuresDir, id);
        File schem = new File(outDir, id + ".schem");
        File metadata = new File(outDir, id + ".yml");
        if (schem.exists() && metadata.exists()) return false;

        StructureDefinition def = definition(root, id);
        Clipboard original = clipboard(root);
        // Old JSON rooms are usually smaller than the procedural long room; pad them so the fixed
        // corridor line runs through their walls instead of outside them.
        StructurePadder.Result fitted = StructurePadder.fit(def, original);
        StructureDefinition outDef = fitted.definition();
        Clipboard outClipboard = fitted.clipboard();

        if (!outDir.exists() && !outDir.mkdirs()) throw new IllegalStateException("could not create " + outDir);
        if (!StructureWorldEdit.save(outClipboard, schem)) throw new IllegalStateException("could not write schematic");
        Files.writeString(metadata.toPath(), StructureMetadata.dump(outDef), StandardCharsets.UTF_8);
        plugin.getLogger().info("[structures] Imported legacy room '" + id + "' from " + jsonFile.getName() + ".");
        return true;
    }

    private static StructureDefinition definition(JsonObject root, String id) {
        StructureDefinition def = new StructureDefinition();
        def.id = id;
        def.schematic = id + ".schem";
        def.allowedRotations = List.of(0, 1, 2, 3);
        if (root.has("description")) def.description = root.get("description").getAsString();
        for (JsonElement type : array(root, "types")) def.types.add(type.getAsString());

        JsonArray bounds = array(root, "bounds");
        JsonObject frame = bounds.isEmpty() ? null : bounds.get(0).getAsJsonObject();
        int minX = frame == null ? 0 : integer(frame, "minX");
        int minY = frame == null ? 0 : integer(frame, "minY");
        int minZ = frame == null ? 0 : integer(frame, "minZ");

        for (JsonElement e : bounds) {
            JsonObject b = e.getAsJsonObject();
            def.bounds.add(new RoomBounds(
                    integer(b, "minX") - minX, integer(b, "minY") - minY, integer(b, "minZ") - minZ,
                    integer(b, "maxX") - minX, integer(b, "maxY") - minY, integer(b, "maxZ") - minZ));
        }
        for (JsonElement e : array(root, "spawnFloors")) {
            JsonObject b = e.getAsJsonObject();
            def.spawnFloors.add(new SpawnFloor(
                    integer(b, "minX") - minX, integer(b, "minY") - minY, integer(b, "minZ") - minZ,
                    integer(b, "maxX") - minX, integer(b, "maxY") - minY, integer(b, "maxZ") - minZ));
        }
        for (JsonElement e : array(root, "markers")) {
            JsonObject raw = e.getAsJsonObject();
            RoomMarker marker = new RoomMarker();
            marker.type = RoomMarkerType.byName(raw.get("type").getAsString());
            marker.x = integer(raw, "x") - minX;
            marker.y = integer(raw, "y") - minY;
            marker.z = integer(raw, "z") - minZ;
            if (raw.has("name")) marker.name = raw.get("name").getAsString();
            def.markers.add(marker);
        }

        // The old aperture levels are only hints for the padder's lane planner; the sealed room is
        // re-planned so a door cannot float above the floor at its new centred lane.
        def.doorsConfigured = true;
        def.doors = new StructureDefinition.DoorSpec[4];
        for (int d = 0; d < 4; d++) def.doors[d] = new StructureDefinition.DoorSpec();
        for (JsonElement e : array(root, "connectors")) {
            JsonObject connector = e.getAsJsonObject();
            int direction = direction(connector.get("direction").getAsString());
            if (direction >= 0) {
                def.doors[direction] = new StructureDefinition.DoorSpec(
                        integer(connector, "floorY") - minY, integer(connector, "height"));
            }
        }
        return def;
    }

    private static Clipboard clipboard(JsonObject root) {
        JsonObject b = array(root, "bounds").get(0).getAsJsonObject();
        BlockVector3 min = BlockVector3.at(integer(b, "minX"), integer(b, "minY"), integer(b, "minZ"));
        BlockVector3 max = BlockVector3.at(integer(b, "maxX"), integer(b, "maxY"), integer(b, "maxZ"));
        BlockArrayClipboard clipboard = new BlockArrayClipboard(new CuboidRegion(min, max));
        for (JsonElement e : array(root, "blocks")) {
            JsonObject block = e.getAsJsonObject();
            String id = block.get("b").getAsString();
            int stateStart = id.indexOf('[');
            String blockId = stateStart < 0 ? id : id.substring(0, stateStart);
            BlockType type = BlockTypes.get(blockId);
            if (type == null) throw new IllegalArgumentException("unknown block type '" + id + "'");
            var state = type.getDefaultState();
            if (stateStart >= 0 && id.endsWith("]")) {
                String properties = id.substring(stateStart + 1, id.length() - 1);
                for (String property : properties.split(",")) {
                    String[] pair = property.split("=", 2);
                    if (pair.length != 2) continue;
                    var definition = type.getProperty(pair[0]);
                    if (definition != null) state = state.with(definition, definition.getValueFor(pair[1]));
                }
            }
            clipboard.setBlock(BlockVector3.at(integer(block, "x"), integer(block, "y"), integer(block, "z")), state);
        }
        sealLegacyConnectors(root, clipboard);
        clipboard.setOrigin(min);
        return clipboard;
    }

    /** Legacy rooms already contain their old, off-centre doorway openings. The current generator
     *  owns corridor placement, so close those old openings before writing the converted schematic. */
    private static void sealLegacyConnectors(JsonObject root, BlockArrayClipboard clipboard) {
        var wall = BlockTypes.STONE_BRICKS.getDefaultState();
        BlockVector3 min = clipboard.getMinimumPoint();
        BlockVector3 max = clipboard.getMaximumPoint();
        for (JsonElement e : array(root, "connectors")) {
            JsonObject connector = e.getAsJsonObject();
            int direction = direction(connector.get("direction").getAsString());
            if (direction < 0) continue;
            for (int[] cell : StructureAuthoring.centeredApertureCells(
                    direction, integer(connector, "x"), integer(connector, "y"), integer(connector, "z"),
                    integer(connector, "width"), integer(connector, "height"))) {
                int x = cell[0];
                int y = cell[1];
                int z = cell[2];
                if (x < min.x() || x > max.x() || y < min.y() || y > max.y()
                        || z < min.z() || z > max.z()) {
                    continue;
                }
                clipboard.setBlock(BlockVector3.at(x, y, z), wall);
            }
        }
    }

    private static int direction(String name) {
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "NORTH" -> 0;
            case "EAST" -> 1;
            case "SOUTH" -> 2;
            case "WEST" -> 3;
            default -> -1;
        };
    }

    private static JsonArray array(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || !value.isJsonArray() ? new JsonArray() : value.getAsJsonArray();
    }

    private static int integer(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null) throw new IllegalArgumentException("missing " + name);
        return value.getAsInt();
    }
}
