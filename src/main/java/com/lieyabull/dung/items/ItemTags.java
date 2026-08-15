package com.lieyabull.dung.items;

/**
 * Single source of truth for every Dung persistent-data tag key. Centralizing the key
 * strings here turns typos into compile-time errors instead of silent save incompatibility.
 */
public final class ItemTags {
    public static final String GEAR = "dung.gear";
    public static final String PERSISTENT = "dung.persistent";
    public static final String KIND = "dung.kind";     // weapon|armor
    public static final String BASE = "dung.base";     // weapon/armor base id
    public static final String RARITY = "dung.rarity";
    public static final String DAMAGE = "dung.damage";
    public static final String REACH = "dung.reach";
    public static final String DEFENSE = "dung.defense";
    public static final String HEALTH = "dung.health";
    public static final String ABILITY = "dung.ability";
    public static final String COST = "dung.cost";

    private ItemTags() {}
}
