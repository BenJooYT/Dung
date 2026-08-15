package com.lieyabull.dung.items;

import net.kyori.adventure.text.format.NamedTextColor;

/** SkyBlock-style item rarities. Higher rarity scales stats and drops more often from later floors. */
public enum Rarity {
    COMMON(NamedTextColor.GRAY, "§7", 1.0, 0.00, 0.20),
    UNCOMMON(NamedTextColor.GREEN, "§a", 1.15, 0.06, 0.22),
    RARE(NamedTextColor.AQUA, "§b", 1.35, 0.14, 0.18),
    EPIC(NamedTextColor.LIGHT_PURPLE, "§d", 1.60, 0.26, 0.16),
    LEGENDARY(NamedTextColor.GOLD, "§6", 1.90, 0.42, 0.14),
    MYTHIC(NamedTextColor.DARK_RED, "§4", 2.30, 0.60, 0.10);

    public final NamedTextColor text;
    public final String legacy;
    public final double statMult;
    public final double floorUnlock; // earliest floor (fractional) this rarity appears
    public final double baseChance;  // share of the base (floor 0) distribution

    Rarity(NamedTextColor text, String legacy, double statMult, double floorUnlock, double baseChance) {
        this.text = text;
        this.legacy = legacy;
        this.statMult = statMult;
        this.floorUnlock = floorUnlock;
        this.baseChance = baseChance;
    }
}