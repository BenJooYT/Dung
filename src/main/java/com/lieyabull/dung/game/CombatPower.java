package com.lieyabull.dung.game;

import com.lieyabull.dung.items.Affix;
import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.items.ItemTags;
import com.lieyabull.dung.items.Rarity;
import com.lieyabull.dung.meta.Upgrades;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Combat Power (CP): a mathematically calculated measurement of a player's raw combat strength,
 * derived from actual item/upgrade stats rather than arbitrary per-item values.
 *
 * <p>Locked-in spec (notes/combat_power_and_difficulty_design_notes-1.txt, sections 14-15):
 * <ul>
 *   <li>Weights: damage 4.0, magic damage 4.0, defense 2.0, health 0.5, shield capacity 1.0,
 *       reach 5.0, affix value 1.0/point, crit 3.0 per 1% folded into each item from its rarity
 *       (Option A). Attack speed is not an item stat and carries no CP. Upgrade level is not a
 *       separate weight (the +10%/level boost is already folded into the stat tag).</li>
 *   <li>Player total = best usable weapon + all armor + equipped shield + other gear + permanent
 *       upgrade tracks + shop tonics. Special effects and support buffs contribute nothing.</li>
 *   <li>Difficulty modifier = clamp((weightedPartyCP / referenceCP(startFloor) - 1) * 1.0,
 *       +-0.10 early / +-0.20 late), computed once at run start and locked; the raw mismatch
 *       then feeds three independently capped shares: tight encounter complexity (elite chance
 *       hard-capped at 25%/room), middle enemy damage, wide enemy health.</li>
 * </ul>
 *
 * <p>All numeric methods that take only primitives/Rarity are pure and unit-testable without a
 * server; the {@code ItemStack}/{@code PlayerInventory} wrappers just extract PDC tags.
 */
public final class CombatPower {
    private CombatPower() {}

    // ---------- Stat -> CP weights (section 14, locked baseline) ----------

    /** CP per point of melee damage (DAMAGE tag). */
    public static final double W_DAMAGE = 4.0;
    /** CP per point of magic damage (MAGIC_DAMAGE tag). */
    public static final double W_MAGIC = 4.0;
    /** CP per point of defense (DEFENSE tag). */
    public static final double W_DEFENSE = 2.0;
    /** CP per point of bonus health (HEALTH tag). */
    public static final double W_HEALTH = 0.5;
    /** CP per point of shield capacity (SHIELD_MAX tag). */
    public static final double W_SHIELD = 1.0;
    /** CP per block of melee reach (REACH tag). */
    public static final double W_REACH = 5.0;
    /** CP per rolled affix value point (AFFIXES tag, stored separately from base stats). */
    public static final double W_AFFIX = 1.0;
    /** CP per 1% of crit chance (locked in). */
    public static final double W_CRIT_PER_PCT = 3.0;

    // ---------- Difficulty modifier constants (section 15, locked defaults) ----------

    /** Reference CP of a correctly-progressed solo player at Floor 1. */
    public static final double REF_CP_START = 30.0;
    /** Expected CP growth per floor. referenceCP(floor) = REF_CP_START + (floor-1)*CP_PER_FLOOR. */
    public static final double CP_PER_FLOOR = 14.4;
    /** How fast the adjustment grows with a CP mismatch. */
    public static final double SENSITIVITY = 1.0;
    /** Cap for floors 1-5: CP can nudge difficulty at most +-10%. */
    public static final double CAP_EARLY = 0.10;
    /** Cap for floors 6+: at most +-20%. */
    public static final double CAP_LATE = 0.20;
    /** Floors at or below this use the early cap; above it use the late cap. */
    public static final int LATE_FLOOR = 5;
    /** Share of the modifier applied to encounter complexity (elite chance, composition). */
    public static final double COMPLEXITY_SHARE = 0.75;
    /** Hard ceiling on the complexity share: normal-room elite injection never passes 25%/room. */
    public static final double COMPLEXITY_CAP = 0.075;
    /** Damage-share cap for floors 1-5. */
    public static final double DMG_CAP_EARLY = 0.15;
    /** Damage-share cap for floors 6+. */
    public static final double DMG_CAP_LATE = 0.30;
    /** Health-share cap for floors 1-5. */
    public static final double HP_CAP_EARLY = 0.30;
    /** Health-share cap for floors 6+. */
    public static final double HP_CAP_LATE = 0.50;
    /** Party weighting, weakest-first: 100% / 90% / 80% / 64%, then 20% steps floored at 0. */
    public static final double[] PARTY_WEIGHTS = {1.0, 0.9, 0.8, 0.64};

    // ---------- Rarity-derived crit (mirrors PlayerState.recomputeStats) ----------

    /** Crit chance a weapon of this rarity grants (0.02 * ordinal). */
    public static double weaponCritChance(Rarity r) {
        return r == null ? 0.0 : 0.02 * r.ordinal();
    }

    /** Crit multiplier a weapon of this rarity grants (min(3.0, 1.5 + 0.1 * ordinal)). */
    public static double weaponCritMult(Rarity r) {
        return r == null ? 1.5 : Math.min(3.0, 1.5 + r.ordinal() * 0.1);
    }

    /** Crit chance an armor piece of this rarity grants (0.01 * ordinal). */
    public static double armorCritChance(Rarity r) {
        return r == null ? 0.0 : 0.01 * r.ordinal();
    }

    /**
     * Crit CP for a weapon: the rarity-derived crit raises average damage by the factor
     * {@code 1 + c*(m-1)}, so the bonus expected damage ({@code dmg*c*(m-1)}) is valued at the
     * normal damage weight. This keeps lore CP as "direct stats + its rarity crit" (Option A).
     */
    public static double weaponCritCp(double dmgBase, Rarity r) {
        if (dmgBase <= 0 || r == null) return 0.0;
        double c = weaponCritChance(r);
        double m = weaponCritMult(r);
        return dmgBase * c * (m - 1.0) * W_DAMAGE;
    }

    /** Crit CP for an armor piece or shield: its rarity crit-chance increment at 3.0 CP per 1%. */
    public static double armorCritCp(Rarity r) {
        if (r == null) return 0.0;
        return armorCritChance(r) * 100.0 * W_CRIT_PER_PCT;
    }

    // ---------- Per-item CP (pure core) ----------

    /**
     * Pure per-item CP from raw stat values. {@code kind} is the dung.kind tag
     * ({@code weapon}/{@code armor}/{@code shield}/other). Magic weapons (magic &gt; 0) value only
     * their magic damage — their melee tag is always 1 by design and adds nothing.
     */
    public static double itemCpCore(String kind, int dmg, int magic, int def, int health,
                                    int shieldMax, double reach, int affixTotal, Rarity rarity) {
        double cp;
        if ("weapon".equals(kind)) {
            if (magic > 0) {
                cp = magic * W_MAGIC + weaponCritCp(magic, rarity);
            } else {
                cp = dmg * W_DAMAGE + weaponCritCp(dmg, rarity);
            }
            cp += health * W_HEALTH + reach * W_REACH;
        } else if ("armor".equals(kind)) {
            cp = def * W_DEFENSE + health * W_HEALTH + armorCritCp(rarity);
        } else if ("shield".equals(kind)) {
            cp = shieldMax * W_SHIELD + armorCritCp(rarity);
        } else {
            cp = dmg * W_DAMAGE + magic * W_MAGIC + def * W_DEFENSE + health * W_HEALTH
                    + shieldMax * W_SHIELD + reach * W_REACH;
        }
        cp += affixTotal * W_AFFIX;
        return cp;
    }

    /** Per-item CP read off an item's PDC tags. Non-gear (or empty) stacks contribute 0. */
    public static double itemCp(ItemStack s) {
        if (s == null || s.getType() == Material.AIR || s.getItemMeta() == null) return 0.0;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.GEAR),
                org.bukkit.persistence.PersistentDataType.STRING)) {
            return 0.0;
        }
        String kind = strTag(pdc, ItemTags.KIND);
        int dmg = intTag(pdc, ItemTags.DAMAGE);
        int magic = intTag(pdc, ItemTags.MAGIC_DAMAGE);
        int def = intTag(pdc, ItemTags.DEFENSE);
        int health = intTag(pdc, ItemTags.HEALTH);
        int shieldMax = intTag(pdc, ItemTags.SHIELD_MAX);
        double reach = doubleTag(pdc, ItemTags.REACH);
        int affixTotal = 0;
        for (Affix.AffixRoll roll : GearFactory.getAffixes(s)) affixTotal += roll.value();
        Rarity rarity;
        try {
            String raw = strTag(pdc, ItemTags.RARITY);
            rarity = raw == null ? null : Rarity.valueOf(raw);
        } catch (IllegalArgumentException e) {
            rarity = null;
        }
        return itemCpCore(kind, dmg, magic, def, health, shieldMax, reach, affixTotal, rarity);
    }

    private static int intTag(org.bukkit.persistence.PersistentDataContainer pdc, String key) {
        Integer v = pdc.get(org.bukkit.NamespacedKey.minecraft(key),
                org.bukkit.persistence.PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    private static double doubleTag(org.bukkit.persistence.PersistentDataContainer pdc, String key) {
        Double v = pdc.get(org.bukkit.NamespacedKey.minecraft(key),
                org.bukkit.persistence.PersistentDataType.DOUBLE);
        return v == null ? 0.0 : v;
    }

    private static String strTag(org.bukkit.persistence.PersistentDataContainer pdc, String key) {
        return pdc.get(org.bukkit.NamespacedKey.minecraft(key),
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    // ---------- Player total CP ----------

    /** Where a player's total CP comes from. {@code total} is always the sum of the parts. */
    public record Breakdown(double weapon, double armor, double shields, double other,
                            double upgrades, double tonics, double total) {
        public static Breakdown of(double weapon, double armor, double shields, double other,
                                   double upgrades, double tonics) {
            return new Breakdown(weapon, armor, shields, other, upgrades, tonics,
                    weapon + armor + shields + other + upgrades + tonics);
        }
    }

    /**
     * Current-build CP for the given inventory. Weapon contribution is the strongest usable
     * weapon anywhere in the loadout (not just the held one); armor covers all 4 slots;
     * only the equipped shield (hotbar slot 9) counts — spares in the bag don't inflate
     * difficulty; broken items contribute nothing (mirroring
     * {@link PlayerState#recomputeStats}). Class passives, special effects and support buffs
     * intentionally contribute nothing — CP is raw gear + permanent-upgrade strength.
     */
    public static Breakdown playerBreakdown(PlayerInventory inv, Map<String, Integer> upgrades,
                                            int tonicDamage, int tonicDefense) {
        double bestWeapon = 0.0;
        double armor = 0.0;
        double shields = 0.0;
        double other = 0.0;
        boolean magicWeaponHeld = false;
        if (inv != null) {
            ItemStack bestStack = null;
            List<ItemStack> all = new ArrayList<>();
            ItemStack[] storage = inv.getStorageContents();
            for (int i = 0; i < storage.length; i++) {
                ItemStack s = storage[i];
                if (s == null) continue;
                all.add(s);
                if (i != com.lieyabull.dung.game.DungeonInstance.SHIELD_SLOT) continue;
                if (s.getType() == Material.AIR) continue;
                if ("shield".equals(GearFactory.kindOfPublic(s)) && !GearFactory.isBroken(s)) {
                    shields += itemCp(s);
                }
            }
            Collections.addAll(all, inv.getArmorContents());
            all.add(inv.getItemInOffHand());
            for (ItemStack s : all) {
                if (s == null || s.getType() == Material.AIR) continue;
                String kind = GearFactory.kindOfPublic(s);
                if ("weapon".equals(kind)) {
                    // Magic is tied to the weapon actually tallying CP (the strongest usable one),
                    // NOT whatever happens to be in the main hand — a player who swaps to a weak
                    // melee stick while their magic weapon sits in a hotbar slot was silently
                    // losing their weapon contribution AND flipping the melee-damage upgrade on.
                    if (!GearFactory.isBroken(s)) {
                        double cp = itemCp(s);
                        if (cp > bestWeapon) {
                            bestWeapon = cp;
                            bestStack = s;
                        }
                    }
                } else if (GearFactory.isGear(s)
                        && !"armor".equals(kind)
                        && !"weapon".equals(kind)
                        && !"shield".equals(kind)) {
                    other += itemCp(s);
                }
            }
            for (ItemStack s : inv.getArmorContents()) {
                if (s == null || s.getType() == Material.AIR) continue;
                if ("armor".equals(GearFactory.kindOfPublic(s)) && !GearFactory.isBroken(s)) {
                    armor += itemCp(s);
                }
            }
            magicWeaponHeld = bestStack != null && intTagOf(bestStack, ItemTags.MAGIC_DAMAGE) > 0;
        }
        double up = upgradeCp(upgrades, magicWeaponHeld);
        double ton = tonicDamage * W_DAMAGE + tonicDefense * W_DEFENSE;
        return Breakdown.of(bestWeapon, armor, shields, other, up, ton);
    }

    /**
     * Permanent shard-bought upgrade tracks valued through the same weights: damage +4.0/level
     * (melee weapons only — a magic weapon's basic melee stays negligible), magic +4.0/level,
     * defense +2.0/level, crit +0.5%/level at 3.0 per 1% (+1.5/level), speed +0.03/level at the
     * 40.0 mobility weight (+1.2/level, bounded by the track's max level), mana +5/level at
     * 0.15 (+0.75/level), hearts +5/level at 0.5 (+2.5/level).
     */
    public static double upgradeCp(Map<String, Integer> upgrades, boolean magicWeaponHeld) {
        if (upgrades == null || upgrades.isEmpty()) return 0.0;
        int dmg = upgrades.getOrDefault("damage", 0);
        int magic = upgrades.getOrDefault("magic_damage", 0);
        int def = upgrades.getOrDefault("defense", 0);
        int crit = upgrades.getOrDefault("crit", 0);
        int spd = upgrades.getOrDefault("speed", 0);
        int mana = upgrades.getOrDefault("mana", 0);
        int hearts = upgrades.getOrDefault("hearts", 0);
        double cp = 0.0;
        if (dmg > 0 && !magicWeaponHeld) cp += dmg * Upgrades.delta(Upgrades.DAMAGE) * W_DAMAGE;
        if (magic > 0) cp += magic * Upgrades.delta(Upgrades.MAGIC_DAMAGE) * W_MAGIC;
        if (def > 0) cp += def * Upgrades.delta(Upgrades.DEFENSE) * W_DEFENSE;
        if (crit > 0) cp += crit * Upgrades.CRIT_DELTA_PCT * W_CRIT_PER_PCT;
        if (spd > 0) cp += spd * (Upgrades.delta(Upgrades.SPEED) / 100.0) * 40.0;
        if (mana > 0) cp += mana * Upgrades.delta(Upgrades.MANA) * 0.15;
        if (hearts > 0) cp += hearts * Upgrades.delta(Upgrades.HEARTS) * W_HEALTH;
        return cp;
    }

    private static int intTagOf(ItemStack s, String tag) {
        if (s == null || s.getItemMeta() == null) return 0;
        Integer v = s.getItemMeta().getPersistentDataContainer().get(
                org.bukkit.NamespacedKey.minecraft(tag),
                org.bukkit.persistence.PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    // ---------- Party + difficulty modifier (section 15) ----------

    /**
     * Weighted party CP: sort ascending (weakest first) and apply 100%/90%/80%/64%, continuing
     * at 20% steps floored at 0 for larger parties. A solo player is 100% of their own CP.
     */
    public static double weightedPartyCp(List<Double> memberCp) {
        if (memberCp == null || memberCp.isEmpty()) return 0.0;
        List<Double> sorted = new ArrayList<>(memberCp);
        Collections.sort(sorted);
        double total = 0.0;
        for (int i = 0; i < sorted.size(); i++) {
            double w = i < PARTY_WEIGHTS.length ? PARTY_WEIGHTS[i] : Math.max(0.0, 1.0 - 0.2 * i);
            total += sorted.get(i) * w;
        }
        return total;
    }

    /** Expected CP of a correctly-progressed solo player at the given 1-based floor. */
    public static double referenceCp(int floorOneBased) {
        return REF_CP_START + Math.max(0, floorOneBased - 1) * CP_PER_FLOOR;
    }

    /**
     * Locked run difficulty modifier from the weighted party CP against the reference for the
     * starting floor: {@code clamp((weighted/ref - 1) * 1.0, cap)}, where the cap is +-0.10 on
     * floors 1-5 and +-0.20 on floors 6+. Positive = party stronger than expected.
     *
     * <p>For a party the reference is the per-member curve scaled by the SAME weights that summed
     * the party (§13: 100/90/80/64 weakest-first) — so a party whose strongest-to-weakest members
     * all sit ON the reference curve always lands at ratio 1.0, i.e. neutral. Dividing a weighted
     * party sum by the bare per-member reference made every 2+ player group 1.9-3.34x "over" the
     * reference and permanently pinned the modifier at the +cap, so section 15's "cpRatio ~= 1.0
     * means right on the expected curve" could never hold for groups.
     */
    public static double difficultyModifier(double weightedCp, int startFloorOneBased) {
        return difficultyModifier(weightedCp, startFloorOneBased, 1);
    }

    /** Party-aware variant: {@code memberCount} scales the reference by the party weight sum (see
     *  above). Values past 4 members are capped at the four-member sum (the documented weights stop
     *  there; Dung parties cap at 4 anyway). */
    public static double difficultyModifier(double weightedCp, int startFloorOneBased, int memberCount) {
        double raw = rawMismatch(weightedCp, startFloorOneBased, memberCount);
        double cap = startFloorOneBased <= LATE_FLOOR ? CAP_EARLY : CAP_LATE;
        return Math.max(-cap, Math.min(cap, raw));
    }

    /** Uncapped mismatch ratio behind the modifier: {@code (weighted/ref - 1) * sensitivity}.
     *  The three difficulty shares clamp this independently (tight elite leash, middle damage,
     *  wide health) instead of sharing the headline cap. */
    public static double rawMismatch(double weightedCp, int startFloorOneBased, int memberCount) {
        double ref = referenceCp(startFloorOneBased) * weightSum(memberCount);
        if (ref <= 0) return 0.0;
        return (weightedCp / ref - 1.0) * SENSITIVITY;
    }

    /** Sum of the party weights (1.0 + 0.9 + 0.8 + 0.64 + stop) for {@code n} members. */
    private static double weightSum(int memberCount) {
        return switch (Math.max(1, memberCount)) {
            case 1 -> 1.0;
            case 2 -> 1.9;
            case 3 -> 2.7;
            default -> 3.34; // 4+ members
        };
    }

    /** Encounter-complexity share: tight leash, hard-capped so elite injection never passes
     *  25% per normal room on any floor. */
    public static double complexityOf(double modifier) {
        return Math.max(-COMPLEXITY_CAP, Math.min(COMPLEXITY_CAP, modifier * COMPLEXITY_SHARE));
    }

    /** Enemy damage share: middle leash between the elite and health caps. */
    public static double dmgOf(double raw, int startFloorOneBased) {
        double cap = startFloorOneBased <= LATE_FLOOR ? DMG_CAP_EARLY : DMG_CAP_LATE;
        return Math.max(-cap, Math.min(cap, raw));
    }

    /** Enemy health share: widest leash, allowed outside the elite cap. */
    public static double hpOf(double raw, int startFloorOneBased) {
        double cap = startFloorOneBased <= LATE_FLOOR ? HP_CAP_EARLY : HP_CAP_LATE;
        return Math.max(-cap, Math.min(cap, raw));
    }
}
