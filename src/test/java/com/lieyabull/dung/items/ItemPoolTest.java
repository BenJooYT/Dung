package com.lieyabull.dung.items;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ItemPool} that focus on the pure-logic portions
 * ({@link ItemPool#rollRarity(int)}) without requiring a Bukkit server.
 * <p>
 * Methods that create {@link org.bukkit.inventory.ItemStack} instances
 * (randomWeapon, randomArmor, roomReward when gearChance > 0) depend on
 * Bukkit APIs and are not tested here.
 */
public class ItemPoolTest {

    // ---- rollRarity() ----

    @Test
    void rollRarityAtFloor0ReturnsOnlyCommon() {
        for (int i = 0; i < 100; i++) {
            Rarity r = ItemPool.rollRarity(0);
            assertEquals(Rarity.COMMON, r);
        }
    }

    @Test
    void rollRarityAtFloor1CanReturnUncommon() {
        boolean foundUncommon = false;
        for (int i = 0; i < 500; i++) {
            Rarity r = ItemPool.rollRarity(1);
            if (r == Rarity.UNCOMMON) {
                foundUncommon = true;
                break;
            }
        }
        assertTrue(foundUncommon, "Should roll UNCOMMON at floor 1 within 500 attempts");
    }

    @Test
    void rollRarityAtFloor3CanReturnRare() {
        boolean foundRare = false;
        for (int i = 0; i < 1000; i++) {
            Rarity r = ItemPool.rollRarity(3);
            if (r == Rarity.RARE) {
                foundRare = true;
                break;
            }
        }
        assertTrue(foundRare, "Should roll RARE at floor 3 within 1000 attempts");
    }

    @Test
    void rollRarityAtFloor6CanReturnEpic() {
        boolean foundEpic = false;
        for (int i = 0; i < 2000; i++) {
            Rarity r = ItemPool.rollRarity(6);
            if (r == Rarity.EPIC) {
                foundEpic = true;
                break;
            }
        }
        assertTrue(foundEpic, "Should roll EPIC at floor 6 within 2000 attempts");
    }

    @Test
    void rollRarityAtFloor10CanReturnLegendary() {
        boolean foundLegendary = false;
        for (int i = 0; i < 5000; i++) {
            Rarity r = ItemPool.rollRarity(10);
            if (r == Rarity.LEGENDARY) {
                foundLegendary = true;
                break;
            }
        }
        assertTrue(foundLegendary, "Should roll LEGENDARY at floor 10 within 5000 attempts");
    }

    @Test
    void rollRarityAtFloor15CanReturnMythic() {
        boolean foundMythic = false;
        for (int i = 0; i < 10000; i++) {
            Rarity r = ItemPool.rollRarity(15);
            if (r == Rarity.MYTHIC) {
                foundMythic = true;
                break;
            }
        }
        assertTrue(foundMythic, "Should roll MYTHIC at floor 15 within 10000 attempts");
    }

    @Test
    void rollRarityNeverReturnsNull() {
        for (int floor = 0; floor < 50; floor++) {
            for (int i = 0; i < 100; i++) {
                assertNotNull(ItemPool.rollRarity(floor));
            }
        }
    }

    @Test
    void rollRarityHigherFloorsProduceRarerDrops() {
        double avgFloor0 = averageRarityOrdinal(0, 10000);
        double avgFloor20 = averageRarityOrdinal(20, 10000);
        assertTrue(avgFloor20 > avgFloor0,
                "Average rarity at floor 20 (" + avgFloor20 + ") should be higher than at floor 0 ("
                        + avgFloor0 + ")");
    }

    private static double averageRarityOrdinal(int floor, int samples) {
        long sum = 0;
        for (int i = 0; i < samples; i++) {
            sum += ItemPool.rollRarity(floor).ordinal();
        }
        return (double) sum / samples;
    }

    // ---- roomReward() - structural tests (no Bukkit needed) ----

    @Test
    void roomRewardReturnsEmptyListForUnknownRoomKind() {
        var result = ItemPool.roomReward(0, 99);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void roomRewardReturnsEmptyListForRoomKind0() {
        var result = ItemPool.roomReward(0, 0);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ---- Rarity weights distribution ----

    @Test
    void rarityDistributionAtFloor0IsAllCommon() {
        Map<Rarity, Integer> counts = new HashMap<>();
        int samples = 1000;
        for (int i = 0; i < samples; i++) {
            Rarity r = ItemPool.rollRarity(0);
            counts.merge(r, 1, Integer::sum);
        }
        assertEquals(samples, counts.getOrDefault(Rarity.COMMON, 0).intValue(),
                "At floor 0, all rolls should be COMMON");
    }

    @Test
    void rarityDistributionAtFloor5HasExpectedRarities() {
        // Floor 5: push = 0.25. Eligible rarities and their floorUnlock values:
        // COMMON (0.00), UNCOMMON (0.06), RARE (0.14), EPIC (0.26), LEGENDARY (0.42)
        // MYTHIC (0.60) is NOT eligible at floor 5 (5 < 0.60 is false, but 5 >= 0.60 is true).
        // Actually floorUnlock is compared as: floor >= r.floorUnlock.
        // 5 >= 0.42 -> true (LEGENDARY eligible), 5 >= 0.60 -> true (MYTHIC eligible).
        // So at floor 5, ALL rarities are eligible.
        Map<Rarity, Integer> counts = new HashMap<>();
        int samples = 10000;
        for (int i = 0; i < samples; i++) {
            Rarity r = ItemPool.rollRarity(5);
            counts.merge(r, 1, Integer::sum);
        }
        // All rarities should appear at floor 5
        assertTrue(counts.getOrDefault(Rarity.COMMON, 0) > 0, "COMMON should appear");
        assertTrue(counts.getOrDefault(Rarity.UNCOMMON, 0) > 0, "UNCOMMON should appear");
        assertTrue(counts.getOrDefault(Rarity.RARE, 0) > 0, "RARE should appear");
        assertTrue(counts.getOrDefault(Rarity.EPIC, 0) > 0, "EPIC should appear");
        assertTrue(counts.getOrDefault(Rarity.LEGENDARY, 0) > 0, "LEGENDARY should appear");
        assertTrue(counts.getOrDefault(Rarity.MYTHIC, 0) > 0, "MYTHIC should appear");
        // COMMON should be more frequent than MYTHIC
        int common = counts.getOrDefault(Rarity.COMMON, 0);
        int mythic = counts.getOrDefault(Rarity.MYTHIC, 0);
        assertTrue(common > mythic, "COMMON should appear more often than MYTHIC at floor 5");
    }

    @Test
    void rarityDistributionAtFloor10IncludesAllRarities() {
        // Floor 10: push = 0.50. All rarities are eligible (10 >= all floorUnlock values).
        Map<Rarity, Integer> counts = new HashMap<>();
        int samples = 10000;
        for (int i = 0; i < samples; i++) {
            Rarity r = ItemPool.rollRarity(10);
            counts.merge(r, 1, Integer::sum);
        }
        assertTrue(counts.getOrDefault(Rarity.LEGENDARY, 0) > 0,
                "LEGENDARY should appear at floor 10");
        assertTrue(counts.getOrDefault(Rarity.MYTHIC, 0) > 0,
                "MYTHIC should appear at floor 10");
    }

    @Test
    void rarityDistributionAtFloor20IncludesMythic() {
        // Floor 20: push = 1.0. MYTHIC (0.60) is eligible.
        Map<Rarity, Integer> counts = new HashMap<>();
        int samples = 10000;
        for (int i = 0; i < samples; i++) {
            Rarity r = ItemPool.rollRarity(20);
            counts.merge(r, 1, Integer::sum);
        }
        assertTrue(counts.getOrDefault(Rarity.MYTHIC, 0) > 0,
                "MYTHIC should appear at floor 20");
    }

    @Test
    void rarityWeightsShiftWithFloorDepth() {
        // As floor increases, the proportion of COMMON should decrease
        int samples = 10000;
        double commonPctFloor0 = proportionCommon(0, samples);
        double commonPctFloor10 = proportionCommon(10, samples);
        assertTrue(commonPctFloor10 < commonPctFloor0,
                "COMMON proportion at floor 10 (" + commonPctFloor10
                        + ") should be lower than at floor 0 (" + commonPctFloor0 + ")");
    }

    private static double proportionCommon(int floor, int samples) {
        int common = 0;
        for (int i = 0; i < samples; i++) {
            if (ItemPool.rollRarity(floor) == Rarity.COMMON) common++;
        }
        return (double) common / samples;
    }
}