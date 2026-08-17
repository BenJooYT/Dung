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
    public static final int REFORGE_SHARD_COST = 15;

    // ---------- PRESERVE ----------
    public static final int PRESERVE_COIN_COST = 40;
    public static final int PRESERVE_SHARD_COST = 60;

    // ---------- SALVAGE ----------
    /** Salvage returns shards scaled by rarity + the item's primary defensive/offensive stat. */
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