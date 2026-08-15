package com.lieyabull.dung.items;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds rarity-colored ItemStacks with SkyBlock-style lore (Stats / Ability lines).
 * Every crafted item is tagged so listeners can identify Dung gear vs normal loot.
 */
public final class GearFactory {
    private GearFactory() {}

    /** weapon: (id, name, material, rarity, minDmg, maxDmg, ability, abilityCost) */
    public static ItemStack weapon(String id, String name, Material mat, Rarity r,
                                   int dmg, int health, String ability, int abilityCost) {
        ItemStack s = new ItemStack(mat);
        s.editMeta(meta -> {
            meta.setDisplayName(r.legacy + name);
            meta.setLore(weaponLore(r, dmg, health, ability, abilityCost));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            if (r.ordinal() >= Rarity.RARE.ordinal()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            }
            var pdc = meta.getPersistentDataContainer();
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.GEAR),
                    org.bukkit.persistence.PersistentDataType.STRING, "true");
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.KIND),
                    org.bukkit.persistence.PersistentDataType.STRING, "weapon");
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.BASE),
                    org.bukkit.persistence.PersistentDataType.STRING, id);
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.RARITY),
                    org.bukkit.persistence.PersistentDataType.STRING, r.name());
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.DAMAGE),
                    org.bukkit.persistence.PersistentDataType.INTEGER, dmg);
            if (health > 0) {
                pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.HEALTH),
                        org.bukkit.persistence.PersistentDataType.INTEGER, health);
            }
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.ABILITY),
                    org.bukkit.persistence.PersistentDataType.STRING, ability);
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.COST),
                    org.bukkit.persistence.PersistentDataType.INTEGER, abilityCost);
        });
        return s;
    }

    public static ItemStack withReach(ItemStack s, double reach) {
        s.editMeta(meta -> {
            var pdc = meta.getPersistentDataContainer();
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.REACH),
                    org.bukkit.persistence.PersistentDataType.DOUBLE, reach);
        });
        return s;
    }

    /** Flag gear bought with persistent currency: it survives death (run gear is stripped). */
    public static ItemStack markPersistent(ItemStack s) {
        s.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.minecraft(ItemTags.PERSISTENT),
                    org.bukkit.persistence.PersistentDataType.STRING, "true");
        });
        return s;
    }

    /** True if the item is marked favorite (skipped by salvage). */
    public static boolean isFavorite(ItemStack s) {
        if (s == null || s.getItemMeta() == null) return false;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        return pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.FAVORITE),
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    /** Toggle the favorite flag; returns the new state (true = now favorited). */
    public static boolean toggleFavorite(ItemStack s) {
        final boolean[] state = {false};
        s.editMeta(meta -> {
            var pdc = meta.getPersistentDataContainer();
            var key = org.bukkit.NamespacedKey.minecraft(ItemTags.FAVORITE);
            boolean fav = pdc.has(key, org.bukkit.persistence.PersistentDataType.STRING);
            if (fav) pdc.remove(key);
            else pdc.set(key, org.bukkit.persistence.PersistentDataType.STRING, "true");
            state[0] = !fav;
        });
        return state[0];
    }

    /** First-run kit: a Frayed Blade + a full Cloth set, so a new player can fight immediately.
     *  Order: [0]=weapon, [1..4]=helmet, chestplate, leggings, boots. Run gear (not persistent). */
    public static ItemStack[] starter() {
        return new ItemStack[]{
                weapon("frayed_blade", "Frayed Blade", Material.IRON_SWORD, Rarity.COMMON, 5, 0, "Rush", 15),
                armor("cloth_0", "Cloth", Material.LEATHER_HELMET, Rarity.COMMON, 1, 0),
                armor("cloth_1", "Cloth", Material.LEATHER_CHESTPLATE, Rarity.COMMON, 1, 0),
                armor("cloth_2", "Cloth", Material.LEATHER_LEGGINGS, Rarity.COMMON, 1, 0),
                armor("cloth_3", "Cloth", Material.LEATHER_BOOTS, Rarity.COMMON, 1, 0),
        };
    }

    public static ItemStack armor(String id, String name, Material mat, Rarity r, int defense, int health) {
        ItemStack s = new ItemStack(mat);
        s.editMeta(meta -> {
            meta.setDisplayName(r.legacy + name);
            meta.setLore(armorLore(r, defense, health));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (r.ordinal() >= Rarity.RARE.ordinal()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            }
            var pdc = meta.getPersistentDataContainer();
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.GEAR),
                    org.bukkit.persistence.PersistentDataType.STRING, "true");
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.KIND),
                    org.bukkit.persistence.PersistentDataType.STRING, "armor");
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.BASE),
                    org.bukkit.persistence.PersistentDataType.STRING, id);
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.RARITY),
                    org.bukkit.persistence.PersistentDataType.STRING, r.name());
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.DEFENSE),
                    org.bukkit.persistence.PersistentDataType.INTEGER, defense);
            if (health > 0) {
                pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.HEALTH),
                        org.bukkit.persistence.PersistentDataType.INTEGER, health);
            }
        });
        return s;
    }

    private static List<String> weaponLore(Rarity r, int dmg, int health, String ability, int abilityCost) {
        List<String> l = new ArrayList<>();
        l.add("§7Damage: §c" + dmg);
        if (health > 0) l.add("§7Health: §a+" + health);
        if (ability != null && !ability.isEmpty()) {
            l.add("§7Ability: §6" + ability + " §8(§b" + abilityCost + " mana§8)");
            l.add("§8How: §7Sneak + Right-Click");
            String how = usage(ability);
            if (how != null) l.add("§8     " + how);
        }
        l.add("");
        l.add(r.legacy + r.name());
        return l;
    }

    private static String usage(String ability) {
        switch (ability) {
            case "Rush": return "dash forward to dodge";
            case "Slash": return "a quick, heavy strike ahead";
            case "Cleave": return "slash everything in a cone ahead";
            case "Smash": return "blast all nearby enemies";
            case "Blade Storm": return "spin, damaging around you";
            case "Arcane Bolt": return "mage strike in a line";
            case "Ravage": return "devastate every enemy in the room";
            default: return "trigger a burst of damage";
        }
    }

    private static List<String> armorLore(Rarity r, int defense, int health) {
        List<String> l = new ArrayList<>();
        l.add("§7Defense: §a" + defense);
        if (health > 0) l.add("§7Health: §a+" + health);
        l.add("");
        l.add(r.legacy + r.name());
        return l;
    }
}