package com.lieyabull.dung.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds rarity-colored ItemStacks with SkyBlock-style lore (Stats / Ability lines).
 * Every crafted item is tagged so listeners can identify Dung gear vs normal loot.
 */
public final class GearFactory {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

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

    /** Add a magic damage tag to a weapon. Magic weapons use this for ability damage
     *  instead of the melee damage value, and deal only 1 melee damage on basic swings. */
    public static ItemStack withMagicDamage(ItemStack s, int magicDmg) {
        s.editMeta(meta -> {
            var pdc = meta.getPersistentDataContainer();
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.MAGIC_DAMAGE),
                    org.bukkit.persistence.PersistentDataType.INTEGER, magicDmg);
            // Override the melee damage to 1 so magic weapons do negligible basic attack damage
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.DAMAGE),
                    org.bukkit.persistence.PersistentDataType.INTEGER, 1);
            // Update lore to show magic damage instead of melee damage
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).startsWith("§7Damage: §c")) {
                    lore.set(i, "§7Magic Damage: §d" + magicDmg);
                    break;
                }
            }
            meta.setLore(lore);
        });
        return s;
    }

    /** Flag gear bought with persistent currency: it survives death (run gear is stripped).
     *  Also assigns a unique UUID so the item can be tracked across inventory snapshots.
     *  Prepends a star emoji (★) to the display name to visually mark persistent items. */
    public static ItemStack markPersistent(ItemStack s) {
        s.editMeta(meta -> {
            var pdc = meta.getPersistentDataContainer();
            pdc.set(
                    org.bukkit.NamespacedKey.minecraft(ItemTags.PERSISTENT),
                    org.bukkit.persistence.PersistentDataType.STRING, "true");
            // Assign a UUID if one doesn't already exist (migration preserves existing UUIDs)
            var uuidKey = org.bukkit.NamespacedKey.minecraft(ItemTags.UUID);
            if (!pdc.has(uuidKey, org.bukkit.persistence.PersistentDataType.STRING)) {
                pdc.set(uuidKey,
                        org.bukkit.persistence.PersistentDataType.STRING,
                        UUID.randomUUID().toString());
            }
            // Prepend a star emoji to the display name to visually mark persistent items
            String name = meta.getDisplayName();
            if (name != null && !name.isEmpty() && !name.startsWith("★")) {
                meta.setDisplayName("★ " + name);
            }
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

    /** True if the item is persistent (survives death, bought with persistent currency). */
    public static boolean isPersistent(ItemStack s) {
        if (s == null || s.getItemMeta() == null) return false;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        return pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.PERSISTENT),
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    /** True if the item is a mana shield. */
    public static boolean isShield(ItemStack s) {
        if (s == null || s.getItemMeta() == null) return false;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        return pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.MANA_SHIELD),
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    /** Get the max shield capacity from a mana shield item. Returns 0 if not a shield. */
    public static int getShieldMax(ItemStack s) {
        if (s == null || s.getItemMeta() == null) return 0;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        var key = org.bukkit.NamespacedKey.minecraft(ItemTags.SHIELD_MAX);
        if (!pdc.has(key, org.bukkit.persistence.PersistentDataType.INTEGER)) return 0;
        return pdc.get(key, org.bukkit.persistence.PersistentDataType.INTEGER);
    }

    /** Get the stored health from a Life Drain weapon. Returns 0 if not set. */
    public static int getStoredHealth(ItemStack s) {
        if (s == null || s.getItemMeta() == null) return 0;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        var key = org.bukkit.NamespacedKey.minecraft(ItemTags.STORED_HEALTH);
        if (!pdc.has(key, org.bukkit.persistence.PersistentDataType.INTEGER)) return 0;
        return pdc.get(key, org.bukkit.persistence.PersistentDataType.INTEGER);
    }

    /** Max stored health for a Life Drain weapon, scaling with rarity (COMMON=25 → MYTHIC=180). */
    public static int storedHealthMax(Rarity r) {
        if (r == null) return 50;
        return switch (r) {
            case COMMON -> 25;
            case UNCOMMON -> 40;
            case RARE -> 60;
            case EPIC -> 90;
            case LEGENDARY -> 130;
            case MYTHIC -> 180;
        };
    }

    /** Max stored health for a given Life Drain weapon item (from its rarity), or 50 if unknown. */
    public static int getStoredHealthMax(ItemStack s) {
        return storedHealthMax(getRarity(s));
    }

    /** Get the item's rarity enum, or null if it has no rarity tag. */
    public static Rarity getRarity(ItemStack s) {
        if (s == null || s.getItemMeta() == null) return null;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        String v = pdc.get(org.bukkit.NamespacedKey.minecraft(ItemTags.RARITY),
                org.bukkit.persistence.PersistentDataType.STRING);
        if (v == null) return null;
        try {
            return Rarity.valueOf(v);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Set the stored health on a Life Drain weapon, capped by the item's rarity. */
    public static void setStoredHealth(ItemStack s, int amount) {
        int max = getStoredHealthMax(s);
        int capped = Math.min(max, Math.max(0, amount));
        s.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.minecraft(ItemTags.STORED_HEALTH),
                    org.bukkit.persistence.PersistentDataType.INTEGER, capped);
            // Update lore to show stored health
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            // Remove old stored health line if present
            lore.removeIf(line -> line.startsWith("§7Stored:"));
            // Find the ability line index and insert after it
            int insertAt = -1;
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).contains("§6Life Drain")) {
                    insertAt = i + 1;
                    break;
                }
            }
            if (capped > 0) {
                String shLine = "§7Stored: §c" + capped + "§7/§f" + max + "§7❤";
                if (insertAt >= 0 && insertAt < lore.size()) {
                    lore.add(insertAt, shLine);
                } else {
                    lore.add(1, shLine);
                }
            }
            meta.setLore(lore);
        });
    }

    /** Get the UUID of a persistent item, or null if it doesn't have one. */
    public static String getUuid(ItemStack s) {
        if (s == null || s.getItemMeta() == null) return null;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        var key = org.bukkit.NamespacedKey.minecraft(ItemTags.UUID);
        if (!pdc.has(key, org.bukkit.persistence.PersistentDataType.STRING)) return null;
        return pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING);
    }

    /** Assign a UUID to a persistent item if it doesn't already have one.
     *  Used for migration of pre-UUID persistent items. */
    public static void assignUuidIfMissing(ItemStack s) {
        if (s == null || s.getItemMeta() == null) return;
        s.editMeta(meta -> {
            var pdc = meta.getPersistentDataContainer();
            var key = org.bukkit.NamespacedKey.minecraft(ItemTags.UUID);
            if (!pdc.has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
                pdc.set(key,
                        org.bukkit.persistence.PersistentDataType.STRING,
                        UUID.randomUUID().toString());
            }
        });
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

    // ==================== Durability helpers ====================

    /** Get the current durability from an item's PDC. Returns -1 if not set. */
    public static int getDurability(ItemStack s) {
        if (s == null || s.getItemMeta() == null) return -1;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        var key = org.bukkit.NamespacedKey.minecraft(ItemTags.DURABILITY);
        if (!pdc.has(key, org.bukkit.persistence.PersistentDataType.INTEGER)) return -1;
        return pdc.get(key, org.bukkit.persistence.PersistentDataType.INTEGER);
    }

    /** Set the current durability on an item's PDC. */
    public static void setDurability(ItemStack s, int durability) {
        s.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.minecraft(ItemTags.DURABILITY),
                    org.bukkit.persistence.PersistentDataType.INTEGER, durability);
        });
    }

    /** Get the max durability from an item's PDC. Returns -1 if not set. */
    public static int getMaxDurability(ItemStack s) {
        if (s == null || s.getItemMeta() == null) return -1;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        var key = org.bukkit.NamespacedKey.minecraft(ItemTags.MAX_DURABILITY);
        if (!pdc.has(key, org.bukkit.persistence.PersistentDataType.INTEGER)) return -1;
        return pdc.get(key, org.bukkit.persistence.PersistentDataType.INTEGER);
    }

    /** Set the max durability on an item's PDC. */
    public static void setMaxDurability(ItemStack s, int maxDurability) {
        s.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.minecraft(ItemTags.MAX_DURABILITY),
                    org.bukkit.persistence.PersistentDataType.INTEGER, maxDurability);
        });
    }

    /** Get the repair count from an item's PDC. Returns 0 if not set. */
    public static int getRepairCount(ItemStack s) {
        if (s == null || s.getItemMeta() == null) return 0;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        var key = org.bukkit.NamespacedKey.minecraft(ItemTags.REPAIR_COUNT);
        if (!pdc.has(key, org.bukkit.persistence.PersistentDataType.INTEGER)) return 0;
        return pdc.get(key, org.bukkit.persistence.PersistentDataType.INTEGER);
    }

    /** Set the repair count on an item's PDC. */
    public static void setRepairCount(ItemStack s, int count) {
        s.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.minecraft(ItemTags.REPAIR_COUNT),
                    org.bukkit.persistence.PersistentDataType.INTEGER, count);
        });
    }

    /** Reduce durability by the given amount. Returns true if the item is now broken (durability <= 0). */
    public static boolean damageItem(ItemStack s, int amount) {
        int dur = getDurability(s);
        if (dur < 0) return false; // not a durability item
        dur = Math.max(0, dur - amount);
        setDurability(s, dur);
        addDurabilityLore(s);
        return dur <= 0;
    }

    /** Restore durability by the given amount (capped at max). */
    public static void repairItem(ItemStack s, int amount) {
        int dur = getDurability(s);
        int max = getMaxDurability(s);
        if (dur < 0 || max < 0) return;
        dur = Math.min(max, dur + amount);
        setDurability(s, dur);
        addDurabilityLore(s);
    }

    /** Add or update the durability lore line on an item. */
    public static void addDurabilityLore(ItemStack s) {
        int dur = getDurability(s);
        int max = getMaxDurability(s);
        if (dur < 0 || max < 0) return;
        s.editMeta(meta -> {
            List<Component> lore = meta.lore();
            if (lore == null) lore = new ArrayList<>();
            // Build the durability bar
            double pct = (double) dur / max;
            String color;
            if (pct >= 0.67) color = "§a";       // Green
            else if (pct >= 0.34) color = "§e";   // Yellow
            else color = "§c";                     // Red
            int filled = (int) Math.round(pct * 10);
            int empty = 10 - filled;
            StringBuilder bar = new StringBuilder("§7Durability: ").append(color);
            bar.append("█".repeat(Math.max(0, filled)));
            bar.append("§8░".repeat(Math.max(0, empty)));
            bar.append(" §7").append(dur).append("/").append(max);
            String durLine = bar.toString();
            // Find and replace existing durability line, or add before the rarity line
            int durIdx = -1;
            int rarityIdx = -1;
            for (int i = 0; i < lore.size(); i++) {
                String text = LegacyComponentSerializer.legacySection().serialize(lore.get(i));
                if (text.startsWith("§7Durability:")) durIdx = i;
                if (text.startsWith("§") && !text.startsWith("§7") && !text.startsWith("§8")) {
                    // This is likely the rarity line (colored rarity name)
                    rarityIdx = i;
                }
            }
            Component durComponent = LegacyComponentSerializer.legacySection().deserialize(durLine);
            if (durIdx >= 0) {
                lore.set(durIdx, durComponent);
            } else if (rarityIdx >= 0) {
                lore.add(rarityIdx, durComponent);
            } else {
                lore.add(durComponent);
            }
            meta.lore(lore);
        });
    }

    /** Initialise durability on a persistent item (weapon=100, armor=30, shield=50). */
    public static void initDurability(ItemStack s) {
        if (s == null || s.getItemMeta() == null) return;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        boolean isPersistent = pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.PERSISTENT),
                org.bukkit.persistence.PersistentDataType.STRING);
        if (!isPersistent) return;
        String kind = pdc.get(org.bukkit.NamespacedKey.minecraft(ItemTags.KIND),
                org.bukkit.persistence.PersistentDataType.STRING);
        int maxDur = "weapon".equals(kind) ? 100 : "shield".equals(kind) ? 50 : 30;
        setMaxDurability(s, maxDur);
        setDurability(s, maxDur);
        addDurabilityLore(s);
    }

    /** Turn a run item into a persistent copy, delivered at half durability. Used by the
     *  Persist Master: a successful attempt queues the item for delivery after the run ends. */
    public static ItemStack persistize(ItemStack s) {
        ItemStack out = markPersistent(s.clone());
        initDurability(out);
        int max = getMaxDurability(out);
        if (max > 0) setDurability(out, Math.max(1, max / 2));
        addDurabilityLore(out);
        return out;
    }

    /** Downgrade a run item one rarity tier (scaling its stats down proportionally and updating
     *  the display color, rarity line, and durability-bar lore). Returns a copy; COMMON or items
     *  without a rarity are returned unchanged. Used when a Persist attempt fails. */
    public static ItemStack downgradeRarity(ItemStack s) {
        Rarity cur = getRarity(s);
        if (cur == null || cur == Rarity.COMMON) return s.clone();
        Rarity target = Rarity.values()[cur.ordinal() - 1];
        double scale = target.statMult / cur.statMult;
        ItemStack out = s.clone();
        out.editMeta(meta -> {
            var pdc = meta.getPersistentDataContainer();
            scaleIntTag(pdc, ItemTags.DAMAGE, scale);
            scaleIntTag(pdc, ItemTags.MAGIC_DAMAGE, scale);
            scaleIntTag(pdc, ItemTags.HEALTH, scale);
            scaleIntTag(pdc, ItemTags.DEFENSE, scale);
            scaleIntTag(pdc, ItemTags.SHIELD_MAX, scale);
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.RARITY),
                    org.bukkit.persistence.PersistentDataType.STRING, target.name());
            // Recolor the display name (keep any ★ persistent prefix and perfection star/suffix)
            String name = meta.getDisplayName();
            if (name != null) {
                boolean star = name.startsWith("★ ");
                String body = star ? name.substring(2) : name;
                boolean hasPerfection = body.contains("§e✦") && body.endsWith(" of Perfection");
                // Strip perfection parts before recolor
                String cleanBody = body;
                if (hasPerfection) {
                    if (cleanBody.endsWith(" of Perfection"))
                        cleanBody = cleanBody.substring(0, cleanBody.length() - " of Perfection".length());
                    if (cleanBody.startsWith("§e✦ "))
                        cleanBody = cleanBody.substring(4);
                }
                if (cleanBody.startsWith(cur.legacy)) {
                    cleanBody = target.legacy + cleanBody.substring(cur.legacy.length());
                }
                // Re-add perfection parts
                if (hasPerfection) {
                    cleanBody = "§e✦ " + cleanBody + " of Perfection";
                }
                meta.setDisplayName((star ? "★ " : "") + cleanBody);
            }
            // Rescale stat lore + rewrite the rarity line
            if (meta.hasLore()) {
                List<Component> old = meta.lore();
                List<Component> next = new ArrayList<>();
                for (Component c : old) {
                    String text = LegacyComponentSerializer.legacySection().serialize(c);
                    next.add(LegacyComponentSerializer.legacySection().deserialize(rescaleLoreLine(text, cur, target, scale)));
                }
                meta.lore(next);
            }
        });
        // Re-cap stored health (Life Drain) to the new rarity's cap
        if (getStoredHealthMax(out) > 0) setStoredHealth(out, getStoredHealth(out));
        return out;
    }

    private static void scaleIntTag(org.bukkit.persistence.PersistentDataContainer pdc, String tag, double scale) {
        var key = org.bukkit.NamespacedKey.minecraft(tag);
        if (!pdc.has(key, org.bukkit.persistence.PersistentDataType.INTEGER)) return;
        int v = pdc.get(key, org.bukkit.persistence.PersistentDataType.INTEGER);
        int nv = v <= 0 ? v : Math.max(1, (int) Math.round(v * scale));
        pdc.set(key, org.bukkit.persistence.PersistentDataType.INTEGER, nv);
    }

    private static String rescaleLoreLine(String line, Rarity cur, Rarity target, double scale) {
        int v;
        if (line.startsWith("§7Damage: §c")) {
            v = parseIntAfter(line.substring("§7Damage: §c".length()));
            return "§7Damage: §c" + scaleValue(v, scale);
        }
        if (line.startsWith("§7Magic Damage: §d")) {
            v = parseIntAfter(line.substring("§7Magic Damage: §d".length()));
            return "§7Magic Damage: §d" + scaleValue(v, scale);
        }
        if (line.startsWith("§7Health: §a+")) {
            v = parseIntAfter(line.substring("§7Health: §a+".length()));
            return "§7Health: §a+" + scaleValue(v, scale);
        }
        if (line.startsWith("§7Defense: §a")) {
            v = parseIntAfter(line.substring("§7Defense: §a".length()));
            return "§7Defense: §a" + scaleValue(v, scale);
        }
        if (line.startsWith("§7Shield Capacity: §b")) {
            v = parseIntAfter(line.substring("§7Shield Capacity: §b".length()));
            return "§7Shield Capacity: §b" + scaleValue(v, scale);
        }
        if (line.equals(cur.legacy + cur.name())) {
            return target.legacy + target.name();
        }
        return line;
    }

    private static int scaleValue(int v, double scale) {
        return v <= 0 ? v : Math.max(1, (int) Math.round(v * scale));
    }

    private static int parseIntAfter(String s) {
        StringBuilder num = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) num.append(c);
            else if (num.length() > 0) break;
        }
        try {
            return num.length() == 0 ? 0 : Integer.parseInt(num.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ==================== Affix + Upgrade helpers ====================

    /** Read the affix list from an item's PDC (stored as a {@code ;}-joined {@code "id:value"} string). */
    public static List<Affix.AffixRoll> getAffixes(ItemStack s) {
        List<Affix.AffixRoll> out = new ArrayList<>();
        if (s == null || s.getType() == Material.AIR || s.getItemMeta() == null) return out;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        String raw = pdc.get(org.bukkit.NamespacedKey.minecraft(ItemTags.AFFIXES),
                org.bukkit.persistence.PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return out;
        for (String entry : raw.split(";")) {
            if (entry.isEmpty()) continue;
            int i = entry.indexOf(':');
            String id = i < 0 ? entry : entry.substring(0, i);
            int value = 0;
            try {
                value = Integer.parseInt(i < 0 ? "" : entry.substring(i + 1));
            } catch (NumberFormatException ignored) {
            }
            Affix affix = byId(id);
            if (affix != null) out.add(new Affix.AffixRoll(affix, value));
        }
        return out;
    }

    private static String joinAffixes(List<Affix.AffixRoll> affixes) {
        StringBuilder sb = new StringBuilder();
        for (Affix.AffixRoll roll : affixes) {
            if (sb.length() > 0) sb.append(';');
            sb.append(Affix.serialize(roll));
        }
        return sb.toString();
    }

    /** Write a fresh affix set onto the item and update its lore. */
    public static void applyAffixes(ItemStack s, List<Affix.AffixRoll> affixes) {
        reforge(s, affixes, getUpgradeLevel(s));
    }

    /** Current upgrade level of an item (0 if none). */
    public static int getUpgradeLevel(ItemStack s) {
        if (s == null || s.getItemMeta() == null) return 0;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        Integer v = pdc.get(org.bukkit.NamespacedKey.minecraft(ItemTags.UPGRADE_LEVEL),
                org.bukkit.persistence.PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    /** Number of times an item has been reforged (0 if never). Used to make each reforge costlier. */
    public static int getReforgeCount(ItemStack s) {
        if (s == null || s.getItemMeta() == null) return 0;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        Integer v = pdc.get(org.bukkit.NamespacedKey.minecraft(ItemTags.REFORGE_COUNT),
                org.bukkit.persistence.PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    /** Set the item's reforge count. */
    public static void setReforgeCount(ItemStack s, int count) {
        if (s == null || s.getType() == Material.AIR) return;
        s.editMeta(meta -> meta.getPersistentDataContainer().set(
                org.bukkit.NamespacedKey.minecraft(ItemTags.REFORGE_COUNT),
                org.bukkit.persistence.PersistentDataType.INTEGER, count));
    }

    /** The item's core stat tag (the one UPGRADE boosts): MAGIC_DAMAGE/DAMAGE for weapons, DEFENSE for
     *  armor, SHIELD_MAX for shields. Returns the tag name, or null if the item has no core stat. */
    public static String coreStatTag(ItemStack s) {
        String kind = kindOf(s);
        if ("armor".equals(kind)) return ItemTags.DEFENSE;
        if ("shield".equals(kind)) return ItemTags.SHIELD_MAX;
        if ("weapon".equals(kind)) {
            return intTagOf(s, ItemTags.MAGIC_DAMAGE) > 0 ? ItemTags.MAGIC_DAMAGE : ItemTags.DAMAGE;
        }
        return null;
    }

    /**
     * Raise an item to the given upgrade level, boosting its core stat by the workstation rule's
     * per-level multiplier and rewriting lore. The stat boost is folded into the stored stat tag so
     * recomputeStats and the lore always agree (no separate multiplier to keep in sync).
     */
    public static void setUpgradeLevel(ItemStack s, int newLevel) {
        int oldLevel = getUpgradeLevel(s);
        String core = coreStatTag(s);
        if (core != null && newLevel > oldLevel) {
            double ratio = com.lieyabull.dung.game.WorkstationRules.upgradeStatMult(newLevel)
                    / com.lieyabull.dung.game.WorkstationRules.upgradeStatMult(oldLevel);
            int current = intTagOf(s, core);
            int boosted = (int) Math.round(current * ratio);
            s.editMeta(meta -> meta.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.minecraft(core),
                    org.bukkit.persistence.PersistentDataType.INTEGER, Math.max(1, boosted)));
        }
        s.editMeta(meta -> meta.getPersistentDataContainer().set(
                org.bukkit.NamespacedKey.minecraft(ItemTags.UPGRADE_LEVEL),
                org.bukkit.persistence.PersistentDataType.INTEGER, newLevel));
        rebuildLoreWithAffixesAndUpgrade(s);
    }

    /** Rewrite an item's lore to reflect its current affixes and upgrade level. Re-derives stat lines
     *  from PDC so the shown numbers stay in sync with what recomputeStats actually applies. */
    private static void rebuildLoreWithAffixesAndUpgrade(ItemStack s) {
        if (s == null || s.getType() == Material.AIR || s.getItemMeta() == null) return;
        String kind = kindOf(s);
        int dmg = intTagOf(s, ItemTags.DAMAGE);
        int magic = intTagOf(s, ItemTags.MAGIC_DAMAGE);
        int def = intTagOf(s, ItemTags.DEFENSE);
        int health = intTagOf(s, ItemTags.HEALTH);
        int shieldMax = intTagOf(s, ItemTags.SHIELD_MAX);
        int upLevel = getUpgradeLevel(s);
        boolean magicWeapon = magic > 0 && "weapon".equals(kind);
        List<Affix.AffixRoll> affixes = getAffixes(s);

        List<Component> lore = new ArrayList<>();
        if (magicWeapon) {
            lore.add(LEGACY.deserialize("§7Magic Damage: §d" + magic));
        } else if (dmg > 0) {
            lore.add(LEGACY.deserialize("§7Damage: §c" + dmg));
        }
        if (def > 0) lore.add(LEGACY.deserialize("§7Defense: §a" + def));
        if (health > 0) lore.add(LEGACY.deserialize("§7Health: §a+" + health));
        if (shieldMax > 0) lore.add(LEGACY.deserialize("§7Shield Capacity: §b" + shieldMax));
        if (magic > 0 && !magicWeapon) lore.add(LEGACY.deserialize("§7Magic Damage: §d" + magic));
        for (Affix.AffixRoll roll : affixes) {
            lore.add(LEGACY.deserialize("§8" + roll.affix().label + " " + roll.affix().stat.color + "+" + roll.value()));
        }
        if (upLevel > 0) lore.add(LEGACY.deserialize("§5✦ §5Upgrade §d" + upLevel));
        // ability line + rarity line if the original had them
        String ability = strTagOf(s, ItemTags.ABILITY);
        if (ability != null && !ability.isEmpty()) {
            Integer cost = intTagOf(s, ItemTags.COST);
            lore.add(LEGACY.deserialize("§7Ability: §6" + ability + " §8(§b" + (cost == null ? 0 : cost) + " mana§8)"));
            lore.add(LEGACY.deserialize("§8How: §7Sneak + Right-Click"));
            String how = usage(ability);
            if (how != null) lore.add(LEGACY.deserialize("§8     " + how));
        }
        Rarity r = getRarity(s);
        if (r != null) {
            lore.add(LEGACY.deserialize(""));
            lore.add(LEGACY.deserialize(r.legacy + r.name()));
        }
        s.editMeta(meta -> meta.lore(lore));
        applyPerfectionName(s, upLevel);
    }

    /** Apply the given affix set and current upgrade level to an item, rewriting lore. */
    public static void reforge(ItemStack s, java.util.List<Affix.AffixRoll> newAffixes, int newUpgradeLevel) {
        String joined = joinAffixes(newAffixes);
        s.editMeta(meta -> {
            var pdc = meta.getPersistentDataContainer();
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.AFFIXES),
                    org.bukkit.persistence.PersistentDataType.STRING, joined);
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.UPGRADE_LEVEL),
                    org.bukkit.persistence.PersistentDataType.INTEGER, newUpgradeLevel);
        });
        rebuildLoreWithAffixesAndUpgrade(s);
    }

    /**
     * If the item is at max upgrade level, prepend a yellow star (after any persist star)
     * and append " of Perfection" to the display name. If below max, strip any existing
     * perfection star/suffix so the name stays clean after a downgrade or reforge.
     */
    private static void applyPerfectionName(ItemStack s, int upLevel) {
        if (s == null || s.getType() == Material.AIR || s.getItemMeta() == null) return;
        s.editMeta(meta -> {
            String name = meta.getDisplayName();
            if (name == null || name.isEmpty()) return;
            boolean isMaxed = upLevel >= com.lieyabull.dung.game.WorkstationRules.UPGRADE_MAX;
            // Check if the name already has the perfection star and suffix
            boolean hasPerfection = name.contains("§e✦") && name.endsWith(" of Perfection");

            if (isMaxed && !hasPerfection) {
                // Insert yellow star after any persist star (★)
                if (name.startsWith("★ ")) {
                    meta.setDisplayName("★ §e✦ " + name.substring(2) + " of Perfection");
                } else {
                    meta.setDisplayName("§e✦ " + name + " of Perfection");
                }
            } else if (!isMaxed && hasPerfection) {
                // Strip perfection star and suffix
                String cleaned = name;
                // Remove " of Perfection" suffix
                if (cleaned.endsWith(" of Perfection")) {
                    cleaned = cleaned.substring(0, cleaned.length() - " of Perfection".length());
                }
                // Remove the yellow star (with or without persist star prefix)
                if (cleaned.startsWith("★ §e✦ ")) {
                    cleaned = "★ " + cleaned.substring(5);
                } else if (cleaned.startsWith("§e✦ ")) {
                    cleaned = cleaned.substring(4);
                }
                meta.setDisplayName(cleaned);
            }
        });
    }

    private static Affix byId(String id) {
        for (Affix a : Affix.values()) if (a.id.equals(id)) return a;
        return null;
    }

    private static String kindOf(ItemStack s) {
        return strTagOf(s, ItemTags.KIND);
    }

    private static int intTagOf(ItemStack s, String tag) {
        if (s == null || s.getItemMeta() == null) return 0;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        Integer v = pdc.get(org.bukkit.NamespacedKey.minecraft(tag),
                org.bukkit.persistence.PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    private static String strTagOf(ItemStack s, String tag) {
        if (s == null || s.getItemMeta() == null) return null;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        return pdc.get(org.bukkit.NamespacedKey.minecraft(tag),
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    /** First-run kit: a Frayed Blade + a full Cloth set, so a new player can fight immediately.
     *  Order: [0]=weapon, [1..4]=helmet, chestplate, leggings, boots. Run gear (not persistent),
     *  marked STARTER so it is never salvageable. */
    public static ItemStack[] starter() {
        return new ItemStack[]{
                markStarter(weapon("frayed_blade", "Frayed Blade", Material.IRON_SWORD, Rarity.COMMON, 5, 0, "Rush", 15)),
                markStarter(armor("cloth_0", "Cloth", Material.LEATHER_HELMET, Rarity.COMMON, 1, 0)),
                markStarter(armor("cloth_1", "Cloth", Material.LEATHER_CHESTPLATE, Rarity.COMMON, 1, 0)),
                markStarter(armor("cloth_2", "Cloth", Material.LEATHER_LEGGINGS, Rarity.COMMON, 1, 0)),
                markStarter(armor("cloth_3", "Cloth", Material.LEATHER_BOOTS, Rarity.COMMON, 1, 0)),
        };
    }

    /** Flag gear as part of the free starter kit (skipped by salvage). */
    public static ItemStack markStarter(ItemStack s) {
        s.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.minecraft(ItemTags.STARTER),
                    org.bukkit.persistence.PersistentDataType.STRING, "true");
        });
        return s;
    }

    /** True if the item is free starter-kit gear (never salvageable). */
    public static boolean isStarter(ItemStack s) {
        if (s == null || s.getItemMeta() == null) return false;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        return pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.STARTER),
                org.bukkit.persistence.PersistentDataType.STRING);
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
            // Apply armor trim matching the rarity color and theme
            if (meta instanceof ArmorMeta armorMeta) {
                TrimMaterial trimMat = switch (r) {
                    case COMMON -> TrimMaterial.QUARTZ;
                    case UNCOMMON -> TrimMaterial.EMERALD;
                    case RARE -> TrimMaterial.DIAMOND;
                    case EPIC -> TrimMaterial.AMETHYST;
                    case LEGENDARY -> TrimMaterial.GOLD;
                    case MYTHIC -> TrimMaterial.REDSTONE;
                };
                TrimPattern trimPattern = switch (r) {
                    case COMMON -> TrimPattern.SENTRY;
                    case UNCOMMON -> TrimPattern.DUNE;
                    case RARE -> TrimPattern.COAST;
                    case EPIC, LEGENDARY, MYTHIC -> TrimPattern.EYE;
                };
                armorMeta.setTrim(new ArmorTrim(trimMat, trimPattern));
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

    /** Build a Mana Shield item. Shields are held in the main hand and replace the weapon slot.
     *  They do NOT count as weapons for stat computation (dung.kind = "shield").
     *  Shield capacity scales with rarity: COMMON=30, UNCOMMON=45, RARE=60, EPIC=80, LEGENDARY=100, MYTHIC=130. */
    public static ItemStack shield(Rarity r) {
        ItemStack s = new ItemStack(Material.SHIELD);
        int shieldMax = switch (r) {
            case COMMON -> 30;
            case UNCOMMON -> 45;
            case RARE -> 60;
            case EPIC -> 80;
            case LEGENDARY -> 100;
            case MYTHIC -> 130;
        };
        s.editMeta(meta -> {
            meta.setDisplayName(r.legacy + "Mana Shield");
            List<String> lore = new ArrayList<>();
            lore.add("§7Shield Capacity: §b" + shieldMax);
            lore.add("§7Sneak to charge shield with mana");
            lore.add("§7Absorbs damage while active");
            lore.add("");
            lore.add(r.legacy + r.name());
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            // Repurpose the shield's native durability bar to show the current charge (empty to start)
            if (meta instanceof org.bukkit.inventory.meta.Damageable dmg) {
                dmg.setDamage(Material.SHIELD.getMaxDurability());
            }
            if (r.ordinal() >= Rarity.RARE.ordinal()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            }
            // Apply banner pattern: base color brown, pattern color matches rarity (black for COMMON)
            if (meta instanceof BlockStateMeta blockMeta) {
                org.bukkit.block.Banner banner = (org.bukkit.block.Banner) blockMeta.getBlockState();
                banner.setBaseColor(org.bukkit.DyeColor.BROWN);
                org.bukkit.DyeColor patternColor = switch (r) {
                    case COMMON -> org.bukkit.DyeColor.BLACK;
                    case UNCOMMON -> org.bukkit.DyeColor.GREEN;
                    case RARE -> org.bukkit.DyeColor.LIGHT_BLUE;
                    case EPIC -> org.bukkit.DyeColor.PURPLE;
                    case LEGENDARY -> org.bukkit.DyeColor.ORANGE;
                    case MYTHIC -> org.bukkit.DyeColor.RED;
                };
                banner.addPattern(new Pattern(patternColor, PatternType.FLOW));
                banner.addPattern(new Pattern(patternColor, PatternType.GRADIENT_UP));
                banner.addPattern(new Pattern(patternColor, PatternType.GRADIENT));
                blockMeta.setBlockState(banner);
            }
            var pdc = meta.getPersistentDataContainer();
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.GEAR),
                    org.bukkit.persistence.PersistentDataType.STRING, "true");
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.KIND),
                    org.bukkit.persistence.PersistentDataType.STRING, "shield");
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.BASE),
                    org.bukkit.persistence.PersistentDataType.STRING, "mana_shield");
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.RARITY),
                    org.bukkit.persistence.PersistentDataType.STRING, r.name());
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.MANA_SHIELD),
                    org.bukkit.persistence.PersistentDataType.STRING, "true");
            pdc.set(org.bukkit.NamespacedKey.minecraft(ItemTags.SHIELD_MAX),
                    org.bukkit.persistence.PersistentDataType.INTEGER, shieldMax);
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
            case "Chain Lightning": return "strike a target, chaining to nearby enemies";
            case "Fireball": return "launch an explosive fireball";
            case "Life Drain": return "drain life from enemies, right-click ally to heal";
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