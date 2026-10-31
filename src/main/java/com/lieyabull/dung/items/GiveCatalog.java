package com.lieyabull.dung.items;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only catalog of the gear that can be granted by the admin Give UI.
 * <p>
 * The live loot rolls use the same descriptors so the admin builder cannot drift away from
 * what the shop and room rewards actually produce.
 */
public final class GiveCatalog {

    /** One weapon base. {@code magicDamage} is separate from {@code baseDamage}; magic weapons
     *  deal only 1 melee damage but carry a separate magic-damage tag. */
    public record WeaponBase(
            String id,
            String name,
            Material material,
            int baseDamage,
            String ability,
            int abilityCost,
            int magicDamage,
            double reach,
            boolean bruiser
    ) {
        public String displayName() {
            return name;
        }
    }

    /** One armor set. {@code healthBase} is the chest-piece health before the slot factor and
     *  rarity multiplier are applied. */
    public record ArmorSet(
            String id,
            String name,
            Material helmet,
            Material chestplate,
            Material leggings,
            Material boots,
            int baseDefense,
            double healthBase,
            boolean emberguard
    ) {
        public Material material(int slot) {
            return switch (slot) {
                case 0 -> helmet;
                case 1 -> chestplate;
                case 2 -> leggings;
                case 3 -> boots;
                default -> throw new IllegalArgumentException("Armor slot must be 0-3: " + slot);
            };
        }

        public String pieceName(int slot) {
            return switch (slot) {
                case 0 -> "Helmet";
                case 1 -> "Chestplate";
                case 2 -> "Leggings";
                case 3 -> "Boots";
                default -> throw new IllegalArgumentException("Armor slot must be 0-3: " + slot);
            };
        }
    }

    /** A concrete armor piece: set + slot. */
    public record ArmorPiece(ArmorSet set, int slot) {
        public String displayName() {
            return set.name() + " " + set.pieceName(slot);
        }

        public String key() {
            return "armor:" + set.id() + ":" + slot;
        }
    }

    /** The single mana-shield base. */
    public record ShieldBase(String id, String name, Material material) {}

    public static final List<WeaponBase> WEAPONS = List.of(
            new WeaponBase("frayed_blade", "Frayed Blade", Material.IRON_SWORD, 5, "Rush", 15, 0, 0.0, false),
            new WeaponBase("crude_axe", "Crude Axe", Material.STONE_AXE, 6, "Cleave", 20, 0, 0.0, true),
            new WeaponBase("longsword", "Longsword", Material.IRON_SWORD, 8, "Slash", 18, 0, 3.8, true),
            new WeaponBase("war_hammer", "War Hammer", Material.IRON_AXE, 11, "Smash", 30, 0, 0.0, true),
            new WeaponBase("crystal_shard", "Crystal Shard", Material.DIAMOND_SWORD, 13, "Blade Storm", 35, 0, 0.0, false),
            new WeaponBase("arcane_staff", "Arcane Staff", Material.BLAZE_ROD, 12, "Arcane Bolt", 25, 12, 4.5, false),
            new WeaponBase("doomblade", "Doomblade", Material.DIAMOND_SWORD, 18, "Ravage", 45, 0, 4.0, true),
            new WeaponBase("storm_rod", "Storm Rod", Material.LIGHTNING_ROD, 14, "Chain Lightning", 35, 14, 4.5, false),
            new WeaponBase("blaze_staff", "Blaze Staff", Material.BLAZE_ROD, 12, "Fireball", 25, 12, 0.0, false),
            new WeaponBase("soul_siphon", "Soul Siphon", Material.NETHERITE_HOE, 12, "Life Drain", 20, 12, 0.0, false)
    );

    public static final List<ArmorSet> ARMOR_SETS = List.of(
            new ArmorSet("cloth", "Cloth", Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS, 1, 4, false),
            new ArmorSet("chain", "Chain", Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE,
                    Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS, 3, 7, false),
            new ArmorSet("iron", "Iron", Material.IRON_HELMET, Material.IRON_CHESTPLATE,
                    Material.IRON_LEGGINGS, Material.IRON_BOOTS, 6, 11, false),
            new ArmorSet("golden", "Golden", Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE,
                    Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS, 7, 13, false),
            new ArmorSet("diamond", "Diamond", Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
                    Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS, 10, 17, false),
            new ArmorSet("netherite", "Netherite", Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                    Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS, 14, 21, false)
    );

    /** Endgame armor branch. Only EMBER is legal for this set. */
    public static final ArmorSet EMBERGUARD = new ArmorSet(
            "emberguard", "Emberguard", Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS, 14, 21, true);

    public static final List<ArmorPiece> ARMOR_PIECES = buildPieces(ARMOR_SETS);
    public static final List<ArmorPiece> EMBERGUARD_PIECES = buildPieces(List.of(EMBERGUARD));
    public static final List<ArmorPiece> ALL_ARMOR_PIECES = buildPieces(allArmorSets());

    public static final ShieldBase MANA_SHIELD = new ShieldBase("mana_shield", "Mana Shield", Material.SHIELD);

    /** Live loot uses the normal six sets; Emberguard is a separate endgame branch. */
    public static final List<ArmorSet> LOOT_ARMOR_SETS = ARMOR_SETS;

    private GiveCatalog() {}

    private static List<ArmorSet> allArmorSets() {
        List<ArmorSet> out = new ArrayList<>(ARMOR_SETS);
        out.add(EMBERGUARD);
        return List.copyOf(out);
    }

    private static List<ArmorPiece> buildPieces(List<ArmorSet> sets) {
        List<ArmorPiece> out = new ArrayList<>();
        for (ArmorSet set : sets) {
            for (int slot = 0; slot < 4; slot++) out.add(new ArmorPiece(set, slot));
        }
        return List.copyOf(out);
    }

    public static WeaponBase weapon(String id) {
        for (WeaponBase base : WEAPONS) {
            if (base.id().equals(id)) return base;
        }
        return null;
    }

    public static ArmorSet armorSet(String id) {
        for (ArmorSet set : ARMOR_SETS) {
            if (set.id().equals(id)) return set;
        }
        return EMBERGUARD.id().equals(id) ? EMBERGUARD : null;
    }

    public static ArmorPiece armorPiece(String setId, int slot) {
        for (ArmorPiece piece : ALL_ARMOR_PIECES) {
            if (piece.set().id().equals(setId) && piece.slot() == slot) return piece;
        }
        return null;
    }
}
