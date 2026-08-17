package com.lieyabull.dung.game;

import com.lieyabull.dung.items.Affix;
import com.lieyabull.dung.items.ItemTags;
import com.lieyabull.dung.items.Rarity;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Pure cost/result math for the unified workstation room. Everything here is deterministic and free of
 * Bukkit state so it can be unit-tested without a server. Costs use three currencies: Run Coins
 * (per-run, {@code PlayerState.coins}), Persistent Coins (permanent, {@code MetaProfile.persistentCoins}
 * — NOT spent by this room), and Shards (rare material, {@code MetaProfile.shards}).
 */
public final class WorkstationRules {
    private WorkstationRules() {}

    /** Floors between workstation (UPGRADE) rooms. */
    public static final int FLOOR_PER = 5;

    /**
     * Floor tier for a 1-based dungeon floor number. The workstation room appears on floors 5, 10, 15,
     * … so {@code tier = floor / FLOOR_PER}, clamped to at least 1. Costs scale linearly with tier so
     * late-game workstation decisions keep pace with rising per-floor income.
     */
    public static int tierOfFloor(int floor) {
        return Math.max(1, floor / FLOOR_PER);
    }

    /** Scale a base cost by the current dungeon floor number (1-based). */
    public static int scaledCost(int base, int floor) {
        return base * tierOfFloor(floor);
    }

    // ---------- UPGRADE ----------
    /** Max times an item can be upgraded at a workstation. */
    public static final int UPGRADE_MAX = 5;
    /** Run-coin base cost for the first upgrade. */
    public static final int UPGRADE_COIN_BASE = 20;
    /** Extra run coins per already-owned upgrade level. */
    public static final int UPGRADE_COIN_PER_LEVEL = 5;
    /** Shard base cost for the first upgrade. */
    public static final int UPGRADE_SHARD_BASE = 10;
    /** Extra shards per already-owned upgrade level. */
    public static final int UPGRADE_SHARD_PER_LEVEL = 5;
    /** Fractional stat gain per upgrade level applied to the item's core stat. */
    public static final double UPGRADE_STAT_PER_LEVEL = 0.10;

    /** Run-coin cost to take an item from {@code currentLevel} to the next. */
    public static int upgradeCoinCost(int currentLevel) {
        return UPGRADE_COIN_BASE + UPGRADE_COIN_PER_LEVEL * currentLevel;
    }

    /** Shard cost to take an item from {@code currentLevel} to the next. */
    public static int upgradeShardCost(int currentLevel) {
        return UPGRADE_SHARD_BASE + UPGRADE_SHARD_PER_LEVEL * currentLevel;
    }

    /** Whether an item may be upgraded further. */
    public static boolean canUpgrade(int currentLevel) {
        return currentLevel < UPGRADE_MAX;
    }

    /** Stat multiplier an item's core stat should carry at a given upgrade level. */
    public static double upgradeStatMult(int level) {
        return 1.0 + UPGRADE_STAT_PER_LEVEL * level;
    }

    // ---------- REFORGE ----------
    public static final int REFORGE_COIN_COST = 0;
    /** Shard cost for the first reforge of an item. */
    public static final int REFORGE_SHARD_COST = 15;
    /** Extra shards added to the cost per time the item has already been reforged. */
    public static final int REFORGE_SHARD_PER_REFORGE = 5;
    /** Shard cost to reforge an item given how many times it has already been reforged — each
     *  consecutive reforge of the same item is costlier, so endless re-rolling has a price. */
    public static int reforgeShardCost(int reforgeCount) {
        return REFORGE_SHARD_COST + REFORGE_SHARD_PER_REFORGE * Math.max(0, reforgeCount);
    }

    // ---------- PRESERVE ----------
    /** Run-coin cost for a preserve attempt (required AND with persistent coins and shards). */
    public static final int PRESERVE_COIN_COST = 50;
    /** Persistent-coin cost for a preserve attempt (required AND with run coins and shards). */
    public static final int PRESERVE_PERSISTENT_COIN_COST = 200;
    /** Shard cost for a preserve attempt (required AND with run coins and persistent coins). */
    public static final int PRESERVE_SHARD_COST = 250;
    /** Base chance (0.0-1.0) a preserve attempt succeeds and queues the item for post-run delivery. */
    public static final double PRESERVE_SUCCESS_CHANCE = 0.40;
    /** Bad-luck protection: after this many consecutive failed preserve attempts, the next attempt is
     *  guaranteed to succeed (a success no later than the Nth attempt). */
    public static final int PRESERVE_PITY = 3;

    /** Whether a preserve attempt with a uniform random {@code roll} in [0,1) succeeds. */
    public static boolean preserveSucceeds(double roll) {
        return roll < PRESERVE_SUCCESS_CHANCE;
    }

    /** Whether a preserve attempt should be guaranteed to succeed given the number of consecutive failed
     *  attempts so far (0-based). Reaches true at {@code PRESERVE_PITY - 1} failures, so a player is never
     *  forced to fail more than {@code PRESERVE_PITY} times in a row. */
    public static boolean preserveGuaranteed(int consecutiveFails) {
        return consecutiveFails >= PRESERVE_PITY - 1;
    }

    /** The number of consecutive failures needed to trigger the pity guarantee. */
    public static int preservePityThreshold() {
        return PRESERVE_PITY;
    }

    // ---------- SALVAGE ----------
    /** Salvage returns run-coin value scaled by rarity + the item's primary defensive/offensive stat.
     *  These run coins are added directly to the player's per-run coin balance and do NOT count
     *  toward the boss persistent coin reward. Lost on death like all run coins. */
    public static int salvageValue(Rarity r, int stat) {
        int rarityBase = (r == null ? Rarity.COMMON.ordinal() : r.ordinal()) + 1;
        return Math.max(1, rarityBase * 2 + stat / 10);
    }

    /**
     * The stat used to size a salvage reward for a given item kind: DEFENSE for armor, SHIELD_MAX for
     * shields, otherwise the higher of DAMAGE or MAGIC_DAMAGE for weapons.
     */
    public static int primaryStat(ItemStack s) {
        if (s == null || s.getType() == Material.AIR || s.getItemMeta() == null) return 0;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        String kind = str(pdc, ItemTags.KIND);
        if ("armor".equals(kind)) return intOrZero(pdc, ItemTags.DEFENSE);
        if ("shield".equals(kind)) return intOrZero(pdc, ItemTags.SHIELD_MAX);
        return Math.max(intOrZero(pdc, ItemTags.DAMAGE), intOrZero(pdc, ItemTags.MAGIC_DAMAGE));
    }

    private static int intOrZero(org.bukkit.persistence.PersistentDataContainer pdc, String key) {
        Integer v = pdc.get(org.bukkit.NamespacedKey.minecraft(key),
                org.bukkit.persistence.PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    private static String str(org.bukkit.persistence.PersistentDataContainer pdc, String key) {
        return pdc.get(org.bukkit.NamespacedKey.minecraft(key),
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    /** Whether an item is eligible for the UPGRADE / REFORGE / PRESERVE / SALVAGE workstations:
     *  a non-starter, non-persistent run-gear item of kind weapon/armor/shield. */
    public static boolean isWorkstationGear(ItemStack s) {
        if (s == null || s.getType() == Material.AIR || s.getItemMeta() == null) return false;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        boolean gear = pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.GEAR),
                org.bukkit.persistence.PersistentDataType.STRING);
        if (!gear) return false;
        if (pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.PERSISTENT),
                org.bukkit.persistence.PersistentDataType.STRING)) return false;
        if (pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.STARTER),
                org.bukkit.persistence.PersistentDataType.STRING)) return false;
        String kind = str(pdc, ItemTags.KIND);
        return "weapon".equals(kind) || "armor".equals(kind) || "shield".equals(kind);
    }

    /** Parse an {@code "id:value"} affix string back into its id (empty if malformed). */
    public static String affixIdOf(String serialized) {
        int i = serialized.indexOf(':');
        return i < 0 ? serialized : serialized.substring(0, i);
    }
}