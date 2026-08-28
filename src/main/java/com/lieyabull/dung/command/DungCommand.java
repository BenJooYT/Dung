package com.lieyabull.dung.command;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.game.DungeonInstance;
import com.lieyabull.dung.game.GameManager;
import com.lieyabull.dung.game.Run;
import com.lieyabull.dung.game.WorkstationRules;
import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.items.ItemPool;
import com.lieyabull.dung.items.ItemTags;
import com.lieyabull.dung.items.Rarity;
import com.lieyabull.dung.meta.MetaManager;
import com.lieyabull.dung.party.Party;
import com.lieyabull.dung.party.PartyManager;
import com.lieyabull.dung.ui.ChatUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import com.lieyabull.dung.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DungCommand implements CommandExecutor, TabCompleter {
    private final Dung plugin;
    /** Non-run commands (/shop, /party, /leaderboard, …) live here; /dung <sub> delegates to it. */
    private final MetaCommand meta;

    public DungCommand(Dung plugin, MetaCommand meta) {
        this.plugin = plugin;
        this.meta = meta;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Admin cleanup, also runnable from console to clear a stuck boss HP bar: /dung bossbar
        if ((label.equalsIgnoreCase("dung") || label.equalsIgnoreCase("dungeon"))
                && args.length > 0 && args[0].equalsIgnoreCase("bossbar")) {
            return bossbarCmd(sender, args);
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use Dung.");
            return true;
        }
        switch (label.toLowerCase()) {
            case "shop": return meta.shopCmd(p, args);
            case "upgrades": return meta.upgradesCmd(p, args);
            case "stash": return meta.stashCmd(p, args);
            case "salvage": return meta.salvageCmd(p, args);
            case "party": return meta.partyCmd(p, args);
            case "balance": meta.balance(p, args); return true;
            case "leaderboard": meta.leaderboard(p, args); return true;
            default: return dungCmd(p, args);
        }
    }

    /** Clear any leaked boss HP bars (keyed `dung_boss_*`). Runnable from console to fix a stuck bar. */
    private boolean bossbarCmd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dung.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        int removed = 0;
        java.util.List<org.bukkit.boss.KeyedBossBar> stuck = new java.util.ArrayList<>();
        java.util.Iterator<org.bukkit.boss.KeyedBossBar> it = Bukkit.getBossBars();
        while (it.hasNext()) {
            org.bukkit.boss.KeyedBossBar bar = it.next();
            if (bar.getKey().getKey().startsWith("dung_boss_")) stuck.add(bar);
        }
        for (org.bukkit.boss.KeyedBossBar k : stuck) {
            k.removeAll();
            k.setVisible(false);
            Bukkit.removeBossBar(k.getKey());
            removed++;
        }
        sender.sendMessage("§aCleared " + removed + " stuck boss bar" + (removed == 1 ? "" : "s") + ".");
        return true;
    }

    /** Wipe all player data (saves.yml, plots.yml), turn off natural mob spawning, and
     *  broadcast the reset. Requires dung.admin permission. */
    private void resetCmd(Player p) {
        // End all active runs
        for (DungeonInstance di : plugin.game().instances()) {
            di.endRun();
        }
        // Clear all player data in MetaManager
        plugin.meta().clearAll();
        // Clear all plot data
        plugin.plotManager().clearAll();
        // Turn off natural mob spawning in all worlds
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            w.setGameRule(org.bukkit.GameRules.SPAWN_MOBS, false);
        }
        Bukkit.broadcastMessage("§c§lAll player data has been reset. Natural mob spawning disabled.");
    }

    // ---------- /dung <sub> ----------

    private boolean dungCmd(Player p, String[] args) {
        GameManager gm = plugin.game();
        String sub = args.length > 0 ? args[0].toLowerCase() : "help";
        switch (sub) {
            case "start":
                if (gm.isInInstance(p)) {
                    p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands(com.lieyabull.dung.lang.Lang.forPlayer(p, "run.alreadyInLeaveFirst")));
                    return true;
                }
                // Check if player is in a party
                Party party = gm.partyManager().partyOf(p);
                if (party != null) {
                    // Party leader starts the run for the whole party
                    if (!party.isLeader(p.getUniqueId())) {
                        p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "run.leaderOnlyStart"));
                        return true;
                    }
                    if (!gm.startRun(party, System.nanoTime())) return true; // reason already sent
                    party.broadcastLocalized("run.started");
                } else {
                    // Solo: create a single-player party
                    party = gm.partyManager().createParty(p);
                    if (!gm.startRun(party, System.nanoTime())) {
                        gm.partyManager().cleanupAfterLeave(p); // don't leave an empty solo party behind
                        return true; // reason already sent
                    }
                    p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "run.started"));
                }
                return true;
            case "descend": {
                DungeonInstance di = gm.instanceOf(p);
                if (di == null) { p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "run.startFirst")); return true; }
                di.descend(p);
                return true;
            }
            case "shieldswitch": {
                DungeonInstance di = gm.instanceOf(p);
                if (di == null) { p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "run.startFirst")); return true; }
                di.doShieldSwitch(p);
                return true;
            }
            case "leave": {
                DungeonInstance di = gm.instanceOf(p);
                if (di != null) {
                    gm.leaveInstance(p);
                    p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "run.left"));
                } else {
                    p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "run.noActive"));
                }
                return true;
            }
            case "party": return meta.partyCmd(p, args);
            case "shop": return meta.shopCmd(p, args);
            case "upgrades": return meta.upgradesCmd(p, args);
            case "salvage": return meta.salvageCmd(p, args);
            case "balance": meta.balance(p, args); return true;
            case "stats": stats(p); return true;
            case "class": classCmd(p, args); return true;
            case "give":
                if (!p.hasPermission("dung.admin")) { p.sendMessage("§cNo permission."); return true; }
                give(p, args);
                return true;
            case "lobbykit":
                if (!p.isOp()) { p.sendMessage("§cNo permission."); return true; }
                lobbyKit(p);
                return true;
            case "stop":
                if (!p.hasPermission("dung.admin")) { p.sendMessage("§cNo permission."); return true; }
                Bukkit.broadcastMessage("§c§lServer is stopping...");
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.shutdown());
                return true;
            case "reset":
                if (!p.hasPermission("dung.admin")) { p.sendMessage("§cNo permission."); return true; }
                resetCmd(p);
                return true;
            case "forceboss":
                if (!p.hasPermission("dung.admin")) { p.sendMessage("§cNo permission."); return true; }
                return forceBossCmd(p, args);
            case "room":
                if (!p.hasPermission("dung.admin")) { p.sendMessage("§cNo permission."); return true; }
                return roomCmd(p, args);
            case "tutorial":
                tutorialCmd(p, args);
                return true;
            case "help":
            default:
                ChatUI.startPrompt(p);
                return true;
        }
    }

    // ---------- /dung forceboss ----------

    /** /dung forceboss <warden|grovekeeper|random> — force the boss type for the next floor
     *  in the dungeon instance whose world the player is standing in. Requires dung.admin. */
    private boolean forceBossCmd(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage("§cUsage: /dung forceboss <warden|grovekeeper|random>");
            return true;
        }
        String type = args[1].toLowerCase();
        if (!type.equals("warden") && !type.equals("grovekeeper") && !type.equals("random")) {
            p.sendMessage("§cInvalid boss type. Use: warden, grovekeeper, or random.");
            return true;
        }
        // Find the dungeon instance for the world the player is in
        DungeonInstance di = plugin.game().instanceByWorld(p.getWorld());
        if (di == null) {
            p.sendMessage("§cYou are not in a dungeon run world.");
            return true;
        }
        if (!di.isRunning()) {
            p.sendMessage("§cThe dungeon run in this world is not active.");
            return true;
        }
        if (type.equals("random")) {
            di.setForcedBossType(null);
            p.sendMessage("§aBoss type reset to random for the next floor.");
        } else {
            di.setForcedBossType(type);
            p.sendMessage("§aNext floor will spawn §2" + type.substring(0, 1).toUpperCase() + type.substring(1) + "§a.");
        }
        return true;
    }

    // ---------- /dung room ----------

    /** Auto-generate &lt;id&gt;.yml + &lt;id&gt;.schem from the player's WorldEdit clipboard: reads the
     *  {@code /copy}ed room, extracts marker signs, and writes a metadata sidecar (bounds, spawn
     *  floor, PLAYER_SPAWN) into {@code plugins/Dung/structures/<id>/}. The room id is the schematic
     *  name; both files share that basename. Doorways/corridors are NOT stored here — the generator
     *  carves them procedurally at build time on the shared corridor line. */
    private boolean genCmd(Player p, String[] args, com.lieyabull.dung.structure.StructureRegistry reg) {
        if (args.length < 3) {
            p.sendMessage("§cUsage: /dung room gen <id> [types...]   (make a WorldEdit selection and /copy first)");
            return true;
        }
        com.sk89q.worldedit.bukkit.WorldEditPlugin wep =
                (com.sk89q.worldedit.bukkit.WorldEditPlugin) Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (wep == null) { p.sendMessage("§cWorldEdit is not loaded."); return true; }
        com.sk89q.worldedit.LocalSession sess = wep.getSession(p);
        com.sk89q.worldedit.session.ClipboardHolder holder;
        try {
            holder = sess.getClipboard();
        } catch (com.sk89q.worldedit.EmptyClipboardException e) {
            holder = null;
        }
        com.sk89q.worldedit.extent.clipboard.Clipboard cb = holder == null ? null : holder.getClipboard();
        if (cb == null) { p.sendMessage("§cMake a WorldEdit selection and run //copy first."); return true; }

        com.sk89q.worldedit.math.BlockVector3 min = cb.getMinimumPoint();
        com.sk89q.worldedit.math.BlockVector3 max = cb.getMaximumPoint();
        com.lieyabull.dung.room.RoomBounds total = new com.lieyabull.dung.room.RoomBounds(
                min.x(), min.y(), min.z(),
                max.x(), max.y(), max.z());

        com.lieyabull.dung.structure.StructureDefinition def = new com.lieyabull.dung.structure.StructureDefinition();
        def.id = args[2].toLowerCase();
        def.schematic = def.id + ".schem";
        if (args.length > 3) for (int i = 3; i < args.length; i++) def.types.add(args[i].toUpperCase());
        if (def.types.isEmpty()) def.types.add("COMBAT");
        def.bounds.add(total);
        int floorY = total.minY + 1;

        // Marker signs -> markers / spawn floors. A written sign whose first line is a known token
        // (e.g. [PLAYER_SPAWN], [SHOPKEEPER], [SPAWN_FLOOR]) becomes that marker at the sign's position,
        // and the sign is stripped out of the schematic so it never appears in the generated room.
        java.util.List<com.lieyabull.dung.structure.SignScanner.Sign> signs =
                com.lieyabull.dung.structure.SignScanner.scan(cb);
        java.util.Set<com.sk89q.worldedit.math.BlockVector3> signBlocks = new java.util.LinkedHashSet<>();
        boolean hasPlayerSpawnSign = false;
        for (com.lieyabull.dung.structure.SignScanner.Sign s : signs) {
            boolean used = false;
            switch (s.text()) {
                case "PLAYER_SPAWN" -> {
                    def.markers.add(new com.lieyabull.dung.room.RoomMarker(
                            com.lieyabull.dung.room.RoomMarkerType.PLAYER_SPAWN, s.x(), s.y(), s.z(), "sign"));
                    hasPlayerSpawnSign = true;
                    used = true;
                }
                case "SHOPKEEPER" -> {
                    def.markers.add(new com.lieyabull.dung.room.RoomMarker(
                            com.lieyabull.dung.room.RoomMarkerType.SHOPKEEPER, s.x(), s.y(), s.z(), "sign"));
                    used = true;
                }
                case "LOOT" -> {
                    def.markers.add(new com.lieyabull.dung.room.RoomMarker(
                            com.lieyabull.dung.room.RoomMarkerType.LOOT, s.x(), s.y(), s.z(), "sign"));
                    used = true;
                }
                case "HAZARD" -> {
                    def.markers.add(new com.lieyabull.dung.room.RoomMarker(
                            com.lieyabull.dung.room.RoomMarkerType.HAZARD, s.x(), s.y(), s.z(), "sign"));
                    used = true;
                }
                case "MECHANIC" -> {
                    def.markers.add(new com.lieyabull.dung.room.RoomMarker(
                            com.lieyabull.dung.room.RoomMarkerType.MECHANIC, s.x(), s.y(), s.z(), "sign"));
                    used = true;
                }
                case "SPECIAL" -> {
                    def.markers.add(new com.lieyabull.dung.room.RoomMarker(
                            com.lieyabull.dung.room.RoomMarkerType.SPECIAL, s.x(), s.y(), s.z(), "sign"));
                    used = true;
                }
                case "SPAWN_FLOOR" -> {
                    def.spawnFloors.add(new com.lieyabull.dung.room.SpawnFloor(
                            s.x(), s.y(), s.z(), s.x(), s.y(), s.z()));
                    used = true;
                }
                default -> { }
            }
            if (used) signBlocks.add(com.sk89q.worldedit.math.BlockVector3.at(s.x(), s.y(), s.z()));
        }

        def.spawnFloors.add(new com.lieyabull.dung.room.SpawnFloor(
                total.minX + 1, floorY, total.minZ + 1,
                Math.max(total.minX + 1, total.maxX - 1), floorY,
                Math.max(total.minZ + 1, total.maxZ - 1)));
        if (!hasPlayerSpawnSign) {
            def.markers.add(new com.lieyabull.dung.room.RoomMarker(
                    com.lieyabull.dung.room.RoomMarkerType.PLAYER_SPAWN,
                    (total.minX + total.maxX) / 2, floorY, (total.minZ + total.maxZ) / 2, "start"));
        }

        com.sk89q.worldedit.extent.clipboard.Clipboard toSave = stripMarkerSigns(cb, signBlocks);
        try {
            java.io.File dir = new java.io.File(plugin.getDataFolder(), "structures/" + def.id);
            dir.mkdirs();
            if (!com.lieyabull.dung.structure.StructureWorldEdit.save(toSave,
                    new java.io.File(dir, def.schematic))) {
                p.sendMessage("§cCould not write " + def.schematic + ".");
                return true;
            }
            java.nio.file.Files.write(new java.io.File(dir, def.id + ".yml").toPath(),
                    com.lieyabull.dung.structure.StructureMetadata.dump(def).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            p.sendMessage("§cFailed to write structure files: " + e.getMessage());
            return true;
        }
        plugin.structures().reload();
        p.sendMessage("§aSaved structure '§f" + def.id + "§a' (" + def.schematic + " + " + def.id
                + ".yml). Doorways/corridors are carved procedurally at generation — no door data stored.");
        if (signs.isEmpty()) {
            p.sendMessage("§7No marker signs found. Place wall signs like §f[PLAYER_SPAWN]§7, §f[SHOPKEEPER]§7, "
                    + "§f[LOOT]§7, §f[HAZARD]§7, §f[MECHANIC]§7, §f[SPECIAL]§7, §f[SPAWN_FLOOR]§7 and re-run.");
        } else {
            p.sendMessage("§aRead §f" + signs.size() + "§a sign(s): "
                    + String.join("§7, §f", signs.stream().map(s -> s.text()).toList()));
            p.sendMessage("§7Used §f" + signBlocks.size() + "§7 as markers (stripped from the schematic); the rest were left in place.");
        }
        p.sendMessage("§7Verify with: §f/dung room validate " + def.id + "§7  and  §f/dung room preview " + def.id + " 0");
        return true;
    }

    /** Return a copy of {@code src} with every block at a marker-sign position replaced by air, so marker
     *  signs never appear in the generated room. Returns {@code src} unchanged when no signs are stripped. */
    private static com.sk89q.worldedit.extent.clipboard.Clipboard stripMarkerSigns(
            com.sk89q.worldedit.extent.clipboard.Clipboard src,
            java.util.Set<com.sk89q.worldedit.math.BlockVector3> positions) {
        if (positions.isEmpty()) return src;
        com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard out =
                new com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard(src.getRegion());
        out.setOrigin(src.getOrigin());
        com.sk89q.worldedit.world.block.BlockState air =
                com.sk89q.worldedit.world.block.BlockTypes.AIR.getDefaultState();
        for (com.sk89q.worldedit.math.BlockVector3 pt : src.getRegion()) {
            out.setBlock(pt, positions.contains(pt) ? air : src.getBlock(pt));
        }
        return out;
    }

    private boolean roomCmd(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage("§7Room structure commands: §fgen <id> [types]§7, §flist§7, §freload§7, §fvalidate <id>§7, §fpreview <id> [0-3]");
            return true;
        }
        com.lieyabull.dung.structure.StructureRegistry reg = plugin.structures().registry();
        switch (args[1].toLowerCase()) {
            case "list": {
                java.util.List<com.lieyabull.dung.structure.StructureDefinition> all = reg.all();
                p.sendMessage("§6--- Structures (" + all.size() + ") ---");
                for (com.lieyabull.dung.structure.StructureDefinition s : all) {
                    p.sendMessage("  §f" + s.id + "§7 [" + String.join(",", s.types) + "]"
                            + (s.description.isEmpty() ? "" : " §8" + s.description));
                }
                return true;
            }
            case "gen":
                return genCmd(p, args, reg);
            case "reload":
                int n = plugin.structures().reload();
                p.sendMessage("§aReloaded structure library: §f" + n + "§a structure(s) registered.");
                return true;
            case "validate": {
                if (args.length < 3) { p.sendMessage("§cUsage: /dung room validate <id>"); return true; }
                com.lieyabull.dung.structure.StructureRegistry.Registered r = reg.byId(args[2]);
                if (r == null) { p.sendMessage("§cUnknown structure id."); return true; }
                com.lieyabull.dung.structure.StructureValidator.Result res =
                        com.lieyabull.dung.structure.StructureValidator.validate(
                                r.definition(), com.lieyabull.dung.structure.StructureWorldEdit.blockLookup(r.clipboard()));
                if (res.hasErrors()) {
                    p.sendMessage("§c'" + args[2] + "' INVALID:");
                    for (com.lieyabull.dung.room.RoomValidationIssue i : res.issues)
                        if (i.level == com.lieyabull.dung.room.RoomValidationIssue.Level.ERROR)
                            p.sendMessage("  §c" + i.message);
                } else {
                    p.sendMessage("§a'" + args[2] + "' VALID (" + res.issues.size() + " total checks).");
                }
                return true;
            }
            case "preview": {
                if (args.length < 3) { p.sendMessage("§cUsage: /dung room preview <id> [rotation 0-3]"); return true; }
                com.lieyabull.dung.structure.StructureRegistry.Registered r = reg.byId(args[2]);
                if (r == null) { p.sendMessage("§cUnknown structure id."); return true; }
                int rot = args.length > 3 ? Integer.parseInt(args[3]) : 0;
                if (rot < 0 || rot > 3) rot = 0;
                org.bukkit.Location l = p.getLocation();
                int bx = l.getBlockX(), by = l.getBlockY(), bz = l.getBlockZ();
                com.lieyabull.dung.room.RoomBounds t = r.definition().total();
                com.lieyabull.dung.structure.StructureWorldEdit.paste(
                        l.getWorld(), r.clipboard(), bx - t.minX, by - t.minY, bz - t.minZ, rot);
                p.sendMessage("§aPasted preview of '" + args[2] + "' (rotation " + rot + ").");
                return true;
            }
            default:
                p.sendMessage("§7Room structure commands: §flist§7, §freload§7, §fvalidate <id>§7, §fpreview <id> [0-3]");
                return true;
        }
    }

    // ---------- /dung tutorial ----------

    /** Show how to author a schematic for the chosen room type (/dung tutorial <type>), or list the
     *  available types. Informational, available to any player. */
    private void tutorialCmd(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage("§6--- Room Schematic Tutorials ---");
            p.sendMessage("§7Usage: §f/dung tutorial <roomtype>");
            p.sendMessage("§7Shows how to build the schematic for one room type.");
            for (com.lieyabull.dung.dungeon.RoomType t : com.lieyabull.dung.dungeon.RoomType.values()) {
                p.sendMessage("  §f" + t.name().toLowerCase() + "§7 - " + tutorialBlurb(t));
            }
            p.sendMessage("§8For example: §f/dung tutorial combat");
            return;
        }
        com.lieyabull.dung.dungeon.RoomType type = null;
        for (com.lieyabull.dung.dungeon.RoomType t : com.lieyabull.dung.dungeon.RoomType.values()) {
            if (t.name().equalsIgnoreCase(args[1])) { type = t; break; }
        }
        if (type == null) {
            p.sendMessage("§cUnknown room type '§f" + args[1] + "§c'. Use §f/dung tutorial§c to list them.");
            return;
        }
        p.sendMessage("§6--- " + type.label + " Room — Schematic ---");
        for (String line : tutorialText(type)) p.sendMessage(line);
    }

    /** One-line blurb used in the /dung tutorial listing. */
    private static String tutorialBlurb(com.lieyabull.dung.dungeon.RoomType type) {
        return switch (type) {
            case START -> "safe spawn room";
            case COMBAT -> "clear-all enemies";
            case ELITE -> "harder combat";
            case TREASURE -> "free loot";
            case SHOP -> "vendor room";
            case UPGRADE -> "workstation room";
            case SECRET -> "hidden room (procedural)";
            case LOCKED -> "keyed room (procedural)";
            case BOSS -> "floor boss";
        };
    }

    /** The tutorial copy for one room type: what its schematic needs, plus the common authoring steps.
     *  Each entry is one line of guidance. */
    private static String[] tutorialText(com.lieyabull.dung.dungeon.RoomType type) {
        java.util.List<String> out = new java.util.ArrayList<>();
        switch (type) {
            case START -> {
                out.add("§fThe safe spawn room of every floor — no enemies spawn here.");
                out.add("§7Required marker: §f[PLAYER_SPAWN]§7 (where players appear when they enter).");
                out.add("§7Keep it compact, flat, well-lit, and free of hazards.");
            }
            case COMBAT -> {
                out.add("§fThe standard enemy-clearing room.");
                out.add("§7Required marker: §f[PLAYER_SPAWN]§7, plus a §f[SPAWN_FLOOR]§7 region for enemies.");
                out.add("§7Enemies spawn on the §f[SPAWN_FLOOR]§7 tiles — they need a solid floor below and 2+ air blocks above.");
                out.add("§7Leave clear, open floor so enemies can reach you; add cover for interest.");
            }
            case ELITE -> {
                out.add("§fA tougher combat room: more and stronger enemies, better drops.");
                out.add("§7Same markers as COMBAT: §f[PLAYER_SPAWN]§7 + §f[SPAWN_FLOOR]§7 region(s).");
                out.add("§7§f[SPAWN_FLOOR]§7 regions control where the stronger enemies drop — spread them out.");
                out.add("§7Pillars, split walls, or hazard pits (mark them §f[HAZARD]§7) make the fight more interesting.");
            }
            case TREASURE -> {
                out.add("§fA free-loot room — pedestal gear spawns around the room center.");
                out.add("§7Required marker: §f[PLAYER_SPAWN]§7 (it anchors the loot pedestals).");
                out.add("§7Keep the center clear so pedestals and pickups are reachable.");
            }
            case SHOP -> {
                out.add("§fThe vendor room — the shopkeeper villager spawns at your §f[SHOPKEEPER]§7 marker.");
                out.add("§7Required markers: §f[PLAYER_SPAWN]§7 AND §f[SHOPKEEPER]§7 — validation rejects a SHOP room without a shopkeeper.");
                out.add("§7Put the shopkeeper against a back wall with a clear floor in front of it.");
            }
            case UPGRADE -> {
                out.add("§fThe progression room — the five workstations spawn procedurally around the room center.");
                out.add("§7Required marker: §f[PLAYER_SPAWN]§7.");
                out.add("§7Keep the center and the back wall clear so the workstation row and name tags don't clip your build.");
            }
            case SECRET -> {
                out.add("§fHidden room behind a cracked wall — always built procedurally.");
                out.add("§7SECRET rooms never use a schematic; no §f[PLAYER_SPAWN]§7 is required (validation skips it).");
                out.add("§7Nothing to author — the generator builds the room and the bombable wall for you.");
            }
            case LOCKED -> {
                out.add("§fKey-locked room — always built procedurally.");
                out.add("§7LOCKED rooms never use a schematic; the generator adds the iron-door barrier and keyed entry.");
                out.add("§7Nothing to author for the room itself.");
            }
            case BOSS -> {
                out.add("§fThe floor boss arena.");
                out.add("§7Required marker: §f[PLAYER_SPAWN]§7.");
                out.add("§7Keep it open and wide — the boss and its adds need room to move.");
            }
        }
        out.add("");
        out.add("§8— Authoring steps (every buildable type) —");
        out.add("§71. Build the room in the world: sealed walls, floor, ceiling, interior props.");
        out.add("§72. Leave doorways out — the generator carves openings on a fixed corridor line at build time.");
        out.add("§73. Select the build and run §f//copy§7 (WorldEdit).");
        out.add("§74. Place marker wall signs inside the selection: §f[PLAYER_SPAWN]§7, §f[SPAWN_FLOOR]§7, §f[SHOPKEEPER]§7, §f[LOOT]§7, §f[HAZARD]§7, §f[MECHANIC]§7, §f[SPECIAL]§7.");
        out.add("§75. Run §f/dung room gen <id> <TYPE>§7 — writes <id>.schem + <id>.yml to plugins/Dung/structures/.");
        out.add("§76. Verify with §f/dung room validate <id>§7 and §f/dung room preview <id> 0§7.");
        return out.toArray(new String[0]);
    }

    // ---------- /dung lobbykit ----------

    /** The lobby-builder palette: dark stone core, gold accents, warm wood, upgrade-room purples,
     *  treasure-room quartz, and softening greenery for the void lobby world. */
    private static final String[][] LOBBY_KIT = {
            {"polished_blackstone", "64"}, {"polished_blackstone_bricks", "64"},
            {"chiseled_polished_blackstone", "64"}, {"blackstone_slab", "64"},
            {"blackstone_wall", "64"}, {"polished_blackstone_brick_wall", "64"},
            {"gilded_blackstone", "16"}, {"gold_block", "16"},
            {"lantern", "32"}, {"soul_lantern", "32"}, {"iron_chain", "32"}, {"end_rod", "32"},
            {"crimson_planks", "64"}, {"crimson_slab", "64"}, {"spruce_planks", "64"},
            {"spruce_stairs", "64"}, {"dark_oak_door", "16"}, {"crimson_fence", "32"},
            {"amethyst_block", "32"}, {"purpur_block", "32"}, {"purpur_pillar", "16"},
            {"quartz_block", "32"}, {"quartz_pillar", "16"}, {"emerald_block", "8"},
            {"moss_block", "64"}, {"moss_carpet", "64"},
            {"azalea", "16"}, {"flowering_azalea", "16"}, {"spore_blossom", "16"},
            {"glow_berries", "16"}, {"hanging_roots", "32"},
            {"big_dripleaf", "16"}, {"small_dripleaf", "16"},
            {"lilac", "16"}, {"peony", "16"}, {"rose_bush", "16"},
            {"grass_block", "64"}, {"coarse_dirt", "64"}, {"gravel", "64"},
            {"andesite", "64"}, {"polished_andesite", "64"},
            {"stone_brick_stairs", "64"}, {"mossy_stone_bricks", "64"}, {"cracked_stone_bricks", "16"},
            {"sea_lantern", "16"}, {"shroomlight", "32"}, {"ochre_froglight", "32"},
            {"item_frame", "16"}, {"armor_stand", "8"}, {"name_tag", "4"},
            {"oak_sign", "16"}, {"flower_pot", "16"},
    };

    /** /dung lobbykit (op-only): hands the caller the full lobby-building palette packed into
     *  pre-filled shulker boxes (as many as needed), added straight to their inventory. */
    private void lobbyKit(Player p) {
        java.util.List<org.bukkit.inventory.ItemStack> entries = new java.util.ArrayList<>();
        int skipped = 0;
        for (String[] e : LOBBY_KIT) {
            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(e[0].toUpperCase());
            if (mat == null) {
                // Skip rather than abort — a single renamed material shouldn't kill the kit
                p.sendMessage("§7Skipping unknown kit material: §f" + e[0]);
                skipped++;
                continue;
            }
            org.bukkit.inventory.ItemStack s = new org.bukkit.inventory.ItemStack(mat);
            s.setAmount(Integer.parseInt(e[1]));
            entries.add(s);
        }
        // Pack entries into shulker boxes (27 slots each), named per box.
        java.util.List<org.bukkit.inventory.ItemStack> shulkers = new java.util.ArrayList<>();
        int boxNum = 1;
        for (int i = 0; i < entries.size(); i += 27) {
            org.bukkit.inventory.ItemStack boxItem = new org.bukkit.inventory.ItemStack(org.bukkit.Material.BLACK_SHULKER_BOX);
            org.bukkit.block.ShulkerBox box = ((org.bukkit.inventory.meta.BlockStateMeta) boxItem.getItemMeta())
                    .getBlockState() instanceof org.bukkit.block.ShulkerBox sb ? sb : null;
            if (box == null) { p.sendMessage("§cFailed to create shulker box state."); return; }
            for (int j = i; j < Math.min(i + 27, entries.size()); j++) box.getInventory().addItem(entries.get(j));
            var bsm = (org.bukkit.inventory.meta.BlockStateMeta) boxItem.getItemMeta();
            bsm.setBlockState(box);
            bsm.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize("§8Lobby Kit §7(" + boxNum + ")"));
            boxItem.setItemMeta(bsm);
            shulkers.add(boxItem);
            boxNum++;
        }
        var leftover = p.getInventory().addItem(shulkers.toArray(new org.bukkit.inventory.ItemStack[0]));
        leftover.values().forEach(drop -> p.getWorld().dropItemNaturally(p.getLocation(), drop));
        p.sendMessage("§aGave you §f" + shulkers.size() + "§a Lobby Kit shulker"
                + (shulkers.size() == 1 ? "" : "s") + "§a — happy building!");
        if (skipped > 0) {
            p.sendMessage("§7(§f" + skipped + "§7 kit material(s) skipped — renamed in this Minecraft version.)");
        }
    }

    private static final String[] DUNG_SUBS = {
            "start", "leave", "descend", "stats", "class", "give", "shieldswitch",
            "party", "shop", "upgrades", "salvage", "balance", "bossbar", "stop", "reset",
            "forceboss", "room", "tutorial", "help"
    };
    private static final String[] ROOM_SUBS = {"list", "reload", "validate", "preview", "gen"};
    private static final String[] TUTORIAL_SUBS = {"start", "combat", "elite", "treasure", "shop", "upgrade", "secret", "locked", "boss"};
    private static final String[] PARTY_SUBS = {"create", "invite", "accept", "decline", "leave", "kick", "disband", "info"};
    private static final String[] CLASSES = {"warrior", "mage", "ranger"};
    private static final String[] GIVE_TYPES = {"rareweapon", "heal", "coins"};
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player p)) return List.of();
        String label = cmd.getName().toLowerCase();
        if (label.equals("dung") || label.equals("dungeon")) {
            if (args.length == 1) return filter(DUNG_SUBS, args[0]);
            if (args.length == 2) {
                return switch (args[0].toLowerCase()) {
                    case "give" -> filter(GIVE_TYPES, args[1]);
                    case "class" -> filter(CLASSES, args[1]);
                    case "party" -> filter(PARTY_SUBS, args[1]);
                    case "room" -> filter(ROOM_SUBS, args[1]);
                    case "tutorial" -> filter(TUTORIAL_SUBS, args[1]);
                    case "forceboss" -> filter(new String[]{"warden", "grovekeeper", "random"}, args[1]);
                    default -> List.of();
                };
            }
            if (args.length >= 3 && args[0].equalsIgnoreCase("party")) {
                String sub = args[1].toLowerCase();
                if (sub.equals("invite") || sub.equals("kick")) return playerNames(args[2]);
            }
            return List.of();
        }
        // shop, upgrades, balance: no arguments
        return List.of();
    }

    /** Return the options in {@code opts} that start with the given (case-insensitive) prefix. */
    private static List<String> filter(String[] opts, String prefix) {
        String q = prefix.toLowerCase();
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (String o : opts) {
            if (o.toLowerCase().startsWith(q)) out.add(o);
        }
        return out;
    }

    /** Return the names of online players starting with the given prefix. */
    private static List<String> playerNames(String prefix) {
        String q = prefix.toLowerCase();
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (pl.getName().toLowerCase().startsWith(q)) out.add(pl.getName());
        }
        return out;
    }

    // ---------- existing: stats / class / give ----------

    private void stats(Player p) {
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "stats.header", p.getName()));
        p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "stats.class", TextUtil.capital(prof.classId)));
        p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "stats.currency", prof.persistentCoins, prof.shards));
        p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "stats.deathsFloor", prof.deaths, prof.bestFloor, prof.kills));
        p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "stats.clears", prof.clears));
    }

    private void classCmd(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "dung.classHint"));
            return;
        }
        String c = args[1].toLowerCase();
        if (!c.equals("warrior") && !c.equals("mage") && !c.equals("ranger")) {
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "dung.unknownClass"));
            return;
        }
        plugin.meta().profile(p.getUniqueId()).classId = c;
        plugin.meta().save(); // persist immediately so a crash/restart can't roll the choice back
        p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "dung.classSet", TextUtil.capital(c)));
    }

    private void give(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§7Usage: /dung give <rareweapon|heal|coins>")); return; }
        switch (args[1].toLowerCase()) {
            case "rareweapon":
                // free debug spawn (no coin cost): real purchases go through /shop
                var debugWep = GearFactory.markPersistent(ItemPool.randomWeapon(2));
                GearFactory.localizeFor(debugWep, p);
                p.getInventory().addItem(debugWep);
                p.sendMessage("§aDebug: spawned a weapon (persists through death).");
                break;
            case "heal":
                p.setHealth(20);
                p.sendMessage("§aHealed.");
                break;
            case "coins":
                p.getInventory().addItem(new ItemStack(org.bukkit.Material.GOLD_NUGGET, 10));
                p.sendMessage("§e+10 coins (run).");
                break;
            default:
                p.sendMessage("§7Unknown give target.");
        }
    }

    private String tag(ItemStack s, String key) {
        if (s == null || s.getType() == org.bukkit.Material.AIR || s.getItemMeta() == null) return null;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        String v = pdc.get(org.bukkit.NamespacedKey.minecraft(key), org.bukkit.persistence.PersistentDataType.STRING);
        return v;
    }

}
