package com.lieyabull.dung.items;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/** Turns a {@link GiveSpec} into the exact item the admin UI previews and grants. */
public final class GiveItemFactory {

    private GiveItemFactory() {}

    public static ItemStack build(GiveSpec spec, Player recipient) {
        Random rng = new Random(spec.buildSeed());
        ItemStack item = switch (spec.category()) {
            case WEAPON -> ItemPool.createWeapon(spec.weaponBase(), spec.rarity(), rng);
            case ARMOR -> ItemPool.createArmor(spec.armorPiece(), spec.rarity(), rng);
            case SHIELD -> ItemPool.createShield(spec.rarity());
        };

        // Upgrade scales the stored core stat; reforge then rewrites affixes and lore at the
        // same level (reforge does not scale the core stat itself).
        GearFactory.setUpgradeLevel(item, spec.upgradeLevel());
        GearFactory.reforge(item, spec.affixRolls(), spec.upgradeLevel());
        GearFactory.finalizeRarityLook(item);
        if (spec.persistent()) GearFactory.markPersistent(item);
        if (recipient != null) GearFactory.localizeFor(item, recipient);
        return item;
    }
}
