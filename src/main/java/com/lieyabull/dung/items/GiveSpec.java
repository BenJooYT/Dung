package com.lieyabull.dung.items;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The complete selection behind one admin Give grant. Pure data: no Bukkit world or inventory
 * access, so the UI can mutate it freely and tests can exercise the validation rules headlessly.
 */
public final class GiveSpec {

    public enum Category { WEAPON, ARMOR, SHIELD }

    /** Matches {@code WorkstationRules.UPGRADE_MAX}; kept here so the pure spec has no game-layer dependency. */
    public static final int MAX_UPGRADE = 5;
    /** The normal maximum number of affixes on an item (MYTHIC/EMBER). */
    public static final int MAX_AFFIXES = 3;

    private final Category category;
    private final GiveCatalog.WeaponBase weapon;
    private final GiveCatalog.ArmorPiece armorPiece;
    private final GiveCatalog.ShieldBase shield;
    private final EnumSet<Affix> affixes = EnumSet.noneOf(Affix.class);
    private Rarity rarity;
    private int upgradeLevel;
    private boolean persistent;
    private UUID target;
    private long buildSeed;

    private GiveSpec(Category category, GiveCatalog.WeaponBase weapon,
                     GiveCatalog.ArmorPiece armorPiece, GiveCatalog.ShieldBase shield) {
        this.category = category;
        this.weapon = weapon;
        this.armorPiece = armorPiece;
        this.shield = shield;
        this.rarity = armorPiece != null && armorPiece.set().emberguard()
                ? Rarity.EMBER : Rarity.COMMON;
        this.buildSeed = ThreadLocalRandom.current().nextLong();
    }

    public static GiveSpec weapon(GiveCatalog.WeaponBase base, UUID target) {
        GiveSpec spec = new GiveSpec(Category.WEAPON, base, null, null);
        spec.target = target;
        return spec;
    }

    public static GiveSpec armor(GiveCatalog.ArmorPiece piece, UUID target) {
        GiveSpec spec = new GiveSpec(Category.ARMOR, null, piece, null);
        spec.target = target;
        return spec;
    }

    public static GiveSpec shield(GiveCatalog.ShieldBase base, UUID target) {
        GiveSpec spec = new GiveSpec(Category.SHIELD, null, null, base);
        spec.target = target;
        return spec;
    }

    public Category category() {
        return category;
    }

    public GiveCatalog.WeaponBase weaponBase() {
        return weapon;
    }

    public GiveCatalog.ArmorPiece armorPiece() {
        return armorPiece;
    }

    public GiveCatalog.ShieldBase shieldBase() {
        return shield;
    }

    /** The dungeon kind tag used by affix eligibility and the item builders. */
    public String kind() {
        return switch (category) {
            case WEAPON -> "weapon";
            case ARMOR -> "armor";
            case SHIELD -> "shield";
        };
    }

    public String displayName() {
        return switch (category) {
            case WEAPON -> weapon.displayName();
            case ARMOR -> armorPiece.displayName();
            case SHIELD -> shield.name();
        };
    }

    public boolean emberguard() {
        return armorPiece != null && armorPiece.set().emberguard();
    }

    public Rarity rarity() {
        return rarity;
    }

    /** Emberguard is EMBER-only; every other gear kind accepts COMMON..MYTHIC. */
    public boolean supportsRarity(Rarity r) {
        if (r == null) return false;
        if (emberguard()) return r == Rarity.EMBER;
        return r != Rarity.EMBER;
    }

    /** Change rarity when legal. Returns false when the caller should keep the current rarity. */
    public boolean setRarity(Rarity r) {
        if (!supportsRarity(r)) return false;
        rarity = r;
        return true;
    }

    public int upgradeLevel() {
        return upgradeLevel;
    }

    public void setUpgradeLevel(int level) {
        upgradeLevel = Math.max(0, Math.min(MAX_UPGRADE, level));
    }

    public boolean persistent() {
        return persistent;
    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    public void togglePersistent() {
        persistent = !persistent;
    }

    public UUID target() {
        return target;
    }

    public void setTarget(UUID target) {
        this.target = target;
    }

    public long buildSeed() {
        return buildSeed;
    }

    public void setBuildSeed(long buildSeed) {
        this.buildSeed = buildSeed;
    }

    public List<Affix> affixes() {
        return List.copyOf(affixes);
    }

    public boolean hasAffix(Affix affix) {
        return affix != null && affixes.contains(affix);
    }

    /** Toggle one eligible affix. Returns false when the affix is ineligible or the cap is reached. */
    public boolean toggleAffix(Affix affix) {
        if (affix == null || !affix.appliesTo(kind())) return false;
        if (affixes.remove(affix)) return true;
        if (affixes.size() >= MAX_AFFIXES) return false;
        affixes.add(affix);
        return true;
    }

    public void clearAffixes() {
        affixes.clear();
    }

    /** Roll a fresh concrete affix set for the current rarity/kind. */
    public void randomizeAffixes(Random rng) {
        affixes.clear();
        for (Affix.AffixRoll roll : Affix.roll(rarity, kind(), rng)) {
            affixes.add(roll.affix());
        }
    }

    /** Selected affixes with values scaled to the current rarity. */
    public List<Affix.AffixRoll> affixRolls() {
        return affixes.stream()
                .map(a -> new Affix.AffixRoll(a, a.valueFor(rarity)))
                .toList();
    }

    public String affixSummary() {
        if (affixes.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (Affix a : affixes) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(a.label).append(" +").append(a.valueFor(rarity));
        }
        return sb.toString();
    }

    public String describe() {
        return displayName() + " [" + rarity + ", upgrade " + upgradeLevel
                + ", affixes " + affixSummary() + (persistent ? ", persistent" : ", run gear") + "]";
    }

}
