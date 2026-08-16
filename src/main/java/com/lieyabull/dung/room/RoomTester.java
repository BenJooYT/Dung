package com.lieyabull.dung.room;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.entity.Enemy;
import com.lieyabull.dung.entity.MobType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Room test mode: instantiates a serialized template exactly as the dungeon would (via
 * {@link RoomInstantiator}) into a cleared test pad, then verifies connections are carved open,
 * player spawn is safe, and enemies can be spawned on each spawn floor and cleaned up. Reports a
 * clear PASS/FAIL. Never instantiates an invalid template.
 */
public final class RoomTester {
    private static final int PAD_X = 2000;
    private static final int PAD_Y = 70;
    private static final int PAD_Z = 2000;

    private final Dung plugin;
    private final RoomEditorWorld editor;

    public RoomTester(Dung plugin, RoomEditorWorld editor) {
        this.plugin = plugin;
        this.editor = editor;
    }

    public static final class Result {
        public boolean pass;
        public final List<String> lines = new ArrayList<>();
        public void line(String s) { lines.add(s); }
        public String summary() {
            return pass ? "§aROOM TEST PASS" : "§cROOM TEST FAIL";
        }
    }

    public Result test(RoomTemplate tpl, Player reporter) {
        Result r = new Result();
        World w = editor.getEditorWorld();
        if (w == null) { r.line("§cEditor world unavailable."); r.pass = false; return r; }

        // 1. Never test an invalid template.
        RoomValidator.Result vres = RoomValidator.validate(tpl);
        if (vres.hasErrors()) {
            r.line("§cRefusing to test invalid template '" + tpl.id + "' - fix these first:");
            for (RoomValidationIssue i : vres.issues)
                if (i.level == RoomValidationIssue.Level.ERROR) r.line("  " + i);
            r.pass = false;
            return r;
        }

        // 2. Clear the pad and instantiate the template exactly as the generator would.
        RoomBounds total = tpl.total();
        int ox = PAD_X, oy = PAD_Y, oz = PAD_Z;
        editor.clearRegion(ox - 3, oy - 3, oz - 3, ox + (total.maxX - total.minX) + 3,
                oy + (total.maxY - total.minY) + 3, oz + (total.maxZ - total.minZ) + 3);
        try {
            RoomInstantiator.instantiate(w, tpl, ox, oy, oz);
        } catch (Exception e) {
            r.line("§cInstantiation threw: " + e.getMessage());
            r.pass = false;
            return r;
        }
        r.line("§7Instantiated '" + tpl.id + "' at test pad (" + ox + "," + oy + "," + oz + ").");

        boolean ok = true;

        // 3. Connections must be carved open (air) at the wall face and clear inward.
        if (tpl.connectors.isEmpty()) {
            r.line("§e(no connections - room is SECRET-only or unreachable by design)");
        }
        for (RoomConnector c : tpl.connectors) {
            int wx = ox + c.x, wy = oy + c.y, wz = oz + c.z;
            Material anchor = w.getBlockAt(wx, wy, wz).getType();
            if (!anchor.isAir()) {
                r.line("§cConnection " + c.direction + " is sealed by " + anchor + " at (" + c.x + "," + c.y + "," + c.z + ") - doorway not carved.");
                ok = false;
            } else {
                // inward passage must be clear for width x height
                boolean clear = true;
                for (int ww = 0; ww < c.width; ww++) {
                    for (int hh = 0; hh < c.height; hh++) {
                        int y = wy - c.height / 2 + hh;
                        int px = wx + (c.direction.dx == 0 ? (ww - (c.width - 1) / 2) : c.direction.dx);
                        int pz = wz + (c.direction.dz == 0 ? (ww - (c.width - 1) / 2) : c.direction.dz);
                        if (!w.getBlockAt(px, y, pz).getType().isAir()) { clear = false; break; }
                    }
                    if (!clear) break;
                }
                if (!clear) {
                    r.line("§cConnection " + c.direction + " passage is blocked inward (opening should be clear " + c.width + "x" + c.height + ").");
                    ok = false;
                } else {
                    r.line("§aConnection " + c.direction + " open and clear (" + c.width + "x" + c.height + ").");
                }
            }
        }

        // 4. Player spawn must be present, safe, and reachable.
        RoomMarker spawn = null;
        for (RoomMarker m : tpl.markers) if (m.type == RoomMarkerType.PLAYER_SPAWN) { spawn = m; break; }
        if (spawn == null) {
            r.line("§cNo player spawn marker - cannot verify player placement.");
            ok = false;
        } else {
            int sx = ox + spawn.x, sy = oy + spawn.y, sz = oz + spawn.z;
            Material at = w.getBlockAt(sx, sy, sz).getType();
            Material below = w.getBlockAt(sx, sy - 1, sz).getType();
            Material above = w.getBlockAt(sx, sy + 1, sz).getType();
            boolean floor = !below.isAir() && below.isSolid();
            boolean head = at.isAir() && above.isAir();
            if (!floor || !head) {
                r.line("§cPlayer spawn unsafe: floor=" + below + (floor ? "(ok)" : "(bad)") + " spawn=" + at + " above=" + above);
                ok = false;
            } else {
                r.line("§aPlayer spawn valid (solid floor, 2 air headroom).");
            }
        }

        // 5. Enemy spawning + cleanup on every spawn floor.
        if (tpl.spawnFloors.isEmpty()) {
            r.line("§cNo spawn floors - cannot test enemy spawning.");
            ok = false;
        } else {
            Random rng = new Random(tpl.id.hashCode());
            for (int i = 0; i < tpl.spawnFloors.size(); i++) {
                SpawnFloor s = tpl.spawnFloors.get(i);
                int ex = ox + (s.minX + s.maxX) / 2;
                int ez = oz + (s.minZ + s.maxZ) / 2;
                int ey = oy + s.minY; // SpawnFloor.minY is the mob's feet cell (floor is minY-1)
                Location loc = new Location(w, ex + 0.5, ey, ez + 0.5);
                boolean inside = tpl.inAnyBound(ex - ox, ey - oy, ez - oz);
                Enemy e = null;
                try {
                    e = new Enemy(w, loc, MobType.GAPER, 0, 999999, reporter, 1.0);
                } catch (Exception exx) {
                    r.line("§cEnemy spawn threw on spawn floor " + i + ": " + exx.getMessage());
                    ok = false;
                    continue;
                }
                boolean alive = e != null && e.alive();
                if (!alive || !inside) {
                    r.line("§cSpawn floor " + i + ": enemy did not spawn cleanly (inside=" + inside + " alive=" + alive + ").");
                    ok = false;
                } else {
                    r.line("§aSpawn floor " + i + " spawned a test enemy and kept it inside the room.");
                }
                if (e != null) { e.despawn(); r.line("§7  cleaned up test enemy (despawn)."); }
            }
        }

        // 6. Optional: teleport the reporter to the player spawn so they can walk it.
        if (spawn != null && ok) {
            reporter.teleport(new Location(w, ox + spawn.x + 0.5, oy + spawn.y, oz + spawn.z + 0.5));
            r.line("§7Teleported you to the player spawn - walk the room to confirm.");
        }

        r.pass = ok;
        return r;
    }
}