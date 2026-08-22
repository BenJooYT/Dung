package com.lieyabull.dung.shop;

import com.lieyabull.dung.game.WorkstationRules;
import com.lieyabull.dung.items.Rarity;

/** Pure shop cost/salvage math shared by both shops. No Bukkit state — unit-testable. */
public final class ShopRules {
    private ShopRules() {}

    /** The roll cost for a category in a shop. */
    public static int costFor(ShopType type, Category category) {
        return category.cost(type);
    }

    /** Shard reward for salvaging a rolled item. Uses the existing salvage formula so shard values
     *  stay consistent with the rest of the economy. */
    public static int salvageValue(Rarity rarity, int primaryStat) {
        return WorkstationRules.salvageValue(rarity, primaryStat);
    }
}