package com.lieyabull.dung.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SkyBlock-style loot: weapons and armor defined by base templates, rolled with a rarity
 * that scales with floor depth. Higher rarities multiply damage/defense.
 */
public final class ItemPool {
    // weapon templates: name, material, base dmg, ability, mana cost
    // Each entry has a weight; Doomblade has half weight (1) vs others (2) so it drops half as often.
    private static final String[][] WEAPONS = {
        {"Frayed Blade", "IRON_SWORD", "5", "Rush", "15"},
        {"Crude Axe", "STONE_AXE", "6", "Cleave", "20"},
        {"Longsword", "IRON_SWORD", "8", "Slash", "18"},
        {"War Hammer", "IRON_AXE", "11", "Smash", "30"},
        {"Crystal Shard", "DIAMOND_SWORD", "13", "Blade Storm", "35"},
        {"Arcane Staff", "BLAZE_ROD", "12", "Arcane Bolt", "25"},
        {"Doomblade", "DIAMOND_SWORD", "18", "Ravage", "45"},
        {"Storm Rod", "LIGHTNING_ROD", "14", "Chain Lightning", "35"},
        {"Blaze Staff", "BLAZE_ROD", "12", "Fireball", "25"},
        {"Soul Siphon", "NETHERITE_HOE", "12", "Life Drain", "20"},
    };
    // Weapon selection weights — Doomblade has half weight (1) vs others (2) so it drops half as often.
    private static final int[] WEAPON_WEIGHTS = {2, 2, 2, 2, 2, 2, 1, 2, 2, 2};
    // armor templates (head/chest/legs/boots) per base set
    private static final String[][] ARMOR_BASES = {
        {"Cloth", "LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS", "1"},
        {"Chain", "CHAINMAIL_HELMET", "CHAINMAIL_CHESTPLATE", "CHAINMAIL_LEGGINGS", "CHAINMAIL_BOOTS", "3"},
        {"Iron", "IRON_HELMET", "IRON_CHESTPLATE", "IRON_LEGGINGS", "IRON_BOOTS", "6"},
        {"Golden", "GOLDEN_HELMET", "GOLDEN_CHESTPLATE", "GOLDEN_LEGGINGS", "GOLDEN_BOOTS", "7"},
        {"Diamond", "DIAMOND_HELMET", "DIAMOND_CHESTPLATE", "DIAMOND_LEGGINGS", "DIAMOND_BOOTS", "10"},
        {"Netherite", "NETHERITE_HELMET", "NETHERITE_CHESTPLATE", "NETHERITE_LEGGINGS", "NETHERITE_BOOTS", "14"},
    };

    private ItemPool() {}

    /** A reach-boosting weapon boosts the melee reach above the 3.0 default. */
    private static final double reachOf(String id) {
        switch (id) {
            case "longsword": return 3.8;
            case "arcane_staff": return 4.5;
            case "doomblade": return 4.0;
            case "storm_rod": return 4.5;
            default: return 0.0;
        }
    }

    // Health affixes ("where logical"): armour carries the bulk of extra HP and scales with the
    // material tier, the slot (chest is the biggest piece, boots the smallest) and rarity. Heavy
    // melee weapons give a small bruiser bonus (they imply bulk/fortitude); mage/light weapons none.
    private static final double[] ARMOR_HEALTH = {4, 7, 11, 13, 17, 21}; // per ARMOR_BASES index
    private static final double[] SLOT_HEALTH = {0.65, 1.0, 0.80, 0.55}; // head, chest, legs, boots
    private static final java.util.Set<String> BRUISER = java.util.Set.of(
            "war_hammer", "doomblade", "longsword", "crude_axe");

    private static int rollWeaponHealth(String id, Rarity r) {
        if (!BRUISER.contains(id)) return 0;
        double v = 8 * r.statMult * (0.8 + ThreadLocalRandom.current().nextDouble() * 0.4);
        return Math.max(0, (int) Math.round(v));
    }

    private static int rollArmorHealth(int set, int slot, Rarity r) {
        double v = ARMOR_HEALTH[set] * SLOT_HEALTH[slot] * r.statMult
                * (0.9 + ThreadLocalRandom.current().nextDouble() * 0.3);
        return Math.max(0, (int) Math.round(v));
    }

    /** Roll rarity given floor (0-based). A rarity is only eligible once floor >= its unlock;
     *  higher floors push toward the rarer tiers (uncapped, so deep floors keep improving). */
    public static Rarity rollRarity(int floor) {
        // no hard cap: deep floors should keep improving drops. push grows without bound.
        double push = floor * 0.05;
        // only rarities that have unlocked at this floor may appear (COMMON always is).
        List<Rarity> eligible = new ArrayList<>();
        for (Rarity r : Rarity.values()) {
            if (floor >= r.floorUnlock) eligible.add(r);
        }
        if (eligible.isEmpty()) eligible.add(Rarity.COMMON);
        // weight a single roll against a table; higher rarities get boosted by push, and the
        // sum is normalized so push may exceed 1.0 without breaking the thresholds.
        double[] weights = new double[eligible.size()];
        double total = 0;
        for (int i = 0; i < eligible.size(); i++) {
            Rarity r = eligible.get(i);
            // Scale the rarity boost by ordinal but halve it so low floors keep COMMON as the
            // most common tier; deep floors (large push) still climb toward the rare tiers.
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
        // Weighted selection — Doomblade has half weight
        int totalWeight = 0;
        for (int wgt : WEAPON_WEIGHTS) totalWeight += wgt;
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int idx = 0;
        for (int i = 0; i < WEAPON_WEIGHTS.length; i++) {
            roll -= WEAPON_WEIGHTS[i];
            if (roll < 0) { idx = i; break; }
        }
        String[] w = WEAPONS[idx];
        Rarity r = rollRarity(floor);
        int base = Integer.parseInt(w[2]);
        int dmg = (int) Math.round(base * r.statMult);
        String id = w[0].toLowerCase().replace(' ', '_');
        Material mat = Material.matchMaterial(w[1]);
        if (mat == null) mat = Material.WOODEN_SWORD; // defensive fallback: never crash loot
        ItemStack s = GearFactory.weapon(id,
                w[0], mat, r, dmg, rollWeaponHealth(id, r), w[3], Integer.parseInt(w[4]));
        // Magic weapons (Arcane Staff, Storm Rod, Blaze Staff, Soul Siphon) have separate
        // magic damage and only deal 1 melee damage. The magic damage is stored in a separate PDC tag.
        String name = w[0];
        int magicDmg = switch (name) {
            case "Arcane Staff" -> 12;
            case "Storm Rod" -> 14;
            case "Blaze Staff" -> 12;
            case "Soul Siphon" -> 12;
            default -> 0;
        };
        if (magicDmg > 0) {
            int scaledMagic = (int) Math.round(magicDmg * r.statMult);
            s = GearFactory.withMagicDamage(s, scaledMagic);
        }
        double reach = reachOf(id);
        s = reach > 0 ? GearFactory.withReach(s, reach) : s;
        // Doomblade: halve the mythic chance by re-rolling rarity if it rolled MYTHIC
        if ("doomblade".equals(id) && r == Rarity.MYTHIC && ThreadLocalRandom.current().nextBoolean()) {
            // Downgrade to a non-mythic rarity by re-rolling without MYTHIC eligible
            Rarity downgrade = rollRarityNoMythic(floor);
            int newDmg = (int) Math.round(base * downgrade.statMult);
            int newHealth = rollWeaponHealth(id, downgrade);
            int newMagic = magicDmg > 0 ? (int) Math.round(magicDmg * downgrade.statMult) : 0;
            ItemStack ds = s;
            ds.editMeta(meta -> {
                var pdc = meta.getPersistentDataContainer();
                pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.RARITY),
                        org.bukkit.persistence.PersistentDataType.STRING, downgrade.name());
                pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.DAMAGE),
                        org.bukkit.persistence.PersistentDataType.INTEGER, newDmg);
                pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.HEALTH),
                        org.bukkit.persistence.PersistentDataType.INTEGER, newHealth);
                if (newMagic > 0) {
                    pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.MAGIC_DAMAGE),
                            org.bukkit.persistence.PersistentDataType.INTEGER, newMagic);
                }
                // Recolor the display name for the new rarity
                String dn = meta.getDisplayName();
                if (dn != null) {
                    // Strip old color codes and re-apply the new rarity color
                    String stripped = dn.replaceAll("§[0-9a-fklmnor]", "");
                    meta.setDisplayName(downgrade.legacy + stripped);
                }
            });
            s = ds;
        }
        return s;
    }

    /** Roll a random armor piece. slot: 0=head,1=chest,2=legs,3=boots. */
    public static ItemStack randomArmor(int floor, int slot) {
        int set = ThreadLocalRandom.current().nextInt(ARMOR_BASES.length);
        String[] b = ARMOR_BASES[set];
        Rarity r = rollRarity(floor);
        int base = Integer.parseInt(b[5]);
        int def = (int) Math.round(base * r.statMult);
        String id = b[0].toLowerCase().replace(' ', '_') + "_" + slot;
        Material mat = Material.matchMaterial(b[slot + 1]);
        if (mat == null) mat = Material.LEATHER_HELMET; // defensive fallback: never crash loot
        return GearFactory.armor(id, b[0], mat, r, def, rollArmorHealth(set, slot, r));
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
        Rarity r = rollRarity(floor);
        return GearFactory.shield(r);
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