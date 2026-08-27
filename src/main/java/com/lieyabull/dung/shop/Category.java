package com.lieyabull.dung.shop;

import org.bukkit.Material;

/** The three purchasable gear categories shared by the run shop and persistent shop. Each category
 *  selects which existing item pool produces the rolled item, and carries its per-shop roll cost. */
public enum Category {
    WEAPON("Weapons", Material.IRON_SWORD, 12, 60),
    ARMOR("Armor", Material.IRON_CHESTPLATE, 9, 45),
    MANA_SHIELD("Mana Shields", Material.SHIELD, 9, 45);

    private final String label;
    private final Material icon;
    private final int runCost;
    private final int persistentCost;

    Category(String label, Material icon, int runCost, int persistentCost) {
        this.label = label;
        this.icon = icon;
        this.runCost = runCost;
        this.persistentCost = persistentCost;
    }

    public String label() {
        return label;
    }

    public Material icon() {
        return icon;
    }

    public int runCost() {
        return runCost;
    }

    public int persistentCost() {
        return persistentCost;
    }

    /** The roll cost for this category in the given shop. */
    public int cost(ShopType type) {
        return type == ShopType.RUN ? runCost : persistentCost;
    }

    /** Singular display label for messages ("a weapon", "a mana shield", ...). */
    public String articleLabel() {
        return switch (this) {
            case WEAPON -> "weapon";
            case ARMOR -> "armor";
            case MANA_SHIELD -> "mana shield";
        };
    }
}