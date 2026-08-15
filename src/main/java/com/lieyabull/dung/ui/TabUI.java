package com.lieyabull.dung.ui;

import com.lieyabull.dung.dungeon.Floor;
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
 * Tab menu: detailed, layered info. Header shows run + progression; the player list is
 * repurposed to show combat build stats, class/abilities, and dungeon exploration status.
 */
public final class TabUI {
    public void refresh(Player p, DungeonInstance di, org.bukkit.scoreboard.Scoreboard board) {
        if (p == null || !p.isOnline() || board == null) return;
        Run run = di.run();
        PlayerState st = run == null ? null : run.playerStateOf(p.getUniqueId());
        Objective o = board.getObjective("dungtab");
        if (o == null) {
            o = board.registerNewObjective("dungtab", Criteria.DUMMY, Component.text("Dung"));
            o.setDisplaySlot(DisplaySlot.PLAYER_LIST);
        }
        // header
        o.setDisplayName("§cDUNG §7— §fFloor " + (run == null ? 0 : run.floorIndex + 1)
                + (st == null ? "" : " §8Class §f" + TextUtil.capital(st.classId)));
        // fill player list rows with detailed info via teams
        int i = 0;
        if (st != null) {
            team(o, i++, "§cDMG §f" + TextUtil.fmt(st.damage) + "   §aDEF §f" + (int) st.defense
                    + "   §fCRIT §f" + (int) (st.critChance * 100) + "%§fx" + TextUtil.fmt(st.critMult));
            team(o, i++, "§bMana §f" + TextUtil.fmt(st.mana) + "/" + (int) st.maxMana
                    + "   §6Speed §f" + TextUtil.fmt(st.speedMult) + "   §7FireRate §f" + st.fireRateTicks + "t");
            team(o, i++, "§eCoins " + st.coins + "   §9Keys " + st.keys + "   §4Bombs " + st.bombs);
            team(o, i++, "");
            team(o, i++, "§6Equipment");
            team(o, i++, "   §fMainhand: " + itemName(p.getInventory().getItemInMainHand()));
            for (int a = 0; a < 4; a++) team(o, i++, "   §f" + armorSlot(a) + ": " + itemName(p.getInventory().getArmorContents()[a]));
            team(o, i++, "");
            team(o, i++, "§6Dungeon  §7(F" + (run == null ? 0 : run.floorIndex + 1) + ")");
            if (run != null && run.floor != null) {
                int total = run.floor.roomCount();
                int vis = run.floor.visited.size();
                int cleared = countCleared(run.floor);
                team(o, i++, "   §fRooms explored §7" + vis + "/" + total + "   §aCleared §7" + cleared);
                team(o, i++, "   §cBoss: " + (di.boss() != null ? "§4ENGAGED" : (di.curRoom() != null && di.curRoom().type.name().equals("BOSS") ? "§6AWAITING" : "§8hidden")));
            }
            team(o, i++, "");
            team(o, i++, "§8Hold sneak to cast, click to attack");
        }
        // hide real player name rows (single player)
    }

    private int countCleared(Floor f) {
        int c = 0;
        for (Floor.RoomNode n : f.rooms()) if (n.cleared) c++;
        return c;
    }

    private String armorSlot(int a) {
        return new String[]{"Boots", "Legs", "Chest", "Helmet"}[a];
    }

    private String itemName(org.bukkit.inventory.ItemStack s) {
        if (s == null || s.getType().isAir()) return "§8(none)";
        return s.hasItemMeta() && s.getItemMeta().hasDisplayName() ? s.getItemMeta().getDisplayName() : s.getType().name();
    }

    /** Remove the tab objective and all row teams so stale dungeon stats don't linger after a run. */
    public void reset(org.bukkit.scoreboard.Scoreboard board) {
        if (board == null) return;
        for (Team t : board.getTeams()) {
            if (t.getName().startsWith("d")) {
                for (String entry : t.getEntries()) board.resetScores(entry);
                t.unregister();
            }
        }
        if (board.getObjective("dungtab") != null) board.getObjective("dungtab").unregister();
    }

    private void team(Objective o, int index, String text) {
        String name = "d" + index;
        Team t = o.getScoreboard().getTeam(name);
        if (t == null) t = o.getScoreboard().registerNewTeam(name);
        t.prefix(LegacyComponentSerializer.legacySection().deserialize(text));
        // invisible entry name so no stray "dN" suffix shows beside the prefix text. Must NOT
        // collide with HUD's invisible entries ("§8"+spaces) — the board is shared and an entry
        // string can belong to only one team. Use a different invisible color (§0) for Tab rows.
        t.addEntry("§0" + " ".repeat(index + 1));
        // A PLAYER_LIST objective only shows rows that have a score; descending value = order
        // from top of the tab. Must be set (repeatedly is fine — same value = no-op packet).
        o.getScore("§0" + " ".repeat(index + 1)).setScore(100 - index);
    }
}