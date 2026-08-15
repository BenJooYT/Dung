package com.lieyabull.dung.ui;

import com.lieyabull.dung.game.DungeonInstance;
import com.lieyabull.dung.game.PlayerState;
import com.lieyabull.dung.game.Run;
import com.lieyabull.dung.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

/**
 * Sidebar HUD: concise live info. Health/mana as hearts+pips, run coins/keys/bombs, floor,
 * current room type, and a class/ability hint. Detailed info lives in the Tab menu instead.
 */
public final class HUD {
    /** Number of fixed sidebar rows. Rows are registered ONCE per board and never cleared per
     *  tick — resetting + re-adding scores every tick made the client tear the whole board down
     *  and rebuild it each frame, which reads as the sidebar flickering between two states. */
    private static final int ROWS = 12;
    private final String[] lastText = new String[ROWS];
    private String lastDisplayName = null;

    public void reset(Player p, org.bukkit.scoreboard.Scoreboard board) {
        if (board == null) return;
        if (board.getObjective("dung") != null) board.getObjective("dung").unregister();
        java.util.Arrays.fill(lastText, null);
        lastDisplayName = null;
    }

    public void update(Player p, DungeonInstance di, org.bukkit.scoreboard.Scoreboard board) {
        if (p == null || !p.isOnline() || board == null) return;
        Run run = di.run();
        if (run == null) return;
        PlayerState st = run.playerStateOf(p.getUniqueId());
        if (st == null) return;
        Objective o = board.getObjective("dung");
        if (o == null) {
            o = board.registerNewObjective("dung", Criteria.DUMMY, Component.text("Dung"));
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            registerRows(o);
        }
        String display = "§cDUNG §8Floor " + (run.floorIndex + 1);
        if (!display.equals(lastDisplayName)) {
            o.setDisplayName(display);
            lastDisplayName = display;
        }
        setLine(o, 0, "§8§m                   ");
        // combat stats
        setLine(o, 1, "§7DMG §c" + TextUtil.fmt(st.damage) + "   §7DEF §a" + (int) st.defense);
        setLine(o, 2, "§7Crit §f" + (int) (st.critChance * 100) + "% §b✕" + TextUtil.fmt(st.critMult));
        setLine(o, 3, "§7Reach §f" + TextUtil.fmt(st.reach) + "   §7Spd §f" + TextUtil.fmt(st.speedMult));
        setLine(o, 4, "");
        // consumables / run
        setLine(o, 5, "§e⛁ Coins §f" + st.coins + "   §9⛂ Keys §f" + st.keys);
        setLine(o, 6, "§4✹ Bombs §f" + st.bombs + "   §cKills §f" + run.kills);
        setLine(o, 7, "");
        setLine(o, 8, "§6Room: §f" + di.curRoom().type.label);
        setLine(o, 9, di.boss() != null ? "§4!! BOSS ACTIVE" : "");
        setLine(o, 10, "§7Class §f" + TextUtil.capital(st.classId));
        // ability cooldown (longest currently running)
        long now = System.currentTimeMillis();
        long rem = 0; String cdName = "";
        for (var e : st.cooldowns.entrySet()) {
            long r = e.getValue() - now;
            if (r > 0 && r > rem) { rem = r; cdName = e.getKey(); }
        }
        setLine(o, 11, rem > 0 ? ("§7" + cdName + " §f" + String.format("%.1f", rem / 1000.0) + "s") : "");
    }

    /** Action bar above the hotbar: red hearts + blue mana with current/max amounts (integer counts). */
    public void sendBar(Player p, PlayerState st) {
        String pct = String.format("%.0f%%", st.maxHearts <= 0 ? 100 : st.hearts / st.maxHearts * 100);
        String hearts = "§c♥ " + (int) st.hearts + "§8/" + (int) st.maxHearts + " §8(" + pct + ")";
        String mana = "§b✦ " + (int) st.mana + "§8/" + (int) st.maxMana;
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(hearts + "   " + mana));
    }

    /** Register all rows (team + invisible entry + static score) exactly once per objective so the
     *  sidebar row set is stable; later updates only re-paint prefixes. */
    private void registerRows(Objective o) {
        org.bukkit.scoreboard.Scoreboard b = o.getScoreboard();
        for (int line = 0; line < ROWS; line++) {
            String teamName = "h" + line;
            String entry = "§8" + " ".repeat(line + 1); // unique + invisible entry per line
            org.bukkit.scoreboard.Team t = b.registerNewTeam(teamName);
            t.addEntry(entry);
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