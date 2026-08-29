package com.lieyabull.dung.game;

import com.lieyabull.dung.items.Affix;
import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.items.ItemTags;
import com.lieyabull.dung.items.Rarity;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live player state for a run. Holds MMORPG combat stats (recomputed from gear SkyBlock
 * style), resource bars (HP/mana), run consumables, and ability cooldowns.
 */
public final class PlayerState {
    public final Player player;
    public int maxHearts = 100;      // 100 HP
    public double hearts = 100.0;
    public double mana = 100;
    public double maxMana = 100;
    /** Mana regen in POINTS PER SECOND (not per tick); upgrades/accessories can raise it later. */
    public double manaRegen = 5.0;
    /** Out-of-combat HP regen in POINTS PER SECOND. 0 = no natural healing. */
    public double healPerSecond = 2.0;
    /** Seconds a player must go without damage before natural regen resumes. */
    public static final double HEAL_DELAY_SECONDS = 5.0;
    /** Timestamp (ms) of the last time the player took damage; used to gate natural regen. */
    private long lastDamageTime = 0;
    public int coins;
    public int keys;   // placeholder: no sink yet; reserved for future locked-door/chest costs
    public int bombs;  // placeholder: no sink yet; reserved for future destructible-wall costs
    // computed combat stats
    public double damage = 3;
    public double magicDamage = 0; // separate magic damage stat (for magic weapons)
    public double defense = 0;
    public double reach = 3.0;   // melee attack range, blocks
    public double critChance = 0.05;
    public double critMult = 1.5;
    public double speedMult = 1.0;
    public int fireRateTicks = 7;
    /** Per-player attack cooldown counter (ticks remaining before next swing). */
    public int fireCd = 0;
    /** True when the held mainhand weapon is a magic weapon (its basic melee stays negligible and
     *  ignores the melee-damage upgrade — only the magic-damage upgrade scales it). */
    public boolean magicWeapon = false;
    // class
    public String classId = "warrior";
    /** Run-long stat bonuses from shop tonics; re-applied by recomputeStats so gear swaps don't wipe them. */
    public int tonicDamage = 0;
    public int tonicDefense = 0;
    // permanent (shard-bought) upgrades: track id -> owned level
    public final Map<String, Integer> upgrades = new java.util.HashMap<>();
    // cooldowns (ms remaining) keyed by ability id
    public final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    // timestamp (ms) of the last successful cast per ability key, used to suppress the redundant
    // "on cooldown" message when a single input (e.g. a main/off-hand duplicate event) re-invokes
    // an ability that just cast within the same instant.
    public final Map<String, Long> lastCastAt = new ConcurrentHashMap<>();
    // minimum gap (ms) used to treat a failing ability as a duplicate of one that just cast.
    public static final long CAST_DUPLICATE_WINDOW_MS = 100;

    /** Global-cooldown key: shared by every ability/class cast so weapon-swap burst-spam is impossible. */
    public static final String GCD_KEY = "__gcd";
    /** Global cooldown between any two ability casts, in ms. */
    public static final long GCD_MS = 400;
    // invulnerability timestamp (ms)
    public long invulnUntil = 0;
    public boolean dead = false;
    // class ability: damage boost (War Cry)
    public long damageBoostUntil = 0;
    public double damageBoostMult = 1.0;
    // class ability: guaranteed crit (Shadow Step)
    public long guaranteedCritUntil = 0;
    // Mana Shield
    public double shield = 0;        // current shield charge
    public double shieldMax = 0;     // max shield capacity (from held item)
    public boolean shieldActive = false; // true when sneaking with mana shield
    /** Tracks how many times the shield has absorbed damage; run shields take 1-3 durability
     *  damage every 2 uses, persistent shields take 1 every 5 uses. */
    public int shieldUseCount = 0;
    /** Sum of health-affix bonuses pending from equipped gear; folded into maxHearts in recomputeStats. */
    private int pendingHealthAffixes = 0;

    public PlayerState(Player p) {
        this.player = p;
    }

    /** Recomputed each time gear changes. Called by GameManager on equip/unequip/floor change. */
    public void recomputeStats() {
        PlayerInventory inv = player.getInventory();
        damage = 3;
        magicDamage = 0;
        defense = 0;
        reach = 3.0;
        critChance = 0.05;
        critMult = 1.5;
        fireRateTicks = 7;
        speedMult = 1.0;
        int healthBonus = 0;
        // weapon damage from mainhand — only a real weapon counts. Holding an armor piece in-hand
        // (which carries HEALTH/DEFENSE/RARITY tags) must NOT grant its stats; it only applies when
        // actually equipped, so players can't get the bonuses by just carrying the item.
        // Broken items (durability == 0) are also skipped — they provide no stats until repaired.
        ItemStack weapon = inv.getItemInMainHand();
        boolean mainhandIsWeapon = isWeaponKind(weapon) && !com.lieyabull.dung.items.GearFactory.isBroken(weapon);
        Integer wdmg = mainhandIsWeapon ? intTag(weapon, ItemTags.DAMAGE) : null;
        if (wdmg != null) damage = wdmg;
        // some weapons extend melee reach (tags set by GearFactory)
        Double wreach = mainhandIsWeapon ? doubleTag(weapon, ItemTags.REACH) : null;
        if (wreach != null) reach = wreach;
        // rarity adds crit/knockback flavor so builds diverge (SkyBlock-style)
        Rarity wr = mainhandIsWeapon ? rarityOf(weapon) : null;
        if (wr != null) {
            critChance += 0.02 * wr.ordinal();
            critMult = Math.min(3.0, 1.5 + wr.ordinal() * 0.1);
        }
        Integer whealth = mainhandIsWeapon ? intTag(weapon, ItemTags.HEALTH) : null;
        if (whealth != null) healthBonus += whealth;
        // Magic damage from held weapon (separate from melee damage)
        Integer wmagic = mainhandIsWeapon ? intTag(weapon, ItemTags.MAGIC_DAMAGE) : null;
        if (wmagic != null) magicDamage = wmagic;
        magicWeapon = mainhandIsWeapon && wmagic != null;
        // Affix bonuses from the held weapon (procedural affixes boost stats on top of the base tags)
        applyAffixBonuses(weapon, mainhandIsWeapon);
        // armor defense from 4 armor slots; rarity pushes crit. Broken items provide no stats.
        for (ItemStack s : inv.getArmorContents()) {
            if (com.lieyabull.dung.items.GearFactory.isBroken(s)) continue;
            Integer def = intTag(s, ItemTags.DEFENSE);
            if (def != null) defense += def;
            Rarity r = rarityOf(s);
            if (r != null) critChance += 0.01 * r.ordinal();
            Integer h = intTag(s, ItemTags.HEALTH);
            if (h != null) healthBonus += h;
            applyAffixBonuses(s, true);
        }
        healthBonus += pendingHealthAffixes;
        pendingHealthAffixes = 0;
        applyClassPassives();
        applyUpgrades();
        // Shop tonics persist across gear swaps: re-applied after every recompute
        damage += tonicDamage;
        defense += tonicDefense;
        // Clamp current mana to the final maxMana (after upgrades) so switching weapons
        // doesn't clamp against the temporary base maxMana before upgrades are applied.
        mana = Math.min(maxMana, mana);
        // Apply the health affix to the max-heart pool as a symmetric reservoir so it can't be
        // farmed by swapping gear: growing the pool burst-heals the gained amount (equipping a big
        // chestpiece feels good), and shrinking it refunds exactly that amount (no free healing).
        int permanentHearts = upgrades.getOrDefault("hearts", 0) * com.lieyabull.dung.meta.Upgrades.delta(com.lieyabull.dung.meta.Upgrades.HEARTS);
        int oldMax = maxHearts;
        maxHearts = 100 + healthBonus + permanentHearts;
        if (maxHearts > oldMax) {
            hearts += (maxHearts - oldMax);
        } else if (maxHearts < oldMax) {
            hearts -= (oldMax - maxHearts);
        }
        hearts = Math.max(0.0, Math.min(hearts, maxHearts));
    }

    /** Apply permanent shard-bought upgrades on top of gear + class passives. */
    private void applyUpgrades() {
        int dmg = upgrades.getOrDefault("damage", 0);
        // The melee-damage upgrade scales melee weapons only; a magic weapon's basic melee stays
        // negligible (1) — it scales through the magic-damage upgrade instead.
        if (dmg > 0 && !magicWeapon) damage += dmg * com.lieyabull.dung.meta.Upgrades.delta(com.lieyabull.dung.meta.Upgrades.DAMAGE);
        int magic = upgrades.getOrDefault("magic_damage", 0);
        if (magic > 0) magicDamage += magic * com.lieyabull.dung.meta.Upgrades.delta(com.lieyabull.dung.meta.Upgrades.MAGIC_DAMAGE);
        int def = upgrades.getOrDefault("defense", 0);
        if (def > 0) defense += def * com.lieyabull.dung.meta.Upgrades.delta(com.lieyabull.dung.meta.Upgrades.DEFENSE);
        int crit = upgrades.getOrDefault("crit", 0);
        if (crit > 0) critChance += crit * com.lieyabull.dung.meta.Upgrades.CRIT_DELTA_PCT / 100.0;
        int spd = upgrades.getOrDefault("speed", 0);
        if (spd > 0) speedMult += spd * 0.03;
        int manaUp = upgrades.getOrDefault("mana", 0);
        if (manaUp > 0) maxMana += manaUp * com.lieyabull.dung.meta.Upgrades.delta(com.lieyabull.dung.meta.Upgrades.MANA);
    }

    /** Apply affix stat bonuses from an equipped item onto the live combat stats. Affixes add flat
     *  values on top of the base stat tags. Only applied for equipped gear (weapon in hand or a worn
     *  armor slot). Health affixes accumulate into {@link #pendingHealthAffixes}. */
    private void applyAffixBonuses(ItemStack s, boolean equipped) {
        if (!equipped || s == null || s.getType() == Material.AIR || s.getItemMeta() == null) return;
        for (Affix.AffixRoll roll : GearFactory.getAffixes(s)) {
            switch (roll.affix().stat) {
                case DAMAGE -> damage += roll.value();
                case MAGIC_DAMAGE -> magicDamage += roll.value();
                case DEFENSE -> defense += roll.value();
                case HEALTH -> pendingHealthAffixes += roll.value();
                case SHIELD_MAX -> shieldMax += roll.value();
            }
        }
    }

    public void applyClassPassives() {
        // reset resource baselines first so swapping classes never leaves a stale mana pool
        maxMana = 100;
        manaRegen = 5.0; // default mana regen, per second
        switch (classId) {
            case "warrior":
                damage *= 1.15;
                defense += 2;
                break;
            case "mage":
                maxMana = 160;
                manaRegen = 8.0; // per second: faster pool for a caster
                break;
            case "ranger":
                critChance += 0.10;
                fireRateTicks = (int) Math.max(5, fireRateTicks - 2);
                break;
        }
        // Mana clamp is now done in recomputeStats() after applyUpgrades(), so the upgrade
        // mana bonus is included in the clamp target. This prevents weapon-swap from clamping
        // mana against the base maxMana before upgrades are factored in.
    }

    private static boolean isWeaponKind(ItemStack s) {
        if (s == null || s.getType() == Material.AIR) return false;
        var pdc = s.getItemMeta() == null ? null : s.getItemMeta().getPersistentDataContainer();
        if (pdc == null) return false;
        String k = pdc.get(org.bukkit.NamespacedKey.minecraft(ItemTags.KIND),
                org.bukkit.persistence.PersistentDataType.STRING);
        return "weapon".equals(k);
    }

    private static Integer intTag(ItemStack s, String key) {
        if (s == null || s.getType() == Material.AIR) return null;
        var pdc = s.getItemMeta() == null ? null : s.getItemMeta().getPersistentDataContainer();
        if (pdc == null || !pdc.has(org.bukkit.NamespacedKey.minecraft(key),
                org.bukkit.persistence.PersistentDataType.INTEGER)) {
            return null;
        }
        return pdc.get(org.bukkit.NamespacedKey.minecraft(key),
                org.bukkit.persistence.PersistentDataType.INTEGER);
    }

    private static Double doubleTag(ItemStack s, String key) {
        if (s == null || s.getType() == Material.AIR) return null;
        var pdc = s.getItemMeta() == null ? null : s.getItemMeta().getPersistentDataContainer();
        if (pdc == null || !pdc.has(org.bukkit.NamespacedKey.minecraft(key),
                org.bukkit.persistence.PersistentDataType.DOUBLE)) {
            return null;
        }
        return pdc.get(org.bukkit.NamespacedKey.minecraft(key),
                org.bukkit.persistence.PersistentDataType.DOUBLE);
    }

    public boolean isInvuln() {
        return System.currentTimeMillis() < invulnUntil;
    }

    public void hurt(double dmg) {
        if (isInvuln() || dead) return;
        double mitigated = Math.max(1.0, dmg * (100.0 / (100.0 + defense)));
        // Mana Shield: absorb damage with shield first if active
        if (shieldActive && shield > 0) {
            if (shield >= mitigated) {
                shield -= mitigated;
                mitigated = 0;
            } else {
                mitigated -= shield;
                shield = 0;
            }
            // Track shield uses. Run shields wear fast (1-3 durability per 2 absorptions);
            // persistent shields wear slow (1 per 5). The shield takes durability damage whether
            // it is held or stored anywhere in the inventory (it is the active source of the
            // shieldMax the damage is being absorbed against).
            shieldUseCount++;
            org.bukkit.inventory.PlayerInventory inv = player.getInventory();
            ItemStack active = inv.getItem(com.lieyabull.dung.game.DungeonInstance.SHIELD_SLOT);
            if (active != null && !active.getType().isAir()
                    && com.lieyabull.dung.items.GearFactory.isShield(active)) {
                boolean persistent = com.lieyabull.dung.items.GearFactory.isPersistent(active);
                int trigger = persistent ? 5 : 2;
                if (shieldUseCount >= trigger) {
                    shieldUseCount = 0;
                    // Damage the active shield in slot 9 (DungeonInstance.SHIELD_SLOT) — the only
                    // shield that provides capacity and thus the one absorbing the damage.
                    int wear = persistent ? 1
                            : java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 4); // 1-3
                    boolean broken = com.lieyabull.dung.items.GearFactory.damageItem(active, wear);
                    if (broken) {
                        inv.setItem(com.lieyabull.dung.game.DungeonInstance.SHIELD_SLOT, null);
                        player.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands(com.lieyabull.dung.lang.Lang.forPlayer(player, "gear.manaShieldBroke")));
                    }
                }
            }
        }
        if (mitigated > 0) {
            hearts -= mitigated;
        }
        invulnUntil = System.currentTimeMillis() + 500; // short i-frame: dodges stunlock, keeps the pressure on
        lastDamageTime = System.currentTimeMillis();
        if (hearts <= 0) dead = true;
    }

    public void heal(double amount) {
        hearts = Math.min(maxHearts, hearts + amount);
    }

    /** Called once per game tick; manaRegen is a per-second rate, so scale by the 20 t/s tick. */
    public void regenMana() {
        mana = Math.min(maxMana, mana + manaRegen / 20.0);
    }

    /**
     * Out-of-combat HP regen, called once per game tick. Does nothing while dead or within
     * {@link #HEAL_DELAY_SECONDS} of taking damage. No delay on a fully-healed pool means
     * hearts simply stay at max; {@code healPerSecond} 0 disables natural healing entirely.
     */
    public void regenHearts() {
        if (dead || healPerSecond <= 0 || hearts >= maxHearts) return;
        double since = (System.currentTimeMillis() - lastDamageTime) / 1000.0;
        if (since < HEAL_DELAY_SECONDS) return;
        hearts = Math.min(maxHearts, hearts + healPerSecond / 20.0);
    }

    /** Check if the damage boost from War Cry is active. */
    public boolean hasDamageBoost() {
        return System.currentTimeMillis() < damageBoostUntil;
    }

    /** Check if the guaranteed crit from Shadow Step is active. */
    public boolean hasGuaranteedCrit() {
        return System.currentTimeMillis() < guaranteedCritUntil;
    }

    public void spendMana(double amt) {
        mana = Math.max(0, mana - amt);
    }

    public boolean canCast(String ability, double cost, long cdMs) {
        if (mana < cost) return false;
        Long until = cooldowns.get(ability);
        return until == null || System.currentTimeMillis() >= until;
    }

    public void startCooldown(String ability, long cdMs) {
        cooldowns.put(ability, System.currentTimeMillis() + cdMs);
    }

    public Rarity bestEquipRarity() {
        Rarity best = Rarity.COMMON;
        for (ItemStack s : player.getInventory().getArmorContents()) {
            Rarity r = rarityOf(s);
            if (r != null && r.ordinal() > best.ordinal()) best = r;
        }
        Rarity wr = rarityOf(player.getInventory().getItemInMainHand());
        if (wr != null && wr.ordinal() > best.ordinal()) best = wr;
        return best;
    }

    private Rarity rarityOf(ItemStack s) {
        if (s == null || s.getType() == Material.AIR || s.getItemMeta() == null) return null;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        String v = pdc.get(org.bukkit.NamespacedKey.minecraft(ItemTags.RARITY),
                org.bukkit.persistence.PersistentDataType.STRING);
        return v == null ? null : Rarity.valueOf(v);
    }
}