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
    private final Map<UUID, Long> lastPartyInvite = new HashMap<>();
    private static final long PARTY_INVITE_COOLDOWN_MS = 5000;

    public DungCommand(Dung plugin) {
        this.plugin = plugin;
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
            case "shop": return shopCmd(p, args);
            case "upgrades": return upgradesCmd(p, args);
            case "stash": return stashCmd(p, args);
            case "salvage": return salvageCmd(p, args);
            case "party": return partyCmd(p, args);
            case "balance": balance(p, args); return true;
            case "leaderboard": leaderboard(p, args); return true;
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
                    p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cYou're already in a run. Use /dung leave first."));
                    return true;
                }
                // Check if player is in a party
                Party party = gm.partyManager().partyOf(p);
                if (party != null) {
                    // Party leader starts the run for the whole party
                    if (!party.isLeader(p.getUniqueId())) {
                        p.sendMessage("§cOnly the party leader can start a run.");
                        return true;
                    }
                    if (!gm.startRun(party, System.nanoTime())) return true; // reason already sent
                    party.broadcast("§aRun started! Clear rooms, gear up, defeat the Warden.");
                } else {
                    // Solo: create a single-player party
                    party = gm.partyManager().createParty(p);
                    if (!gm.startRun(party, System.nanoTime())) {
                        gm.partyManager().cleanupAfterLeave(p); // don't leave an empty solo party behind
                        return true; // reason already sent
                    }
                    p.sendMessage("§aRun started! Clear rooms, gear up, defeat the Warden.");
                }
                return true;
            case "descend": {
                DungeonInstance di = gm.instanceOf(p);
                if (di == null) { p.sendMessage("§cStart a run first."); return true; }
                di.descend(p);
                return true;
            }
            case "shieldswitch": {
                DungeonInstance di = gm.instanceOf(p);
                if (di == null) { p.sendMessage("§cStart a run first."); return true; }
                di.doShieldSwitch(p);
                return true;
            }
            case "leave": {
                DungeonInstance di = gm.instanceOf(p);
                if (di != null) {
                    gm.leaveInstance(p);
                    p.sendMessage("§7Left the run.");
                } else {
                    p.sendMessage("§cNo active run.");
                }
                return true;
            }
            case "party": return partyCmd(p, args);
            case "shop": return shopCmd(p, args);
            case "upgrades": return upgradesCmd(p, args);
            case "salvage": return salvageCmd(p, args);
            case "balance": balance(p, args); return true;
            case "stats": stats(p); return true;
            case "class": classCmd(p, args); return true;
            case "give":
                if (!p.hasPermission("dung.admin")) { p.sendMessage("§cNo permission."); return true; }
                give(p, args);
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

    // ---------- /party ----------

    private boolean partyCmd(Player p, String[] args) {
        PartyManager pm = plugin.game().partyManager();
        GameManager gm = plugin.game();
        if (args.length == 0) {
            Party party = pm.partyOf(p);
            if (party == null) {
                p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§7You are not in a party. Use §f/party create§7 to start one."));
                p.sendMessage("§7Commands: §fcreate, invite <player>, accept, decline, leave, kick <player>, disband, info");
                return true;
            }
            p.sendMessage("§6--- Party ---");
            p.sendMessage("§7Leader: §f" + Bukkit.getOfflinePlayer(party.leader()).getName());
            p.sendMessage("§7Members (" + party.size() + "/" + Party.MAX_SIZE + "):");
            for (java.util.UUID uid : party.members()) {
                String name = Bukkit.getOfflinePlayer(uid).getName();
                String tag = uid.equals(party.leader()) ? " §6(Leader)" : "";
                p.sendMessage("  §7- §f" + name + tag);
            }
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create": {
                if (pm.partyOf(p) != null) {
                    p.sendMessage("§cYou're already in a party. Leave first.");
                    return true;
                }
                pm.createParty(p);
                p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§aParty created! Invite players with §f/party invite <player>"));
                return true;
            }
            case "invite": {
                if (args.length < 2) { p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cUsage: /party invite <player>")); return true; }
                long now = System.currentTimeMillis();
                Long last = lastPartyInvite.get(p.getUniqueId());
                if (last != null && now - last < PARTY_INVITE_COOLDOWN_MS) {
                    p.sendMessage("§cYou can't invite yet. Wait " + (int) Math.ceil((PARTY_INVITE_COOLDOWN_MS - (now - last)) / 1000.0) + "s.");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { p.sendMessage("§cPlayer not found."); return true; }
                if (target.equals(p)) { p.sendMessage("§cYou can't invite yourself."); return true; }
                if (gm.isInInstance(p)) {
                    p.sendMessage("§cYou can't invite while your party is in a run.");
                    return true;
                }
                if (pm.invite(p, target)) {
                    lastPartyInvite.put(p.getUniqueId(), now);
                    p.sendMessage("§aInvited " + target.getName() + " to the party.");
                    target.sendMessage(
                            com.lieyabull.dung.ui.ChatUI.command("§a[Accept]", "/party accept", "Join the party")
                                    .append(Component.text("  "))
                                    .append(com.lieyabull.dung.ui.ChatUI.command("§c[Decline]", "/party decline", "Decline the invite"))
                                    .hoverEvent(null) // remove hover from the container
                    );
                    target.sendMessage("§a" + p.getName() + " invited you to a party!");
                } else {
                    p.sendMessage("§cCould not invite. They may already be in a party, or the party is full.");
                }
                return true;
            }
            case "accept": {
                if (gm.isInInstance(p)) {
                    p.sendMessage("§cYou can't join a party while you're in a run.");
                    return true;
                }
                UUID inviterId = pm.getInviter(p);
                Player inviter = inviterId != null ? Bukkit.getPlayer(inviterId) : null;
                if (inviter != null && gm.isInInstance(inviter)) {
                    p.sendMessage("§cThat party has already started a run.");
                    return true;
                }
                if (pm.acceptInvite(p)) {
                    p.sendMessage("§aYou joined the party!");
                } else {
                    p.sendMessage("§cNo pending invite or party is full.");
                }
                return true;
            }
            case "decline": {
                pm.declineInvite(p);
                p.sendMessage("§7Invite declined.");
                return true;
            }
            case "leave": {
                pm.leaveParty(p);
                DungeonInstance leaveDi = gm.instanceOf(p);
                if (leaveDi != null) leaveDi.removePlayer(p);
                p.sendMessage("§7You left the party.");
                return true;
            }
            case "kick": {
                if (args.length < 2) { p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cUsage: /party kick <player>")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { p.sendMessage("§cPlayer not found."); return true; }
                if (pm.kick(p, target)) {
                    DungeonInstance kickDi = gm.instanceOf(target);
                    if (kickDi != null) kickDi.removePlayer(target);
                    p.sendMessage("§aKicked " + target.getName() + " from the party.");
                } else {
                    p.sendMessage("§cCould not kick. You may not be the leader.");
                }
                return true;
            }
            case "disband": {
                DungeonInstance disbandDi = gm.instanceOf(p);
                if (pm.disband(p)) {
                    if (disbandDi != null) disbandDi.endRun();
                    p.sendMessage("§cParty disbanded.");
                } else {
                    p.sendMessage("§cYou are not the party leader.");
                }
                return true;
            }
            default:
                p.sendMessage("§7Party commands: §fcreate, invite <player>, accept, decline, leave, kick <player>, disband, info");
                return true;
        }
    }

    // ---------- /shop ----------

    private boolean shopCmd(Player p, String[] args) {
        if (plugin.game().isInInstance(p)) {
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cYou can't use /shop while inside a dungeon run. Leave with /dung leave first."));
            return true;
        }
        plugin.shopUI().openPersistentShop(p);
        return true;
    }

    // ---------- /upgrades ----------

    private boolean upgradesCmd(Player p, String[] args) {
        if (plugin.game().isInInstance(p)) {
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cYou can't use /upgrades while inside a dungeon run. Leave with /dung leave first."));
            return true;
        }
        plugin.shopUI().openUpgrades(p);
        return true;
    }

    // ---------- /stash ----------

    private boolean stashCmd(Player p, String[] args) {
        plugin.stashUI().open(p);
        return true;
    }

    // ---------- /salvage ----------

    private boolean salvageCmd(Player p, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("all")) return salvageAll(p);
        if (args.length > 0 && (args[0].equalsIgnoreCase("fav") || args[0].equalsIgnoreCase("favorite"))) {
            return toggleFavorite(p);
        }
        return salvageHeld(p);
    }

    /** Break the held Dung armor piece into salvage shards. Shards are permanent: during a run they're
     *  added to a per-floor counter that becomes persistent shards when the floor boss is defeated;
     *  outside a run they go straight into your persistent shard balance. */
    private boolean salvageHeld(Player p) {
        ItemStack held = p.getInventory().getItemInMainHand();
        String kind = tag(held, ItemTags.KIND);
        if (!"armor".equals(kind)) {
            p.sendMessage("§cHold a Dung armor piece in your main hand to salvage it.");
            return true;
        }
        if (GearFactory.isFavorite(held)) {
            p.sendMessage(ChatUI.clickableCommands("§8That armor is §bfavorited§8. Run §f/salvage favorite§8 to un-favorite it first."));
            return true;
        }
        if (GearFactory.isStarter(held)) {
            p.sendMessage("§8That's your free starter kit — it can't be salvaged.");
            return true;
        }
        // Persistent gear IS salvable when held, so a player can consciously turn a permanent piece
        // into shards. Favorite it (via /salvage favorite) if you want it protected from accidental
        // salvage. Bulk salvage (/salvage all) still skips persistent gear.
        String name = held.getItemMeta() == null ? held.getType().name() : held.getItemMeta().getDisplayName();
        int value = salvageValue(held);
        int amount = held.getAmount() - 1;
        if (amount <= 0) p.getInventory().setItemInMainHand(null);
        else held.setAmount(amount);
        UUID pid = p.getUniqueId();
        DungeonInstance di = plugin.game().instanceOf(p);
        if (di == null) {
            addShards(p, value);
            p.sendMessage("§bSalvaged " + rarityColor(held) + name
                    + "§b → §b+" + value + " shards§7 (balance §b" + plugin.meta().profile(pid).shards + "§7).");
        } else {
            Run run = di.run();
            run.salvageShards.merge(pid, value, Integer::sum);
            int total = run.salvageShards.getOrDefault(pid, 0);
            p.sendMessage("§bSalvaged " + rarityColor(held) + name
                    + "§b → §b+" + value + " shards§7 (floor total §b" + total + "§7).");
        }
        return true;
    }

    /** Toggle the favorite flag on the held armor piece (works anywhere, protects from salvage). */
    private boolean toggleFavorite(Player p) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!"armor".equals(tag(held, ItemTags.KIND))) {
            p.sendMessage("§cHold a Dung armor piece to favorite/un-favorite it.");
            return true;
        }
        boolean now = com.lieyabull.dung.items.GearFactory.toggleFavorite(held);
        p.sendMessage(now
                ? "§bFavorited — §f/salvage§b and §f/salvage all§b will skip this piece."
                : "§7Un-favorited — this piece can be salvaged again.");
        return true;
    }

    /** Salvage every salvable armor piece in the main inventory OUTSIDE the hotbar, armor slots,
     *  and offhand. Favorited pieces are always skipped. Shards go to the persistent balance outside
     *  a run, or to the per-floor counter during a run. */
    private boolean salvageAll(Player p) {
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        int pieces = 0, totalValue = 0;
        // main storage only (0-35); slots 36+ are armor/offhand which getSize() ALSO includes,
        // and those are armed/equipped, not "in the bag".
        for (int slot = 9; slot < 36; slot++) {
            org.bukkit.inventory.ItemStack s = inv.getItem(slot);
            if (!isSalvableArmor(s)) continue;
            pieces++;
            totalValue += salvageValue(s);
            inv.setItem(slot, null);
        }
        if (pieces == 0) {
            p.sendMessage("§7Nothing to salvage — no Dung armor in your bag that isn't favorited, hotbar, or equipped.");
            return true;
        }
        UUID pid = p.getUniqueId();
        DungeonInstance di = plugin.game().instanceOf(p);
        if (di == null) {
            addShards(p, totalValue);
            p.sendMessage("§bSalvaged §f" + pieces + "§b armor pieces §b→ §b+" + totalValue
                    + " shards§7 (balance §b" + plugin.meta().profile(pid).shards + "§7).");
        } else {
            Run run = di.run();
            run.salvageShards.merge(pid, totalValue, Integer::sum);
            int total = run.salvageShards.getOrDefault(pid, 0);
            p.sendMessage("§bSalvaged §f" + pieces + "§b armor pieces §b→ §b+" + totalValue
                    + " shards§7 (floor total §b" + total + "§7).");
        }
        return true;
    }

    /** Bulk-salvage eligibility — single source of truth in {@link WorkstationRules}. */
    private static boolean isSalvableArmor(org.bukkit.inventory.ItemStack s) {
        return com.lieyabull.dung.game.WorkstationRules.isBulkSalvageable(s);
    }

    /** Shard value of one piece — single source of truth in {@link WorkstationRules}. */
    private static int salvageValue(org.bukkit.inventory.ItemStack s) {
        return com.lieyabull.dung.game.WorkstationRules.salvageValueOf(s);
    }

    private static String pdcString(org.bukkit.inventory.ItemStack s, String key) {
        if (s == null || s.getItemMeta() == null) return null;
        return s.getItemMeta().getPersistentDataContainer().get(
                org.bukkit.NamespacedKey.minecraft(key), org.bukkit.persistence.PersistentDataType.STRING);
    }

    private static int pdcInt(org.bukkit.inventory.ItemStack s, String key) {
        if (s == null || s.getItemMeta() == null) return 0;
        Integer v = s.getItemMeta().getPersistentDataContainer().get(
                org.bukkit.NamespacedKey.minecraft(key), org.bukkit.persistence.PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    private void addShards(Player p, int amount) {
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        prof.shards += amount;
        plugin.meta().save();
    }

    private String rarityColor(org.bukkit.inventory.ItemStack s) {
        String rs = pdcString(s, ItemTags.RARITY);
        if (rs == null) return "";
        try {
            return Rarity.valueOf(rs).legacy;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    // ---------- balance ----------

    private void balance(Player p, String[] args) {
        if (args.length > 1) {
            // Check another player's balance
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                p.sendMessage("§cPlayer not found.");
                return;
            }
            MetaManager.MetaProfile prof = plugin.meta().profile(target.getUniqueId());
            p.sendMessage("§6--- " + target.getName() + "'s Balance ---");
            p.sendMessage("§7Persistent coins: §6" + prof.persistentCoins);
            p.sendMessage("§7Shards: §b" + prof.shards);
        } else {
            MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
            p.sendMessage("§6--- " + p.getName() + "'s Balance ---");
            p.sendMessage("§7Persistent coins: §6" + prof.persistentCoins);
            p.sendMessage("§7Shards: §b" + prof.shards);
        }
    }

    // ---------- leaderboard ----------

    private static final String[] DUNG_SUBS = {
            "start", "leave", "descend", "stats", "class", "give", "shieldswitch",
            "party", "shop", "upgrades", "salvage", "balance", "bossbar", "stop", "reset", "room", "tutorial", "help"
    };
    private static final String[] ROOM_SUBS = {"list", "reload", "validate", "preview", "gen"};
    private static final String[] TUTORIAL_SUBS = {"start", "combat", "elite", "treasure", "shop", "upgrade", "secret", "locked", "boss"};
    private static final String[] PARTY_SUBS = {"create", "invite", "accept", "decline", "leave", "kick", "disband", "info"};
    private static final String[] CLASSES = {"warrior", "mage", "ranger"};
    private static final String[] GIVE_TYPES = {"rareweapon", "heal", "coins"};
    private static final String[] SALVAGE_SUBS = {"all", "favorite", "fav"};

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
                    default -> List.of();
                };
            }
            if (args.length >= 3 && args[0].equalsIgnoreCase("party")) {
                String sub = args[1].toLowerCase();
                if (sub.equals("invite") || sub.equals("kick")) return playerNames(args[2]);
            }
            return List.of();
        }
        if (label.equals("party")) {
            if (args.length == 1) return filter(PARTY_SUBS, args[0]);
            if (args.length == 2) {
                String sub = args[0].toLowerCase();
                if (sub.equals("invite") || sub.equals("kick")) return playerNames(args[1]);
            }
            return List.of();
        }
        if (label.equals("salvage")) {
            if (args.length == 1) return filter(SALVAGE_SUBS, args[0]);
            return List.of();
        }
        if (label.equals("leaderboard")) {
            if (args.length == 1) return filter(LB_CATEGORIES, args[0]);
            if (args.length == 2) return filter(new String[]{"1", "2", "3", "4", "5"}, args[1]);
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

    private static final String[] LB_CATEGORIES = {
            "persistent_coins", "shards", "kills", "clears", "max_floor"
    };
    private static final String[] LB_LABELS = {
            "§6Persistent Coins", "§bShards", "§cKills", "§aFloors Cleared", "§5Max Floor"
    };
    private static final int LB_PER_PAGE = 5;

    private void leaderboard(Player p, String[] args) {
        int catIdx = 0; // default: persistent_coins
        int page = 1;

        // args layout: /leaderboard <category> <page> (args[0] = category, args[1] = page)
        if (args.length > 0) {
            boolean found = false;
            for (int i = 0; i < LB_CATEGORIES.length; i++) {
                if (LB_CATEGORIES[i].equalsIgnoreCase(args[0])) {
                    catIdx = i;
                    found = true;
                    break;
                }
            }
            if (!found) {
                p.sendMessage("§cUnknown leaderboard category: §f" + args[0]
                        + "§c. Valid: §f" + String.join(", ", LB_CATEGORIES));
                return;
            }
        }
        if (args.length > 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {}
        }

        // Collect all saved profiles (including offline players) from the save file.
        var meta = plugin.meta();
        java.util.List<java.util.Map.Entry<java.util.UUID, MetaManager.MetaProfile>> sorted =
                new java.util.ArrayList<>(meta.allProfiles().entrySet());

        // Sort by the selected category descending
        java.util.Comparator<java.util.Map.Entry<java.util.UUID, MetaManager.MetaProfile>> comp;
        switch (catIdx) {
            case 0: comp = java.util.Map.Entry.comparingByValue(
                    java.util.Comparator.comparingInt(prof -> prof.persistentCoins)); break;
            case 1: comp = java.util.Map.Entry.comparingByValue(
                    java.util.Comparator.comparingInt(prof -> prof.shards)); break;
            case 2: comp = java.util.Map.Entry.comparingByValue(
                    java.util.Comparator.comparingInt(prof -> prof.kills)); break;
            case 3: comp = java.util.Map.Entry.comparingByValue(
                    java.util.Comparator.comparingInt(prof -> prof.clears)); break;
            case 4: comp = java.util.Map.Entry.comparingByValue(
                    java.util.Comparator.comparingInt(prof -> prof.bestFloor)); break;
            default: comp = java.util.Map.Entry.comparingByValue(
                    java.util.Comparator.comparingInt(prof -> prof.persistentCoins));
        }
        sorted.sort(comp.reversed());

        int totalPages = Math.max(1, (int) Math.ceil((double) sorted.size() / LB_PER_PAGE));
        if (page > totalPages) page = totalPages;
        int start = (page - 1) * LB_PER_PAGE;
        int end = Math.min(start + LB_PER_PAGE, sorted.size());

        // Build header
        p.sendMessage("");
        p.sendMessage("§6§l--- " + LB_LABELS[catIdx] + " §6§lLeaderboard ---");
        p.sendMessage("");

        if (sorted.isEmpty()) {
            p.sendMessage("§7No data yet.");
        } else {
            for (int i = start; i < end; i++) {
                var entry = sorted.get(i);
                // Prefer the persisted profile name (so offline players are shown too); fall back to
                // Bukkit's offline lookup, then a placeholder.
                String name = entry.getValue().name;
                if (name == null) name = org.bukkit.Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (name == null) name = "§7Unknown";
                boolean online = org.bukkit.Bukkit.getPlayer(entry.getKey()) != null;
                String suffix = online ? "" : " §8(offline)";
                int rank = i + 1;
                String rankStr = rank <= 3 ? getRankColor(rank) + "#" + rank : "§7#" + rank;
                int value = switch (catIdx) {
                    case 0 -> entry.getValue().persistentCoins;
                    case 1 -> entry.getValue().shards;
                    case 2 -> entry.getValue().kills;
                    case 3 -> entry.getValue().clears;
                    case 4 -> entry.getValue().bestFloor;
                    default -> 0;
                };
                p.sendMessage(rankStr + " §f" + name + suffix + " §7- §e" + value);
            }
        }

        p.sendMessage("");
        // Page navigation
        var line = net.kyori.adventure.text.Component.empty();
        if (page > 1) {
            line = line.append(ChatUI.command("§7[§f◀ Prev§7]", "/leaderboard " + LB_CATEGORIES[catIdx] + " " + (page - 1), "Previous page"));
        } else {
            line = line.append(LegacyComponentSerializer.legacySection().deserialize("§8◀ Prev"));
        }
        line = line.append(LegacyComponentSerializer.legacySection().deserialize(" §7Page " + page + "/" + totalPages + " "));
        if (page < totalPages) {
            line = line.append(ChatUI.command("§7[§fNext ▶§7]", "/leaderboard " + LB_CATEGORIES[catIdx] + " " + (page + 1), "Next page"));
        } else {
            line = line.append(LegacyComponentSerializer.legacySection().deserialize("§8Next ▶"));
        }
        p.sendMessage(line);

        // Category switcher buttons
        var catLine = LegacyComponentSerializer.legacySection().deserialize("§7Categories: ");
        for (int i = 0; i < LB_CATEGORIES.length; i++) {
            if (i == catIdx) {
                catLine = catLine.append(LegacyComponentSerializer.legacySection().deserialize("§a§l" + getShortLabel(i) + "§7"));
            } else {
                catLine = catLine.append(ChatUI.command("§7" + getShortLabel(i), "/leaderboard " + LB_CATEGORIES[i] + " 1", LB_LABELS[i]));
            }
            if (i < LB_CATEGORIES.length - 1) {
                catLine = catLine.append(LegacyComponentSerializer.legacySection().deserialize(" §8| "));
            }
        }
        p.sendMessage(catLine);
        p.sendMessage("");
    }

    private static String getRankColor(int rank) {
        return switch (rank) {
            case 1 -> "§b"; // aqua
            case 2 -> "§9"; // blue
            case 3 -> "§1"; // dark blue
            default -> "§7";
        };
    }

    private static String getShortLabel(int idx) {
        return switch (idx) {
            case 0 -> "Coins";
            case 1 -> "Shards";
            case 2 -> "Kills";
            case 3 -> "Clears";
            case 4 -> "MaxFloor";
            default -> "?";
        };
    }

    // ---------- existing: stats / class / give ----------

    private void stats(Player p) {
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        p.sendMessage("§6--- " + p.getName() + " ---");
        p.sendMessage("§7Class: §f" + TextUtil.capital(prof.classId));
        p.sendMessage("§7Persistent coins: §6" + prof.persistentCoins + "   §7Shards: §b" + prof.shards);
        p.sendMessage("§7Deaths: §c" + prof.deaths + "   §7Best floor: §f" + prof.bestFloor + "   §7Kills: §f" + prof.kills);
        p.sendMessage("§7Floors cleared: §f" + prof.clears);
    }

    private void classCmd(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage("§7Classes: §fwarrior, mage, ranger");
            return;
        }
        String c = args[1].toLowerCase();
        if (!c.equals("warrior") && !c.equals("mage") && !c.equals("ranger")) {
            p.sendMessage("§cUnknown class.");
            return;
        }
        plugin.meta().profile(p.getUniqueId()).classId = c;
        plugin.meta().save(); // persist immediately so a crash/restart can't roll the choice back
        p.sendMessage("§aClass set to " + TextUtil.capital(c) + ". Next run uses it.");
    }

    private void give(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§7Usage: /dung give <rareweapon|heal|coins>")); return; }
        switch (args[1].toLowerCase()) {
            case "rareweapon":
                // free debug spawn (no coin cost): real purchases go through /shop
                p.getInventory().addItem(GearFactory.markPersistent(ItemPool.randomWeapon(2)));
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
