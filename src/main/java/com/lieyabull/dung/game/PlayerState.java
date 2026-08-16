package com.lieyabull.dung.game;

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
    public double defense = 0;
    public double reach = 3.0;   // melee attack range, blocks
    public double critChance = 0.05;
    public double critMult = 1.5;
    public double speedMult = 1.0;
    public int fireRateTicks = 3;
    /** Per-player attack cooldown counter (ticks remaining before next swing). */
    public int fireCd = 0;
    // class
    public String classId = "warrior";
    // permanent (shard-bought) upgrades: track id -> owned level
    public final Map<String, Integer> upgrades = new java.util.HashMap<>();
    // cooldowns (ms remaining) keyed by ability id
    public final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

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

    public PlayerState(Player p) {
        this.player = p;
    }

    /** Recomputed each time gear changes. Called by GameManager on equip/unequip/floor change. */
    public void recomputeStats() {
        PlayerInventory inv = player.getInventory();
        damage = 3;
        defense = 0;
        reach = 3.0;
        critChance = 0.05;
        critMult = 1.5;
        fireRateTicks = 3;
        speedMult = 1.0;
        int healthBonus = 0;
        // weapon damage from mainhand — only a real weapon counts. Holding an armor piece in-hand
        // (which carries HEALTH/DEFENSE/RARITY tags) must NOT grant its stats; it only applies when
        // actually equipped, so players can't get the bonuses by just carrying the item.
        ItemStack weapon = inv.getItemInMainHand();
        boolean mainhandIsWeapon = isWeaponKind(weapon);
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
        // armor defense from 4 armor slots; rarity pushes crit
        for (ItemStack s : inv.getArmorContents()) {
            Integer def = intTag(s, ItemTags.DEFENSE);
            if (def != null) defense += def;
            Rarity r = rarityOf(s);
            if (r != null) critChance += 0.01 * r.ordinal();
            Integer h = intTag(s, ItemTags.HEALTH);
            if (h != null) healthBonus += h;
        }
        applyClassPassives();
        applyUpgrades();
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
        if (dmg > 0) damage += dmg * com.lieyabull.dung.meta.Upgrades.delta(com.lieyabull.dung.meta.Upgrades.DAMAGE);
        int def = upgrades.getOrDefault("defense", 0);
        if (def > 0) defense += def * com.lieyabull.dung.meta.Upgrades.delta(com.lieyabull.dung.meta.Upgrades.DEFENSE);
        int crit = upgrades.getOrDefault("crit", 0);
        if (crit > 0) critChance += crit * 0.005;
        int spd = upgrades.getOrDefault("speed", 0);
        if (spd > 0) speedMult += spd * 0.03;
        int manaUp = upgrades.getOrDefault("mana", 0);
        if (manaUp > 0) maxMana += manaUp * com.lieyabull.dung.meta.Upgrades.delta(com.lieyabull.dung.meta.Upgrades.MANA);
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
                fireRateTicks = (int) Math.max(2, fireRateTicks - 2);
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
        hearts -= mitigated;
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