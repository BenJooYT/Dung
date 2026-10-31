package com.lieyabull.dung.items;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SkyBlock-style loot: weapons and armor defined by base templates, rolled with a rarity
 * that scales with floor depth. Higher rarities multiply damage/defense.
 * <p>
 * The base descriptors live in {@link GiveCatalog}; the exact builders below are shared by
 * live loot and the admin Give UI so both paths produce the same stats and tags.
 */
public final class ItemPool {

    /** Weapon selection weights — Doomblade has half weight (1) vs others (2) so it drops half as often. */
    private static final int[] WEAPON_WEIGHTS = {2, 2, 2, 2, 2, 2, 1, 2, 2, 2};
    /** Health factor per armor slot: head, chest, legs, boots. */
    private static final double[] SLOT_HEALTH = {0.65, 1.0, 0.80, 0.55};

    private ItemPool() {}

    /** Build one exact weapon base at the given rarity. */
    public static ItemStack createWeapon(GiveCatalog.WeaponBase base, Rarity r, Random rng) {
        int dmg = (int) Math.round(base.baseDamage() * r.statMult);
        int health = rollWeaponHealth(base, r, rng);
        ItemStack s = GearFactory.weapon(base.id(), base.name(), base.material(), r, dmg, health,
                base.ability(), base.abilityCost());
        if (base.magicDamage() > 0) {
            s = GearFactory.withMagicDamage(s, (int) Math.round(base.magicDamage() * r.statMult));
        }
        if (base.reach() > 0) {
            s = GearFactory.withReach(s, base.reach());
        }
        return s;
    }

    /** Build one exact armor piece. Emberguard pieces are forced to {@link Rarity#EMBER}. */
    public static ItemStack createArmor(GiveCatalog.ArmorPiece piece, Rarity r, Random rng) {
        GiveCatalog.ArmorSet set = piece.set();
        int slot = piece.slot();
        if (set.emberguard()) {
            int def = (int) Math.round(set.baseDefense() * Rarity.EMBER.statMult);
            int health = (int) Math.round(set.healthBase() * SLOT_HEALTH[slot] * Rarity.EMBER.statMult);
            return GearFactory.emberArmor(slot, def, health);
        }
        int def = (int) Math.round(set.baseDefense() * r.statMult);
        int health = rollArmorHealth(set, slot, r, rng);
        Material mat = set.material(slot);
        if (mat == null) mat = Material.LEATHER_HELMET; // defensive fallback: never crash loot
        return GearFactory.armor(set.id() + "_" + slot, set.name(), mat, r, def, health);
    }

    /** Build one Emberguard piece directly (the live loot branch). */
    public static ItemStack createEmberguard(int slot) {
        int def = (int) Math.round(GiveCatalog.EMBERGUARD.baseDefense() * Rarity.EMBER.statMult);
        int health = (int) Math.round(GiveCatalog.EMBERGUARD.healthBase() * SLOT_HEALTH[slot]
                * Rarity.EMBER.statMult);
        return GearFactory.emberArmor(slot, def, health);
    }

    /** Build one mana shield at the given rarity. */
    public static ItemStack createShield(Rarity r) {
        return GearFactory.shield(r);
    }

    private static int rollWeaponHealth(GiveCatalog.WeaponBase base, Rarity r, Random rng) {
        if (!base.bruiser()) return 0;
        double v = 8 * r.statMult * (0.8 + rng.nextDouble() * 0.4);
        return Math.max(0, (int) Math.round(v));
    }

    private static int rollArmorHealth(GiveCatalog.ArmorSet set, int slot, Rarity r, Random rng) {
        double v = set.healthBase() * SLOT_HEALTH[slot] * r.statMult
                * (0.9 + rng.nextDouble() * 0.3);
        return Math.max(0, (int) Math.round(v));
    }

    /** Roll rarity given floor (0-based). A rarity is only eligible once floor >= its unlock;
     *  higher floors push toward the rarer tiers (uncapped, so deep floors keep improving). */
    public static Rarity rollRarity(int floor) {
        double push = floor * 0.03;
        List<Rarity> eligible = new ArrayList<>();
        for (Rarity r : Rarity.values()) {
            if (floor >= r.floorUnlock) eligible.add(r);
        }
        if (eligible.isEmpty()) eligible.add(Rarity.COMMON);
        double[] weights = new double[eligible.size()];
        double total = 0;
        for (int i = 0; i < eligible.size(); i++) {
            Rarity r = eligible.get(i);
            double w = r.baseChance * (1.0 + push * r.ordinal() * 0.5);
            weights[i] = w;
            total += w;
        }
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll <= 0) return eligible.get(i);
        }
        return eligible.get(eligible.size() - 1);
    }

    public static ItemStack randomWeapon(int floor) {
        List<GiveCatalog.WeaponBase> bases = GiveCatalog.WEAPONS;
        int[] weights = WEAPON_WEIGHTS;
        if (weights.length != bases.size()) {
            Bukkit.getLogger().warning("[Dung] WEAPONS/WEAPON_WEIGHTS length mismatch ("
                    + bases.size() + " vs " + weights.length + ") — falling back to uniform weights.");
            weights = new int[bases.size()];
            java.util.Arrays.fill(weights, 1);
        }
        int totalWeight = 0;
        for (int wgt : weights) totalWeight += wgt;
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int idx = 0;
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) { idx = i; break; }
        }
        GiveCatalog.WeaponBase base = bases.get(idx);
        Rarity r = rollRarity(floor);
        // Doomblade: halve the mythic chance by re-rolling without MYTHIC.
        if ("doomblade".equals(base.id()) && r == Rarity.MYTHIC
                && ThreadLocalRandom.current().nextBoolean()) {
            r = rollRarityNoMythic(floor);
        }
        return createWeapon(base, r, ThreadLocalRandom.current());
    }

    /** Roll a random armor piece. slot: 0=head,1=chest,2=legs,3=boots. */
    public static ItemStack randomArmor(int floor, int slot) {
        Rarity r = rollRarity(floor);
        // Emberguard is an endgame armor-only branch for now. It uses exactly half of the
        // Mythic outcome rate, while leaving the other half as ordinary Mythic armor.
        if (floor >= 7 && r == Rarity.MYTHIC && ThreadLocalRandom.current().nextBoolean()) {
            return createEmberguard(slot);
        }
        GiveCatalog.ArmorSet set = GiveCatalog.LOOT_ARMOR_SETS.get(
                ThreadLocalRandom.current().nextInt(GiveCatalog.LOOT_ARMOR_SETS.size()));
        return createArmor(new GiveCatalog.ArmorPiece(set, slot), r, ThreadLocalRandom.current());
    }

    /** Roll rarity but exclude MYTHIC (for Doomblade mythic-halving). */
    private static Rarity rollRarityNoMythic(int floor) {
        double push = floor * 0.05;
        List<Rarity> eligible = new ArrayList<>();
        for (Rarity r : Rarity.values()) {
            if (r == Rarity.MYTHIC) continue;
            if (floor >= r.floorUnlock) eligible.add(r);
        }
        if (eligible.isEmpty()) eligible.add(Rarity.COMMON);
        double[] weights = new double[eligible.size()];
        double total = 0;
        for (int i = 0; i < eligible.size(); i++) {
            Rarity r = eligible.get(i);
            double w = r.baseChance * (1.0 + push * r.ordinal() * 0.5);
            weights[i] = w;
            total += w;
        }
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll <= 0) return eligible.get(i);
        }
        return eligible.get(eligible.size() - 1);
    }

    /** Roll a random shield item. Shields drop at a lower rate than weapons/armor. */
    public static ItemStack randomShield(int floor) {
        return createShield(rollRarity(floor));
    }

    /** A full "loot reward" for clearing a room: chance of gear + always some coins. */
    public static List<ItemStack> roomReward(int floor, int roomKind) {
        List<ItemStack> out = new ArrayList<>();
        double gearChance = switch (roomKind) {
            case 1 -> 0.30;  // combat
            case 2 -> 1.0;   // treasure
            case 3 -> 0.25;  // shop
            case 4 -> 0.55;  // secret
            case 5 -> 1.0;   // elite
            case 6 -> 1.0;   // boss
            case 9 -> 0.75;  // event
            default -> 0.0;
        };
        if (ThreadLocalRandom.current().nextDouble() < gearChance) {
            double roll = ThreadLocalRandom.current().nextDouble();
            if (roll < 0.40) { // 40% weapon
                out.add(randomWeapon(floor));
            } else if (roll < 0.75) { // 35% armor
                out.add(randomArmor(floor, ThreadLocalRandom.current().nextInt(4)));
            } else { // 25% shield (lower weight than weapons/armor)
                out.add(randomShield(floor));
            }
        }
        return out;
    }
}
