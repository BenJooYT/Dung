package com.lieyabull.dung.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A single procedural stat modifier that can be rolled onto a run-gear item. REFORGE workstations
 * reroll an item's affix set; UPGRADE adds to the item's core stat. Affixes are stored on the item's
 * PDC as a list of {@code "id:value"} strings so the set can be freely rerolled without touching the
 * item's base stats, rarity, or ability.
 * <p>
 * This is intentionally small and data-driven: adding a new affix is just a new enum constant.
 * Each affix names which stat it boosts and a per-level value curve, and declares which item kinds
 * it may appear on.
 */
public enum Affix {
    VICIOUS("vicious", "Vicious", Stat.DAMAGE, 2, Kinds.WEAPON),
    ARCANE("arcane", "Arcane", Stat.MAGIC_DAMAGE, 2, Kinds.WEAPON),
    STURDY("sturdy", "Sturdy", Stat.DEFENSE, 2, Kinds.ARMOR),
    VITAL("vital", "Vital", Stat.HEALTH, 3, Kinds.WEAPON_AND_ARMOR),
    AEGIS("aegis", "Aegis", Stat.SHIELD_MAX, 5, Kinds.SHIELD);

    /** The stat an affix boosts. Mirrors the PDC stat tags so recomputeStats can sum them. */
    public enum Stat {
        DAMAGE(ItemTags.DAMAGE, "§c"),
        MAGIC_DAMAGE(ItemTags.MAGIC_DAMAGE, "§d"),
        DEFENSE(ItemTags.DEFENSE, "§a"),
        HEALTH(ItemTags.HEALTH, "§a"),
        SHIELD_MAX(ItemTags.SHIELD_MAX, "§b");

        public final String tag;
        public final String color;

        Stat(String tag, String color) {
            this.tag = tag;
            this.color = color;
        }
    }

    private static final class Kinds {
        static final String WEAPON = "weapon";
        static final String ARMOR = "armor";
        static final String SHIELD = "shield";
        static final String WEAPON_AND_ARMOR = "weaponarmor";
        static final String ANY = "any";
    }

    public final String id;
    public final String label;
    public final Stat stat;
    /** Base value at rarity COMMON; higher rarities scale it via {@link Rarity#statMult}. */
    public final int base;
    private final String kindMask;

    Affix(String id, String label, Stat stat, int base, String kindMask) {
        this.id = id;
        this.label = label;
        this.stat = stat;
        this.base = base;
        this.kindMask = kindMask;
    }

    /** Whether this affix may appear on an item of the given dung.kind. */
    public boolean appliesTo(String kind) {
        if (kind == null) return false;
        if (Kinds.ANY.equals(kindMask)) return true;
        if (Kinds.WEAPON_AND_ARMOR.equals(kindMask)) {
            return Kinds.WEAPON.equals(kind) || Kinds.ARMOR.equals(kind);
        }
        return kindMask.equals(kind);
    }

    /** Affixes eligible for an item of the given dung.kind. */
    public static List<Affix> poolFor(String kind) {
        List<Affix> out = new ArrayList<>();
        for (Affix a : values()) if (a.appliesTo(kind)) out.add(a);
        return out;
    }

    /** Roll an affix value scaled by the item's rarity. */
    public int valueFor(Rarity r) {
        double mult = r == null ? 1.0 : r.statMult;
        return Math.max(1, (int) Math.round(base * mult));
    }

    /**
     * Roll a fresh affix set for an item of the given rarity and kind. The number of affixes grows
     * with rarity (COMMON=0 .. MYTHIC=3); each is a distinct, kind-eligible affix at its rarity value.
     */
    public static List<AffixRoll> roll(Rarity rarity, String kind, Random rng) {
        int count = countFor(rarity);
        if (count <= 0) return Collections.emptyList();
        List<Affix> pool = new ArrayList<>(poolFor(kind));
        if (pool.isEmpty()) return Collections.emptyList();
        Collections.shuffle(pool, rng);
        List<AffixRoll> out = new ArrayList<>();
        for (int i = 0; i < count && i < pool.size(); i++) {
            Affix a = pool.get(i);
            out.add(new AffixRoll(a, a.valueFor(rarity)));
        }
        return out;
    }

    /** How many affixes an item of this rarity carries (0..3). */
    public static int countFor(Rarity r) {
        if (r == null) return 0;
        return switch (r) {
            case COMMON -> 0;
            case UNCOMMON -> 1;
            case RARE -> 1;
            case EPIC -> 2;
            case LEGENDARY -> 2;
            case MYTHIC -> 3;
        };
    }

    /** Serialize an affix roll for PDC storage as {@code "id:value"}. */
    public static String serialize(AffixRoll roll) {
        return roll.affix().id + ":" + roll.value();
    }

    /** A single applied affix: which affix and its rolled value. */
    public record AffixRoll(Affix affix, int value) {}
}