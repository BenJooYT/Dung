package com.lieyabull.dung.items;

/**
 * Single source of truth for every Dung persistent-data tag key. Centralizing the key
 * strings here turns typos into compile-time errors instead of silent save incompatibility.
 */
public final class ItemTags {
    public static final String GEAR = "dung.gear";
    public static final String PERSISTENT = "dung.persistent";
    public static final String FAVORITE = "dung.favorite";
    public static final String KIND = "dung.kind";     // weapon|armor
    public static final String BASE = "dung.base";     // weapon/armor base id
    public static final String RARITY = "dung.rarity";
    public static final String DAMAGE = "dung.damage";
    public static final String REACH = "dung.reach";
    public static final String DEFENSE = "dung.defense";
    public static final String HEALTH = "dung.health";
    public static final String ABILITY = "dung.ability";
    public static final String COST = "dung.cost";
    public static final String DURABILITY = "dung.durability";
    public static final String MAX_DURABILITY = "dung.maxdurability";
    public static final String RUN_ITEM = "dung.runitem";   // key|bomb — run-only hotbar item
    public static final String STARTER = "dung.starter";    // free starter-kit gear — never salvageable
    public static final String UUID = "dung.uuid";        // unique persistent item identifier
    public static final String MANA_SHIELD = "dung.mana_shield"; // "true" if this is a mana shield item
    public static final String SHIELD_MAX = "dung.shield_max";   // max shield capacity for mana shield
    public static final String STORED_HEALTH = "dung.storedhealth"; // stored health for Life Drain weapon
    public static final String MAGIC_DAMAGE = "dung.magic_damage"; // magic damage value (separate from melee)
    public static final String AFFIXES = "dung.affixes";      // list of "affixId:value" applied to a gear item
    public static final String UPGRADE_LEVEL = "dung.upgrade_level"; // int: how many times an item was upgraded

    private ItemTags() {}
}
