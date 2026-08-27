package com.lieyabull.dung.ui;

import com.lieyabull.dung.dungeon.Floor;
import com.lieyabull.dung.game.DungeonInstance;
import com.lieyabull.dung.game.PlayerState;
import com.lieyabull.dung.game.Run;
import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
        String titleClass = st == null ? "" : com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.titleClass", className(p, st.classId));
        o.setDisplayName(com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.title", (run == null ? 0 : run.floorIndex + 1), titleClass));
        // fill player list rows with detailed info via teams
        int i = 0;
        if (st != null) {
            team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.build",
                    TextUtil.fmt(st.damage), TextUtil.fmt(st.magicDamage), (int) st.defense,
                    (int) (st.critChance * 100), TextUtil.fmt(st.critMult)));
            team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.mana",
                    TextUtil.fmt(st.mana), (int) st.maxMana, TextUtil.fmt(st.speedMult), st.fireRateTicks));
            team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.consumables", st.coins, st.keys, st.bombs));
            team(o, i++, "");
            team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.equipment"));
            team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.mainhand", itemName(p.getInventory().getItemInMainHand())));
            for (int a = 0; a < 4; a++) team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.armorSlot", armorSlot(p, a), itemName(p.getInventory().getArmorContents()[a])));
            // Durability summary for persistent gear
            String durSummary = durabilitySummary(p);
            if (!durSummary.isEmpty()) team(o, i++, "   " + durSummary);
            team(o, i++, "");
            team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.dungeon", (run == null ? 0 : run.floorIndex + 1)));
            if (run != null && run.floor != null) {
                int total = run.floor.roomCount();
                int vis = run.floor.visited.size();
                int cleared = countCleared(run.floor);
                team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.roomsExplored", vis, total, cleared));
                String bossState;
                if (di.boss() != null) bossState = com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.boss.engaged");
                else if (di.curRoom() != null && di.curRoom().type.name().equals("BOSS")) bossState = com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.boss.awaiting");
                else bossState = com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.boss.hidden");
                team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.boss", bossState));
            }
            // Class ability info
            String classAbilityLabel = classAbilityName(p, st.classId);
            String classKey = "class_" + st.classId;
            Long classCd = st.cooldowns.get(classKey);
            long classRem = classCd == null ? 0 : classCd - System.currentTimeMillis();
            String cdStr;
            if (classRem > 0) {
                cdStr = String.format("%.1fs", classRem / 1000.0);
            } else {
                cdStr = com.lieyabull.dung.lang.Lang.forPlayer(p, "hud.readyText");
            }
            team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.abilityCd", classAbilityLabel, cdStr));
            team(o, i++, "");
            team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.controls"));
        }
        // hide real player name rows (single player)
    }

    private int countCleared(Floor f) {
        int c = 0;
        for (Floor.RoomNode n : f.rooms()) if (n.cleared) c++;
        return c;
    }

    private String armorSlot(Player p, int a) {
        return com.lieyabull.dung.lang.Lang.forPlayer(p, new String[]{
                "tab.armor.boots", "tab.armor.legs", "tab.armor.chest", "tab.armor.helmet"}[a]);
    }

    private String className(Player p, String classId) {
        String key = "class." + classId;
        String localized = com.lieyabull.dung.lang.Lang.get(com.lieyabull.dung.lang.Lang.languageOf(p), key);
        if (!localized.equals(key)) return localized;
        return TextUtil.capital(classId);
    }

    private String classAbilityName(Player p, String classId) {
        return switch (classId) {
            case "warrior" -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.warrior");
            case "mage" -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.mage");
            case "ranger" -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.ranger");
            default -> com.lieyabull.dung.lang.Lang.forPlayer(p, "ability.class");
        };
    }

    private String itemName(org.bukkit.inventory.ItemStack s) {
        if (s == null || s.getType().isAir()) return "§8(none)";
        return s.hasItemMeta() && s.getItemMeta().hasDisplayName() ? s.getItemMeta().getDisplayName() : s.getType().name();
    }

    /** Build a durability summary string for the player's persistent gear. */
    private static String durabilitySummary(Player p) {
        int total = 0;
        int totalMax = 0;
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        ItemStack[] hands = {inv.getItemInMainHand(), inv.getItemInOffHand()};
        for (ItemStack s : hands) {
            if (s == null || s.getType() == Material.AIR) continue;
            int d = GearFactory.getDurability(s);
            int m = GearFactory.getMaxDurability(s);
            if (d >= 0 && m > 0) { total += d; totalMax += m; }
        }
        for (ItemStack s : inv.getArmorContents()) {
            if (s == null || s.getType() == Material.AIR) continue;
            int d = GearFactory.getDurability(s);
            int m = GearFactory.getMaxDurability(s);
            if (d >= 0 && m > 0) { total += d; totalMax += m; }
        }
        if (totalMax <= 0) return "";
        double pct = (double) total / totalMax;
        String color;
        if (pct >= 0.67) color = "§a";
        else if (pct >= 0.34) color = "§e";
        else color = "§c";
        int filled = (int) Math.round(pct * 10);
        int empty = 10 - filled;
        StringBuilder bar = new StringBuilder("§7Durability: ").append(color);
        bar.append("█".repeat(Math.max(0, filled)));
        bar.append("§8░".repeat(Math.max(0, empty)));
        bar.append(" §7").append(total).append("/").append(totalMax);
        return bar.toString();
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