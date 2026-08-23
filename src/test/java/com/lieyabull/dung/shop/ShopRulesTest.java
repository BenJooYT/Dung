package com.lieyabull.dung.shop;

import com.lieyabull.dung.game.WorkstationRules;
import com.lieyabull.dung.items.Rarity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure cost/salvage math tests for {@link ShopRules} — no Bukkit runtime needed. */
public class ShopRulesTest {

    @Test
    void runShopCostsUseRunCoins() {
        assertEquals(24, ShopRules.costFor(ShopType.RUN, Category.WEAPON));
        assertEquals(18, ShopRules.costFor(ShopType.RUN, Category.ARMOR));
        assertEquals(18, ShopRules.costFor(ShopType.RUN, Category.MANA_SHIELD));
    }

    @Test
    void persistentShopCostsUsePersistentCoins() {
        assertEquals(60, ShopRules.costFor(ShopType.PERSISTENT, Category.WEAPON));
        assertEquals(45, ShopRules.costFor(ShopType.PERSISTENT, Category.ARMOR));
        assertEquals(45, ShopRules.costFor(ShopType.PERSISTENT, Category.MANA_SHIELD));
    }

    @Test
    void persistentCostsExceedRunCostsForEveryCategory() {
        for (Category c : Category.values()) {
            assertTrue(c.persistentCost() > c.runCost(),
                    c + " persistent cost should exceed its run cost");
        }
    }

    @Test
    void categoryCostDelegatesToShopType() {
        assertEquals(Category.WEAPON.runCost(), Category.WEAPON.cost(ShopType.RUN));
        assertEquals(Category.WEAPON.persistentCost(), Category.WEAPON.cost(ShopType.PERSISTENT));
    }

    @Test
    void salvageValueMirrorsWorkstationRulesAndScalesWithRarity() {
        assertEquals(WorkstationRules.salvageValue(Rarity.COMMON, 5), ShopRules.salvageValue(Rarity.COMMON, 5));
        assertEquals(WorkstationRules.salvageValue(Rarity.MYTHIC, 5), ShopRules.salvageValue(Rarity.MYTHIC, 5));
        int common = ShopRules.salvageValue(Rarity.COMMON, 5);
        int mythic = ShopRules.salvageValue(Rarity.MYTHIC, 5);
        assertTrue(mythic > common, "higher rarity should salvage for more shards");
        // never zero
        assertTrue(ShopRules.salvageValue(Rarity.COMMON, 0) >= 1);
    }

    @Test
    void articleLabelsAreSingularAndDistinct() {
        assertEquals("weapon", Category.WEAPON.articleLabel());
        assertEquals("armor", Category.ARMOR.articleLabel());
        assertEquals("mana shield", Category.MANA_SHIELD.articleLabel());
    }
}