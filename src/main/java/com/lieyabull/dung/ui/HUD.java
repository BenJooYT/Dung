package com.lieyabull.dung.ui;

import com.lieyabull.dung.dungeon.Floor;
import com.lieyabull.dung.dungeon.RoomType;
import com.lieyabull.dung.game.DungeonInstance;
import com.lieyabull.dung.game.PlayerState;
import com.lieyabull.dung.game.Run;
import com.lieyabull.dung.items.ItemTags;
import com.lieyabull.dung.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Sidebar HUD: live info plus the build summary. HP/mana/shield live in the action bar
 * ({@link #sendBar}, the single action-bar writer); the sidebar shows combat stats, run
 * resources, room, worst-piece durability, boss/locked hints, class, and ability cooldowns.
 * Tab adds the detailed equipment/durability breakdown — it is not the only place stats live,
 * because the vanilla player-list slot cannot render arbitrary rows.
 */
public final class HUD {
    /** Number of fixed sidebar rows. Rows are registered ONCE per board and never cleared per
     *  tick — resetting + re-adding scores every tick made the client tear the whole board down
     *  and rebuild it each frame, which reads as the sidebar flickering between two states. */
    private static final int ROWS = 15;
    private final String[] lastText = new String[ROWS];
    private String lastDisplayName = null;

    /** Reset the last-text tracking so the next update re-paints every row. */
    public void resetLastText() {
        java.util.Arrays.fill(lastText, null);
        lastDisplayName = null;
    }

    public void reset(Player p, org.bukkit.scoreboard.Scoreboard board) {
        if (board == null) return;
        // Unregister our own hN row teams so a reused board never hits "team already exists"
        // on re-register. Match exactly h+digits (our rows) — never a broad prefix that could
        // catch other plugins' teams on a shared board.
        for (Team t : new ArrayList<>(board.getTeams())) {
            String n = t.getName();
            if (n != null && n.matches("h\\d+")) {
                for (String entry : new ArrayList<>(t.getEntries())) board.resetScores(entry);
                t.unregister();
            }
        }
        if (board.getObjective("dung") != null) board.getObjective("dung").unregister();
        java.util.Arrays.fill(lastText, null);
        lastDisplayName = null;
    }

    /** Blank every sidebar row (used when there is no live state so no stale rows linger). */
    private void clearAll(Objective o) {
        for (int line = 0; line < ROWS; line++) setLine(o, line, "");
    }

    public void update(Player p, DungeonInstance di, org.bukkit.scoreboard.Scoreboard board) {
        if (p == null || !p.isOnline() || board == null) return;
        Objective o = board.getObjective("dung");
        if (o == null) {
            o = board.registerNewObjective("dung", Criteria.DUMMY, Component.text("Dung"));
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            registerRows(o);
        }
        Run run = di.run();
        // No live state — blank every row instead of early-returning so stale dungeon stats
        // from a previous run can never linger on the sidebar.
        if (run == null) {
            clearAll(o);
            return;
        }
        PlayerState st = run.playerStateOf(p.getUniqueId());
        if (st == null) {
            clearAll(o);
            return;
        }
        String display = com.lieyabull.dung.lang.Lang.forPlayer(p, "hud.title", run.floorIndex + 1);
        if (!display.equals(lastDisplayName)) {
            o.setDisplayName(display);
            lastDisplayName = display;
        }
        // 15-row sidebar: build summary, run resources, room, gear, hints, class, cooldowns.
        // HP/mana/shield live in the action bar (sendBar, single writer).
        setLine(o, 0, "§8§m                   ");
        // build stats — DMG always shows melee (red) and magic (blue) counters side by side
        setLine(o, 1, com.lieyabull.dung.lang.Lang.forPlayer(p, "hud.dmgDef",
                TextUtil.fmt(st.damage), TextUtil.fmt(st.magicDamage), (int) st.defense));
        setLine(o, 2, com.lieyabull.dung.lang.Lang.forPlayer(p, "hud.crit",
                (int) (st.critChance * 100), TextUtil.fmt(st.critMult)));
        setLine(o, 3, com.lieyabull.dung.lang.Lang.forPlayer(p, "hud.reachSpd",
                TextUtil.fmt(st.reach), TextUtil.fmt(st.speedMult)));
        // Combat Power (§11): cached on the PlayerState, refreshed on gear change.
        setLine(o, 4, com.lieyabull.dung.lang.Lang.forPlayer(p, "hud.cp", (int) Math.round(st.combatPower)));
        // consumables / run (live)
        setLine(o, 5, com.lieyabull.dung.lang.Lang.forPlayer(p, "hud.coinsLine", st.coins, st.keys));
        setLine(o, 6, com.lieyabull.dung.lang.Lang.forPlayer(p, "hud.bombsLine", st.bombs, run.killsOf(p.getUniqueId())));
        setLine(o, 7, "");
        // Determine which room the player is physically inside. If they're in the corridor
        // (between rooms), show "Corridor" instead of the stale previous room.
        Floor.RoomNode physicalRoom = di.roomAt(p.getLocation());
        String roomLabel;
        if (physicalRoom != null) {
            roomLabel = roomTypeLabel(p, physicalRoom.type);
        } else {
            roomLabel = com.lieyabull.dung.lang.Lang.forPlayer(p, "hud.corridor");
        }
        // Check if any adjacent room is a LOCKED room; fold the boss/locked hint into the room
        // row so the five durability slots below always own fixed rows.
        String lockedHint = "";
        Floor.RoomNode cur = physicalRoom;
        if (cur != null) {
            int[] DX = {0, 1, 0, -1};
            int[] DZ = {-1, 0, 1, 0};
            for (int d = 0; d < 4; d++) {
                if (!cur.doors[d]) continue;
                Floor.RoomNode adj = run.floor.at(cur.x + DX[d], cur.z + DZ[d]);
                if (adj != null && adj.type == RoomType.LOCKED && !adj.cleared) {
                    lockedHint = com.lieyabull.dung.lang.Lang.forPlayer(p, "hud.lockedHint");
                    break;
                }
            }
        }
        String roomHint = di.boss() != null
                ? com.lieyabull.dung.lang.Lang.forPlayer(p, "hud.bossActive") : lockedHint;
        String roomLine = com.lieyabull.dung.lang.Lang.forPlayer(p, "hud.room", roomLabel);
        if (!roomHint.isEmpty()) roomLine += "  " + roomHint;
        setLine(o, 7, roomLine);
        // Per-slot durability: Hand, then Helmet/Chest/Legs/Boots from top to bottom.
        List<UiDurability.Piece> slots = UiDurability.hudSlots(p.getInventory(), p);
        for (int i = 0; i < slots.size() && i < 5; i++) {
            setLine(o, 8 + i, UiDurability.hudSlotLine(slots.get(i)));
        }
        // Class + class-ability cooldown share one row.
        long now = System.currentTimeMillis();
        String classAbilityLabel = classAbilityName(p, st.classId);
        String classKey = "class_" + st.classId;
        Long classCd = st.cooldowns.get(classKey);
        long classRem = classCd == null ? 0 : classCd - now;
        String classLine = classRem > 0
                ? com.lieyabull.dung.lang.Lang.forPlayer(p, "hud.cd", classAbilityLabel, String.format("%.1f", classRem / 1000.0))
                : com.lieyabull.dung.lang.Lang.forPlayer(p, "hud.ready", classAbilityLabel);
        setLine(o, 13, "§7" + className(p, st.classId) + " §8· " + classLine);
        // In-hand item ability: always show its name so the player can see what the weapon does,
        // plus the remaining cooldown while it is recharging. Falls back to the slot-9 shield hint
        // when the held item has no ability.
        String itemAbilityRow = "";
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand != null && !hand.getType().isAir() && hand.getItemMeta() != null) {
            var pdc = hand.getItemMeta().getPersistentDataContainer();
            if (pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.ABILITY), org.bukkit.persistence.PersistentDataType.STRING)) {
                String itemAbilityId = pdc.get(org.bukkit.NamespacedKey.minecraft(ItemTags.ABILITY),
                        org.bukkit.persistence.PersistentDataType.STRING);
                String itemAbilityLabel = itemAbilityName(p, itemAbilityId);
                Long itemCd = st.cooldowns.get(itemAbilityId);
                long itemRem = itemCd == null ? 0 : itemCd - now;
                itemAbilityRow = itemRem > 0
                        ? com.lieyabull.dung.lang.Lang.forPlayer(p, "hud.cd",
                                itemAbilityLabel, String.format("%.1f", itemRem / 1000.0))
                        : com.lieyabull.dung.lang.Lang.forPlayer(p, "hud.ready", itemAbilityLabel);
            }
        }
        setLine(o, 14, itemAbilityRow.isEmpty() ? shieldHint(p) : itemAbilityRow);
    }

    /** Persistent on-screen clue for the Mana Shield's dedicated slot (critic hud.md #6): shown on
     *  the sidebar whenever hotbar slot 9 is empty, so the hint survives a skipped tutorial. */
    private static String shieldHint(Player p) {
        try {
            if (p != null && p.getInventory().getItem(8) == null) {
                return com.lieyabull.dung.lang.Lang.forPlayer(p, "hud.shieldHint");
            }
        } catch (Exception ignored) {
            // Mocked inventories in tests may not support slot indexing — the row just stays blank.
        }
        return "";
    }

    /** Localized friendly name for a room type (used in the sidebar Room row). */
    private static String roomTypeLabel(Player p, RoomType type) {
        String key = "roomtype." + type.name();
        String localized = com.lieyabull.dung.lang.Lang.get(com.lieyabull.dung.lang.Lang.languageOf(p), key);
        if (localized.equals(key)) return type.label;
        return "§f" + localized;
    }

    /** Localized friendly name of a player's class for the sidebar. */
    private static String className(Player p, String classId) {
        String key = "class." + classId;
        String localized = com.lieyabull.dung.lang.Lang.get(com.lieyabull.dung.lang.Lang.languageOf(p), key);
        if (!localized.equals(key)) return localized;
        return TextUtil.capital(classId);
    }

    /** Localized name of the player's class ability for the cooldown row. */
    private static String classAbilityName(Player p, String classId) {
        return switch (classId) {
            case "warrior" -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.warrior");
            case "mage" -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.mage");
            case "ranger" -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.ranger");
            default -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.class");
        };
    }

    /** Localized friendly name for an item ability id (e.g. "Chain Lightning") for its cooldown row.
     *  The shared "ability.*" keys end in a celebratory "!" that reads oddly in a cooldown label, so
     *  the trailing exclamation mark is stripped here. */
    private static String itemAbilityName(Player p, String id) {
        String raw = switch (id) {
            case "Rush" -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.rush");
            case "Slash" -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.slash");
            case "Cleave" -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.cleave");
            case "Smash" -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.smash");
            case "Blade Storm" -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.bladeStorm");
            case "Arcane Bolt" -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.arcaneBolt");
            case "Ravage" -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.ravage");
            case "Chain Lightning" -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.chainLightning");
            case "Fireball" -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.fireball");
            case "Life Drain" -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.lifeDrain");
            case "Lightning" -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.lightning");
            default -> "§f" + id;
        };
        return raw.endsWith("!") ? raw.substring(0, raw.length() - 1) : raw;
    }

    /** Action bar above the hotbar: red hearts + blue mana with current/max amounts (integer counts).
     *  A single writer owns the action bar (the per-tick HUD refresh) so transient hints are appended
     *  as a suffix here rather than issued from a second cadence, which fought the bar and flickered. */
    public void sendBar(Player p, PlayerState st, String hint) {
        String pct = String.format("%.0f%%", st.maxHearts <= 0 ? 100 : st.hearts / st.maxHearts * 100);
        String hearts = "§c♥ " + (int) st.hearts + "§8/" + (int) st.maxHearts + " §8(" + pct + ")";
        String mana = "§b✦ " + (int) st.mana + "§8/" + (int) st.maxMana;
        // Show shield charge if the player has shield capacity (from a held shield or affixes)
        String shieldStr = "";
        if (st.shieldMax > 0) {
            int shieldPct = (int) (st.shieldMax <= 0 ? 0 : st.shield / st.shieldMax * 100);
            shieldStr = "   §7🛡 " + (int) st.shield + "§8/" + (int) st.shieldMax + " §8(" + shieldPct + "%)";
        }
        String suffix = (hint == null || hint.isEmpty()) ? "" : "   " + hint;
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(hearts + "   " + mana + shieldStr + suffix));
    }

    /** Register all rows (team + invisible entry + static score) exactly once per objective so the
     *  sidebar row set is stable; later updates only re-paint prefixes. Reuses existing teams so
     *  a re-register on a reused board never throws "team already exists". */
    private void registerRows(Objective o) {
        org.bukkit.scoreboard.Scoreboard b = o.getScoreboard();
        for (int line = 0; line < ROWS; line++) {
            String teamName = "h" + line;
            String entry = "§8" + " ".repeat(line + 1); // unique + invisible entry per line
            org.bukkit.scoreboard.Team t = b.getTeam(teamName);
            if (t == null) t = b.registerNewTeam(teamName);
            if (!t.getEntries().contains(entry)) t.addEntry(entry);
            o.getScore(entry).setScore(ROWS - line);
        }
    }

    /** Re-paint one row's text only when it changed. Scores/entries are registered once, so an
     *  unchanged line sends no packets at all — no tear-down, no flicker. */
    private void setLine(Objective o, int line, String text) {
        if (line >= 0 && line < ROWS && text.equals(lastText[line])) return;
        if (line >= 0 && line < ROWS) lastText[line] = text;
        org.bukkit.scoreboard.Team t = o.getScoreboard().getTeam("h" + line);
        if (t != null) t.prefix(LegacyComponentSerializer.legacySection().deserialize(truncateVisible(text, 40)));
    }

    /** Truncate to `max` VISIBLE characters, keeping any §-codes that started before the cut so
     *  colored lines aren't trimmed by counting raw color-code characters. */
    private static String truncateVisible(String s, int max) {
        StringBuilder sb = new StringBuilder();
        int vis = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00A7') {
                if (i + 1 < s.length()) { sb.append(c).append(s.charAt(i + 1)); i++; }
                continue;
            }
            if (vis >= max) break;
            sb.append(c);
            vis++;
        }
        return sb.toString();
    }
}