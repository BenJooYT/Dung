package com.lieyabull.dung.command;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.room.Direction;
import com.lieyabull.dung.room.RoomConnType;
import com.lieyabull.dung.room.RoomEditSession;
import com.lieyabull.dung.room.RoomEditor;
import com.lieyabull.dung.room.RoomMarkerType;
import com.lieyabull.dung.room.RoomTemplate;
import com.lieyabull.dung.room.RoomTester;
import com.lieyabull.dung.room.RoomValidationIssue;
import com.lieyabull.dung.room.RoomValidator;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * In-game room editor CLI. Worlds colliding with room construction, gameplay metadata, and
 * validation are author-friendly (like building normally) while the produced template is validated
 * and testable before it can ever be bundled.
 */
public final class RoomCommand implements CommandExecutor, TabCompleter {
    private static final String[] SUB = {
        "help","new","pos1","pos2","region","spawnfloor","conn","marker","playerspawn",
        "shopkeeper","capture","info","validate","export","test","testlocal","list","open","tutorial","toggle"
    };

    private final Dung plugin;
    private final RoomEditor editor;
    private final RoomTester tester;

    public RoomCommand(Dung plugin, RoomEditor editor) {
        this.plugin = plugin;
        this.editor = editor;
        this.tester = new RoomTester(plugin, editor.editorWorld());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use the room editor.");
            return true;
        }
        if (!p.isOp() && !p.hasPermission("dung.admin")) {
            p.sendMessage("§cYou need the §odung.admin§c permission to use the room editor.");
            return true;
        }
        if (args.length == 0) { help(p); return true; }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help": help(p); break;
            case "open": editor.openEditor(p); break;
            case "new": return newRoom(p, args);
            case "pos1": case "pos2": return setPos(p, sub);
            case "region": return region(p);
            case "spawnfloor": return spawnFloor(p);
            case "conn": return conn(p, args);
            case "marker": return marker(p, args);
            case "playerspawn": return playerSpawn(p);
            case "shopkeeper": return shopkeeper(p);
            case "capture": return capture(p);
            case "info": return info(p);
            case "validate": return validate(p);
            case "export": return export(p, args);
            case "test": return test(p, args, false);
            case "testlocal": return test(p, args, true);
            case "list": return list(p);
            case "tutorial": return tutorial(p, args);
            case "toggle": return toggle(p);
            default: p.sendMessage("§cUnknown subcommand §f" + args[0] + "§c. Use §f/room help§c."); break;
        }
        return true;
    }

    private void help(Player p) {
        p.sendMessage("§9Room editor§7 (build a room, then capture + validate + export):");
        p.sendMessage("§f/room open §7- teleport to the editor world");
        p.sendMessage("§f/room new <id> [type...] §7- start a template (types: START/COMBAT/TREASURE/SHOP/SECRET/ELITE/BOSS/LOCKED)");
        p.sendMessage("§f/room pos1 / pos2 §7- WorldEdit-like two-corner selection");
        p.sendMessage("§f/room region §7- add selection as a room-bound cuboid (first one sets the origin)");
        p.sendMessage("§f/room spawnfloor §7- add selection as an enemy spawn floor");
        p.sendMessage("§f/room conn <n/e/s/w/u/d> [type] [width] [height] §7- add a connection at your position (type: DOOR/CORRIDOR/OPENING/LOCKED/SECRET/BOSS/STAIR/SHAFT)");
        p.sendMessage("§f/room marker <type> [name] §7- add a metadata point (PLAYER_SPAWN/SHOPKEEPER/LOOT/HAZARD/MECHANIC/SPECIAL)");
        p.sendMessage("§f/room playerspawn / shopkeeper §7- shortcuts to set those markers at your position");
        p.sendMessage("§f/room capture §7- snapshot the blocks inside all bound regions");
        p.sendMessage("§f/room info §7- show the in-progress template summary");
        p.sendMessage("§f/room validate §7- validate; only a passing room may be exported/registered");
        p.sendMessage("§f/room export <id> §7- write asset + manifest to plugins/Dung/rooms/");
        p.sendMessage("§f/room test <id> §7- test a registered room; §f/room testlocal §7- test the in-progress one");
        p.sendMessage("§f/room list §7- list registered production templates");
        p.sendMessage("§f/room tutorial [next|back|reset|skip <n>] §7- walk-through tutorial (replayable)");
        p.sendMessage("§f/room toggle §7- enable/disable custom room templates (saves to config)");
    }

    private boolean newRoom(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§cUsage: /room new <id> [type...]"); return true; }
        String id = args[1].toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (id.isEmpty()) { p.sendMessage("§cInvalid id (use a-z0-9_)."); return true; }
        List<String> types = new ArrayList<>();
        if (args.length > 2) {
            for (int i = 2; i < args.length; i++) {
                String t = args[i].toUpperCase();
                try { com.lieyabull.dung.dungeon.RoomType.valueOf(t); types.add(t); }
                catch (IllegalArgumentException e) { p.sendMessage("§cIgnoring unknown type " + args[i]); }
            }
        }
        if (types.isEmpty()) types.add("COMBAT");
        RoomEditSession s = editor.session(p);
        s.start(id, types);
        p.sendMessage("§aStarted room template §f" + id + "§a types=§f" + types);
        return true;
    }

    private boolean setPos(Player p, String which) {
        RoomEditSession s = editor.session(p);
        if (s.template() == null) { p.sendMessage("§cStart a room with /room new first."); return true; }
        int x = p.getLocation().getBlockX(), y = p.getLocation().getBlockY(), z = p.getLocation().getBlockZ();
        if (which.equals("pos1")) s.setPos1(x, y, z); else s.setPos2(x, y, z);
        p.sendMessage("§7" + which + " = §f" + x + "," + y + "," + z);
        return true;
    }

    private boolean region(Player p) {
        RoomEditSession s = editor.session(p);
        if (s.template() == null) { p.sendMessage("§cStart a room first."); return true; }
        if (!s.hasSelection()) { p.sendMessage("§cSet both pos1 and pos2 first."); return true; }
        s.addRegionFromSelection();
        p.sendMessage("§aAdded bound region. Set a new selection for another cuboid, or /room capture.");
        return true;
    }

    private boolean spawnFloor(Player p) {
        RoomEditSession s = editor.session(p);
        if (s.template() == null) { p.sendMessage("§cStart a room first."); return true; }
        if (!s.hasSelection()) { p.sendMessage("§cSet both pos1 and pos2 first."); return true; }
        s.addSpawnFloorFromSelection();
        p.sendMessage("§aAdded spawn floor.");
        return true;
    }

    private boolean conn(Player p, String[] args) {
        RoomEditSession s = editor.session(p);
        if (s.template() == null) { p.sendMessage("§cStart a room first."); return true; }
        if (s.template().bounds.isEmpty()) { p.sendMessage("§cAdd a bound region first (origin)."); return true; }
        if (args.length < 2) { p.sendMessage("§cUsage: /room conn <n/e/s/w/u/d> [type] [width] [height]"); return true; }
        Direction dir;
        switch (args[1].toLowerCase()) {
            case "n": dir = Direction.NORTH; break;
            case "e": dir = Direction.EAST; break;
            case "s": dir = Direction.SOUTH; break;
            case "w": dir = Direction.WEST; break;
            case "u": dir = Direction.UP; break;
            case "d": dir = Direction.DOWN; break;
            default: p.sendMessage("§cBad direction " + args[1]); return true;
        }
        RoomConnType type = RoomConnType.DOOR;
        int width = 3, height = 3;
        try {
            if (args.length > 2) type = RoomConnType.byName(args[2]);
            if (args.length > 3) width = Integer.parseInt(args[3]);
            if (args.length > 4) height = Integer.parseInt(args[4]);
        } catch (Exception e) { p.sendMessage("§cBad arg: " + e.getMessage()); return true; }
        Location loc = p.getLocation();
        s.addConnector(dir, type, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), width, height, 1);
        p.sendMessage("§aAdded " + dir + " " + type + " connection w=" + width + " h=" + height + " at your position.");
        return true;
    }

    private boolean marker(Player p, String[] args) {
        RoomEditSession s = editor.session(p);
        if (s.template() == null) { p.sendMessage("§cStart a room first."); return true; }
        if (args.length < 2) { p.sendMessage("§cUsage: /room marker <type> [name]"); return true; }
        RoomMarkerType type;
        try { type = RoomMarkerType.byName(args[1]); }
        catch (Exception e) { p.sendMessage("§cBad marker type " + args[1]); return true; }
        String name = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";
        Location loc = p.getLocation();
        s.addMarker(type, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), name);
        p.sendMessage("§aAdded " + type + " marker at your position" + (name.isEmpty() ? "" : " (" + name + ")") + ".");
        return true;
    }

    private boolean playerSpawn(Player p) {
        RoomEditSession s = editor.session(p);
        if (s.template() == null) { p.sendMessage("§cStart a room first."); return true; }
        Location loc = p.getLocation();
        s.setPlayerSpawn(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        p.sendMessage("§aPlayer spawn set at your position.");
        return true;
    }

    private boolean shopkeeper(Player p) {
        RoomEditSession s = editor.session(p);
        if (s.template() == null) { p.sendMessage("§cStart a room first."); return true; }
        Location loc = p.getLocation();
        s.setShopkeeper(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        p.sendMessage("§aShopkeeper set at your position.");
        return true;
    }

    private boolean capture(Player p) {
        RoomEditSession s = editor.session(p);
        if (s.template() == null) { p.sendMessage("§cStart a room first."); return true; }
        if (s.template().bounds.isEmpty()) { p.sendMessage("§cAdd at least one bound region first."); return true; }
        org.bukkit.World w = editor.editorWorld().getEditorWorld();
        if (!editor.editorWorld().isEditorWorld(p.getWorld())) { p.sendMessage("§cCapture in the editor world (/room open) so no foreign terrain is included."); return true; }
        s.captureBlocks(w);
        p.sendMessage("§aCaptured §f" + s.template().blocks.size() + "§a blocks from " + s.template().bounds.size() + " region(s).");
        return true;
    }

    private boolean info(Player p) {
        RoomEditSession s = editor.session(p);
        RoomTemplate t = s.template();
        if (t == null) { p.sendMessage("§7No in-progress template. /room new <id> [type]"); return true; }
        p.sendMessage("§9Template §f" + t.id + "§9 types=§f" + t.types);
        p.sendMessage("  regions=" + t.bounds.size() + " connectors=" + t.connectors.size()
                + " spawnFloors=" + t.spawnFloors.size() + " markers=" + t.markers.size() + " blocks=" + t.blocks.size()
                + " validated=" + t.validated);
        return true;
    }

    private boolean validate(Player p) {
        RoomEditSession s = editor.session(p);
        if (s.template() == null) { p.sendMessage("§cNo template in progress."); return true; }
        if (s.template().bounds.isEmpty()) { p.sendMessage("§cNothing to validate yet - add regions + capture."); return true; }
        RoomValidator.Result res = RoomValidator.validate(s.template());
        for (RoomValidationIssue i : res.issues) p.sendMessage((i.level == RoomValidationIssue.Level.ERROR ? "§c" : "§e") + i);
        p.sendMessage(res.valid ? "§aRoom '" + s.template().id + "' VALID - ready to export/register." : "§cRoom INVALID - " + res.errorCount() + " error(s). Fix and re-validate.");
        return true;
    }

    private boolean export(Player p, String[] args) {
        RoomEditSession s = editor.session(p);
        if (s.template() == null) { p.sendMessage("§cNo template in progress."); return true; }
        RoomTemplate t = s.template();
        if (args.length < 2) { p.sendMessage("§cUsage: /room export <id>"); return true; }
        String id = args[1].toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (t.id.isEmpty()) t.id = id;
        else if (!t.id.equals(id)) t.id = id;
        RoomValidator.Result res = RoomValidator.validate(t);
        if (res.hasErrors()) {
            p.sendMessage("§cCannot export invalid room - fix errors first:");
            for (RoomValidationIssue i : res.issues) if (i.level == RoomValidationIssue.Level.ERROR) p.sendMessage("  §c" + i);
            return true;
        }
        java.util.List<File> files = editor.export(t);
        if (files.isEmpty()) { p.sendMessage("§cExport failed."); return true; }
        for (File f : files) p.sendMessage("§aWrote §f" + f.getAbsolutePath());
        p.sendMessage("§7To integrate: copy §f" + t.id + ".json§7 into §fsrc/main/resources/rooms/§7 and add it to §frooms/index.txt§7. Then build.");
        return true;
    }

    private boolean test(Player p, String[] args, boolean local) {
        RoomTemplate tpl;
        if (local) {
            RoomEditSession s = editor.session(p);
            tpl = s.template();
            if (tpl == null) { p.sendMessage("§cNo in-progress template to test."); return true; }
        } else {
            if (args.length < 2) { p.sendMessage("§cUsage: /room test <id>"); return true; }
            tpl = editor.registry().byId(args[1]);
            if (tpl == null) { p.sendMessage("§cNo registered template '" + args[1] + "'. Try /room testlocal for the in-progress room."); return true; }
        }
        RoomTester.Result r = tester.test(tpl, p);
        for (String l : r.lines) p.sendMessage(l);
        p.sendMessage(r.summary());
        return true;
    }

    private boolean tutorial(Player p, String[] args) {
        com.lieyabull.dung.room.RoomTutorial tut = plugin.roomTutorial();
        if (args.length < 2) {
            tut.start(p);
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "next":
                tut.next(p);
                break;
            case "back":
                tut.back(p);
                break;
            case "reset":
                tut.reset(p);
                break;
            case "skip":
                if (args.length < 3) {
                    p.sendMessage("§cUsage: /room tutorial skip <step>");
                    return true;
                }
                try {
                    int n = Integer.parseInt(args[2]);
                    tut.skipTo(p, n);
                } catch (NumberFormatException e) {
                    p.sendMessage("§cInvalid step number: " + args[2]);
                }
                break;
            default:
                p.sendMessage("§cUnknown tutorial subcommand. Use: next, back, reset, skip <n>");
                break;
        }
        return true;
    }

    private boolean list(Player p) {
        java.util.List<RoomTemplate> all = editor.registry().all();
        if (all.isEmpty()) { p.sendMessage("§7No registered room templates."); return true; }
        p.sendMessage("§9Registered rooms:");
        for (RoomTemplate t : all) p.sendMessage("  §f" + t.id + "§7 [" + String.join(",", t.types) + "]");
        return true;
    }

    private boolean toggle(Player p) {
        boolean current = plugin.getConfig().getBoolean("custom-rooms", true);
        boolean newVal = !current;
        plugin.getConfig().set("custom-rooms", newVal);
        plugin.saveConfig();
        p.sendMessage("§aCustom rooms are now " + (newVal ? "§2ENABLED" : "§cDISABLED") + "§a. "
                + "New dungeon floors will " + (newVal ? "" : "§cnot ") + "§ause custom room templates.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUB, args[0]);
        }
        if (args[0].equalsIgnoreCase("conn") && args.length == 3) {
            String[] dirs = {"n","e","s","w","u","d"};
            return filter(dirs, args[1]);
        }
        if (args[0].equalsIgnoreCase("conn") && args.length == 4) {
            String[] types = {"DOOR","CORRIDOR","OPENING","LOCKED","SECRET","BOSS","STAIR","SHAFT"};
            return filter(types, args[2]);
        }
        if (args[0].equalsIgnoreCase("marker") && args.length == 3) {
            String[] types = {"PLAYER_SPAWN","SHOPKEEPER","LOOT","HAZARD","MECHANIC","SPECIAL"};
            return filter(types, args[2]);
        }
        if (args[0].equalsIgnoreCase("tutorial") && args.length == 2) {
            String[] subs = {"next","back","reset","skip"};
            return filter(subs, args[1]);
        }
        return java.util.Collections.emptyList();
    }

    private List<String> filter(String[] opts, String prefix) {
        List<String> out = new ArrayList<>();
        for (String o : opts) if (o.startsWith(prefix)) out.add(o);
        return out;
    }
}