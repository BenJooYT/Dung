package com.lieyabull.dung.ui;

import com.lieyabull.dung.dungeon.Floor;
import com.lieyabull.dung.dungeon.RoomType;
import com.lieyabull.dung.game.DungeonInstance;
import com.lieyabull.dung.game.PlayerState;
import com.lieyabull.dung.game.Run;
import com.lieyabull.dung.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab menu: detailed, layered info. Header shows run + progression; the player list is
 * repurposed to show combat build stats, class/abilities, and dungeon exploration status.
 * Static build/CP/equipment detail lives here (never duplicated in the HUD sidebar, which
 * stays live-only). Durability renders per-piece from the shared {@link UiDurability} helper.
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
            // Combat Power breakdown (§11): cached total + per-source parts.
            team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.cp",
                    (int) Math.round(st.combatPower), (int) st.cpWeapon, (int) st.cpArmor));
            team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.cpBreak",
                    (int) st.cpShields, (int) st.cpOther, (int) st.cpUpgrades, (int) st.cpTonics));
            team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.mana",
                    TextUtil.fmt(st.mana), (int) st.maxMana, TextUtil.fmt(st.speedMult), st.fireRateTicks));
            team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.consumables", st.coins, st.keys, st.bombs));
            team(o, i++, "");
            team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.equipment"));
            team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.mainhand", itemName(p.getInventory().getItemInMainHand())));
            team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.offhand", itemName(p.getInventory().getItemInOffHand())));
            for (int a = 0; a < 4; a++) team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.armorSlot", armorSlot(p, a), itemName(p.getInventory().getArmorContents()[a])));
            // Durability per-piece from the shared helper (HUD shows worst-piece + count).
            // Prefix comes from the tab.durability Lang key — never hardcoded English.
            String durPrefix = com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.durability");
            List<UiDurability.Piece> pieces = UiDurability.collect(p.getInventory(), p);
            for (String dl : UiDurability.tabLines(durPrefix, pieces)) team(o, i++, "   " + dl);
            team(o, i++, "");
            team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.dungeon", (run == null ? 0 : run.floorIndex + 1)));
            if (run != null && run.floor != null) {
                int total = run.floor.roomCount();
                int vis = run.floor.visited.size();
                int cleared = countCleared(run.floor);
                team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.roomsExplored", vis, total, cleared));
                String bossState;
                if (di.boss() != null) bossState = com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.boss.engaged");
                else {
                    // "Awaiting" is per-player: the boss only starts when the whole party is in the
                    // boss room, so a member elsewhere must not read AWAITING. Physical room + a
                    // direct RoomType compare (never fragile .name().equals()).
                    Floor.RoomNode phys = di.roomAt(p.getLocation());
                    bossState = phys != null && phys.type == RoomType.BOSS
                            ? com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.boss.awaiting")
                            : com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.boss.hidden");
                }
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
            // In-hand weapon ability: shows its cooldown, or a cast hint ("Shift + Right-click:
            // <ability>") where the cooldown appears when it is ready to fire.
            String weaponCdRow = "";
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand != null && !hand.getType().isAir() && hand.getItemMeta() != null) {
                var pdc = hand.getItemMeta().getPersistentDataContainer();
                if (pdc.has(org.bukkit.NamespacedKey.minecraft("dung.ability"), org.bukkit.persistence.PersistentDataType.STRING)) {
                    String weaponAbilityId = pdc.get(org.bukkit.NamespacedKey.minecraft("dung.ability"),
                            org.bukkit.persistence.PersistentDataType.STRING);
                    Long weaponCd = st.cooldowns.get(weaponAbilityId);
                    long weaponRem = weaponCd == null ? 0 : weaponCd - System.currentTimeMillis();
                    if (weaponRem > 0) {
                        weaponCdRow = com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.abilityCd",
                                itemAbilityName(p, weaponAbilityId), String.format("%.1fs", weaponRem / 1000.0));
                    } else {
                        weaponCdRow = com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.weaponReady",
                                itemAbilityName(p, weaponAbilityId));
                    }
                }
            }
            team(o, i++, weaponCdRow);
            team(o, i++, "");
            team(o, i++, com.lieyabull.dung.lang.Lang.forPlayer(p, "tab.controls"));
        }
        // Clear any rows beyond this frame so a shrinking row count (or st == null with i == 0)
        // never leaves stale dungeon stats visible from a previous longer frame.
        clearBeyond(o, i);
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

    private String itemName(org.bukkit.inventory.ItemStack s) {
        if (s == null || s.getType().isAir()) return "§8(none)";
        return s.hasItemMeta() && s.getItemMeta().hasDisplayName() ? s.getItemMeta().getDisplayName() : s.getType().name();
    }

    /** Remove the tab objective and all row teams so stale dungeon stats don't linger after a run.
     *  Matches exactly our own {@code dung_dN} teams (plus legacy {@code dN} teams for migration
     *  from before the rename) — never a broad startsWith("d") that could catch other teams. */
    public void reset(org.bukkit.scoreboard.Scoreboard board) {
        if (board == null) return;
        for (Team t : new ArrayList<>(board.getTeams())) {
            String n = t.getName();
            if (n != null && (n.startsWith("dung_d") || n.matches("d\\d+"))) {
                for (String entry : new ArrayList<>(t.getEntries())) board.resetScores(entry);
                t.unregister();
            }
        }
        if (board.getObjective("dungtab") != null) board.getObjective("dungtab").unregister();
    }

    /** Unregister row teams at/after {@code from} (both current and legacy prefixes) and reset
     *  their entry scores so shrunken frames leave no stale rows. */
    private void clearBeyond(Objective o, int from) {
        org.bukkit.scoreboard.Scoreboard b = o.getScoreboard();
        for (int idx = from; idx < 40; idx++) {
            for (String name : new String[]{"dung_d" + idx, "d" + idx}) {
                Team t = b.getTeam(name);
                if (t == null) continue;
                for (String entry : new ArrayList<>(t.getEntries())) b.resetScores(entry);
                // Also reset the conventional invisible entry in case the team was emptied already.
                b.resetScores("§0" + " ".repeat(idx + 1));
                t.unregister();
            }
        }
    }

    private void team(Objective o, int index, String text) {
        String name = "dung_d" + index;
        Team t = o.getScoreboard().getTeam(name);
        if (t == null) t = o.getScoreboard().registerNewTeam(name);
        t.prefix(LegacyComponentSerializer.legacySection().deserialize(text));
        // invisible entry name so no stray "dung_dN" suffix shows beside the prefix text. Must NOT
        // collide with HUD's invisible entries ("§8"+spaces) — the board is shared and an entry
        // string can belong to only one team. Use a different invisible color (§0) for Tab rows.
        t.addEntry("§0" + " ".repeat(index + 1));
        // A PLAYER_LIST objective only shows rows that have a score; descending value = order
        // from top of the tab. Must be set (repeatedly is fine — same value = no-op packet).
        o.getScore("§0" + " ".repeat(index + 1)).setScore(100 - index);
    }
}