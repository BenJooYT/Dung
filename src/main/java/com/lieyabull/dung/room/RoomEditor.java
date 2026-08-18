package com.lieyabull.dung.room;

import com.lieyabull.dung.Dung;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Coordinates the room-editor subsystem: the isolated editor world, the per-player authoring
 * sessions, the production registry, and asset export. Export writes a self-contained asset package
 * (the template JSON + a human/agent-readable manifest) into the plugin data folder so it can be
 * reviewed, validated, and copied into the source tree as a JAR resource. It never modifies the
 * running JAR.
 */
public final class RoomEditor {
    private final Dung plugin;
    private final RoomRegistry registry;
    private final RoomEditorWorld editorWorld;
    private final Map<UUID, RoomEditSession> sessions = new HashMap<>();
    private final File exportDir;

    public RoomEditor(Dung plugin) {
        this.plugin = plugin;
        this.registry = new RoomRegistry(plugin);
        this.editorWorld = new RoomEditorWorld(plugin);
        this.exportDir = new File(plugin.getDataFolder(), "rooms");
        if (!exportDir.exists()) exportDir.mkdirs();
    }

    public RoomRegistry registry() { return registry; }
    public RoomEditorWorld editorWorld() { return editorWorld; }
    public File exportDir() { return exportDir; }

    public RoomEditSession session(Player p) {
        return sessions.computeIfAbsent(p.getUniqueId(), k -> new RoomEditSession(k));
    }

    public void openEditor(Player p) {
        org.bukkit.World w = editorWorld.getEditorWorld();
        if (w == null) { p.sendMessage("§cCould not create editor world."); return; }
        p.teleport(new org.bukkit.Location(w, 0.5, RoomChunkGenerator.SURFACE_Y + 1, 0.5));
        p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§7Welcome to the §9room editor§7. Type §f/room help§7."));
    }

    /** Write the template JSON and an integration manifest to the export dir. Returns written files. */
    public java.util.List<File> export(RoomTemplate tpl) {
        java.util.List<File> out = new java.util.ArrayList<>();
        if (tpl == null) return out;
        File json = new File(exportDir, tpl.id + ".json");
        File manifest = new File(exportDir, tpl.id + ".MANIFEST.md");
        try {
            Files.write(json.toPath(), RoomIo.toJson(tpl).getBytes(StandardCharsets.UTF_8));
            Files.write(manifest.toPath(), manifestText(tpl).getBytes(StandardCharsets.UTF_8));
            out.add(json);
            out.add(manifest);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to export room '" + tpl.id + "': " + e.getMessage());
        }
        return out;
    }

    private String manifestText(RoomTemplate tpl) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Room asset: ").append(tpl.id).append("\n");
        sb.append("\nThis package contains the full self-contained room template. To integrate it as a\n");
        sb.append("production dungeon room, copy `").append(tpl.id).append(".json` into\n");
        sb.append("`src/main/resources/rooms/` and add `").append(tpl.id).append(".json` to\n");
        sb.append("`src/main/resources/rooms/index.txt`. The plugin validates + registers it on startup.\n\n");
        sb.append("## Metadata\n");
        sb.append("- id: ").append(tpl.id).append("\n");
        sb.append("- types: ").append(String.join(", ", tpl.types)).append("\n");
        sb.append("- description: ").append(tpl.description).append("\n");
        sb.append("- validated: ").append(tpl.validated).append("\n");
        sb.append("- format version: ").append(tpl.version).append("\n\n");
        RoomBounds total = tpl.total();
        sb.append("## Dimensions (template-relative)\n");
        sb.append("- bounds regions: ").append(tpl.bounds.size()).append("\n");
        sb.append("- total: x[").append(total.minX).append("..").append(total.maxX)
          .append("] y[").append(total.minY).append("..").append(total.maxY)
          .append("] z[").append(total.minZ).append("..").append(total.maxZ).append("]\n\n");
        sb.append("## Connections\n");
        for (RoomConnector c : tpl.connectors) {
            sb.append("- ").append(c.direction).append(" ").append(c.type).append(" @(")
              .append(c.x).append(",").append(c.y).append(",").append(c.z)
              .append(") w=").append(c.width).append(" h=").append(c.height)
              .append(" floorY=").append(c.floorY).append(" clearance=").append(c.clearance).append("\n");
        }
        sb.append("\n## Spawn floors\n");
        for (SpawnFloor s : tpl.spawnFloors) {
            sb.append("- (").append(s.minX).append(",").append(s.minY).append(",").append(s.minZ)
              .append(")-(").append(s.maxX).append(",").append(s.maxY).append(",").append(s.maxZ).append(")\n");
        }
        sb.append("\n## Markers\n");
        for (RoomMarker m : tpl.markers) {
            sb.append("- ").append(m.type).append(" @(").append(m.x).append(",").append(m.y).append(",").append(m.z)
              .append(")").append(m.name != null && !m.name.isEmpty() ? " \"" + m.name + "\"" : "").append("\n");
        }
        sb.append("\n## Block structure\n");
        sb.append("- ").append(tpl.blocks.size()).append(" blocks (block states preserved).\n");
        return sb.toString();
    }
}