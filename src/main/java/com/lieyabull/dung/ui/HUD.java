package com.lieyabull.dung.ui;

import com.lieyabull.dung.game.GameManager;
import com.lieyabull.dung.game.PlayerState;
import com.lieyabull.dung.game.Run;
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
    public void reset(Player p, org.bukkit.scoreboard.Scoreboard board) {
        if (board == null) return;
        if (board.getObjective("dung") != null) board.getObjective("dung").unregister();
    }

    public void update(GameManager gm, org.bukkit.scoreboard.Scoreboard board) {
        Player p = gm.player();
        if (p == null || !p.isOnline() || board == null) return;
        Run run = gm.run();
        if (run == null) return;
        PlayerState st = run.playerState();
        Objective o = board.getObjective("dung");
        if (o == null) {
            o = board.registerNewObjective("dung", Criteria.DUMMY, Component.text("Dung"));
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        o.setDisplayName("§cDUNG §8Floor " + (run.floorIndex + 1));
        // clear prior line entries so removed rows don't linger
        for (org.bukkit.scoreboard.Team t : board.getTeams()) {
            if (t.getName().startsWith("h")) {
                for (String entry : t.getEntries()) board.resetScores(entry);
            }
        }
        int score = 11;
        set(o, 0, score--, "§8§m                   ");
        // combat stats
        set(o, 1, score--, "§7DMG §c" + fmt(st.damage) + "   §7DEF §a" + (int) st.defense);
        set(o, 2, score--, "§7Crit §f" + (int) (st.critChance * 100) + "% §b✕" + fmt(st.critMult));
        set(o, 3, score--, "§7Reach §f" + fmt(st.reach) + "   §7Spd §f" + fmt(st.speedMult));
        set(o, 4, score--, "");
        // consumables / run
        set(o, 5, score--, "§e⛁ Coins §f" + st.coins + "   §9⛂ Keys §f" + st.keys);
        set(o, 6, score--, "§4✹ Bombs §f" + st.bombs + "   §cKills §f" + run.kills);
        set(o, 7, score--, "");
        set(o, 8, score--, "§6Room: §f" + gm.curRoom().type.label);
        set(o, 9, score--, gm.boss() != null ? "§4!! BOSS ACTIVE" : "");
        set(o, 10, score--, "§7Class §f" + capital(st.classId));
        // ability cooldown (longest currently running)
        long now = System.currentTimeMillis();
        long rem = 0; String cdName = "";
        for (var e : st.cooldowns.entrySet()) {
            long r = e.getValue() - now;
            if (r > 0 && r > rem) { rem = r; cdName = e.getKey(); }
        }
        String cdLine = rem > 0 ? ("§7" + cdName + " §f" + String.format("%.1f", rem / 1000.0) + "s") : "";
        set(o, 11, score--, cdLine);
    }

    /** Action bar above the hotbar: red hearts + blue mana with current/max amounts (integer counts). */
    public void sendBar(Player p, PlayerState st) {
        String hearts = "§c♥ " + (int) st.hearts + "§8/" + (int) st.maxHearts;
        String mana = "§b✦ " + (int) st.mana + "§8/" + (int) st.maxMana;
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(hearts + "   " + mana));
    }

    /** Team name "h<line>" is internal (never displayed). The visible ENTRY is an invisible
     *  colored-blank string so the prefix text shows without stray letters; entry stays stable
     *  so updating only re-paints the prefix instead of adding rows. */
    private void set(Objective o, int line, int score, String text) {
        String teamName = "h" + line;
        String entry = "§8" + " ".repeat(line + 1); // unique + invisible entry per line
        org.bukkit.scoreboard.Scoreboard b = o.getScoreboard();
        org.bukkit.scoreboard.Team t = b.getTeam(teamName);
        if (t == null) {
            t = b.registerNewTeam(teamName);
            t.addEntry(entry);
        }
        t.prefix(LegacyComponentSerializer.legacySection().deserialize(truncateVisible(text, 40)));
        o.getScore(entry).setScore(score);
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

    private String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.1f", v);
    }

    private String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}