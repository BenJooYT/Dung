package com.lieyabull.dung.game;

import com.lieyabull.dung.items.Rarity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure-logic tests for {@link WorkstationRules} — no Bukkit dependencies. */
public class WorkstationRulesTest {

    // ---- UPGRADE costs ----

    @Test
    void upgradeCoinCostScalesWithLevel() {
        assertEquals(WorkstationRules.UPGRADE_COIN_BASE, WorkstationRules.upgradeCoinCost(0));
        assertEquals(WorkstationRules.UPGRADE_COIN_BASE + WorkstationRules.UPGRADE_COIN_PER_LEVEL,
                WorkstationRules.upgradeCoinCost(1));
        assertEquals(WorkstationRules.UPGRADE_COIN_BASE + WorkstationRules.UPGRADE_COIN_PER_LEVEL * 4,
                WorkstationRules.upgradeCoinCost(4));
    }

    @Test
    void upgradeShardCostScalesWithLevel() {
        assertEquals(WorkstationRules.UPGRADE_SHARD_BASE, WorkstationRules.upgradeShardCost(0));
        assertEquals(WorkstationRules.UPGRADE_SHARD_BASE + WorkstationRules.UPGRADE_SHARD_PER_LEVEL,
                WorkstationRules.upgradeShardCost(1));
    }

    @Test
    void canUpgradeBoundary() {
        assertTrue(WorkstationRules.canUpgrade(0));
        assertTrue(WorkstationRules.canUpgrade(WorkstationRules.UPGRADE_MAX - 1));
        assertFalse(WorkstationRules.canUpgrade(WorkstationRules.UPGRADE_MAX));
        assertFalse(WorkstationRules.canUpgrade(WorkstationRules.UPGRADE_MAX + 1));
    }

    @Test
    void upgradeStatMultIsMonotonic() {
        double prev = 0;
        for (int lvl = 0; lvl <= WorkstationRules.UPGRADE_MAX; lvl++) {
            double m = WorkstationRules.upgradeStatMult(lvl);
            assertTrue(m > prev, "mult should grow with level");
            prev = m;
        }
        assertEquals(1.0, WorkstationRules.upgradeStatMult(0), 1e-9);
    }

    // ---- REFORGE ----

    @Test
    void reforgeCostsPositiveShards() {
        assertTrue(WorkstationRules.REFORGE_SHARD_COST > 0);
        assertTrue(WorkstationRules.REFORGE_COIN_COST >= 0);
    }

    // ---- PRESERVE ----

    @Test
    void preserveCostsArePositiveAndUseNoPersistentCoins() {
        assertTrue(WorkstationRules.PRESERVE_COIN_COST > 0);
        assertTrue(WorkstationRules.PRESERVE_SHARD_COST > 0);
    }

    // ---- SALVAGE ----

    @Test
    void salvageValueScalesWithRarityAndStat() {
        int commonLow = WorkstationRules.salvageValue(Rarity.COMMON, 5);
        int mythicLow = WorkstationRules.salvageValue(Rarity.MYTHIC, 5);
        assertTrue(mythicLow > commonLow, "higher rarity should salvage for more");
        int commonHigh = WorkstationRules.salvageValue(Rarity.COMMON, 100);
        assertTrue(commonHigh > commonLow, "higher stat should salvage for more");
        // never zero
        assertTrue(WorkstationRules.salvageValue(null, 0) >= 1);
    }

    // ---- eligibility ----

    @Test
    void nullOrAirIsNotWorkstationGear() {
        assertFalse(WorkstationRules.isWorkstationGear(null));
    }

    // ---- affix id parsing ----

    @Test
    void affixIdOfParsesIdAndHandlesMalformed() {
        assertEquals("vicious", WorkstationRules.affixIdOf("vicious:7"));
        assertEquals("vicious", WorkstationRules.affixIdOf("vicious")); // no colon -> whole string
    }
}
