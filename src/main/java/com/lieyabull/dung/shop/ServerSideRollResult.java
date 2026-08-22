package com.lieyabull.dung.shop;

import com.lieyabull.dung.items.Rarity;
import org.bukkit.inventory.ItemStack;

/**
 * The server-authoritative outcome of a shop roll. Generated exactly once, server-side, when the
 * player clicks ROLL (after the currency is charged). The item and rarity animations that follow are
 * only a visual presentation of this already-determined result — the client can never influence it.
 */
public final class ServerSideRollResult {
    public final ItemStack item;     // the generated gear (run gear, or persistent for the persistent shop)
    public final Rarity rarity;      // the rarity baked into the item
    public final Category category;  // which item pool produced it
    public final int salvageValue;   // shards awarded if the player chooses SALVAGE

    public ServerSideRollResult(ItemStack item, Rarity rarity, Category category, int salvageValue) {
        this.item = item;
        this.rarity = rarity;
        this.category = category;
        this.salvageValue = salvageValue;
    }
}