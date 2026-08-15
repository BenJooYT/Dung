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
    public int fireRateTicks = 12;
    // class
    public String classId = "warrior";
    // cooldowns (ms remaining) keyed by ability id
    public final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    // invulnerability timestamp (ms)
    public long invulnUntil = 0;
    public boolean dead = false;

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
        fireRateTicks = 12;
        speedMult = 1.0;
        int healthBonus = 0;
        // weapon damage from mainhand
        ItemStack weapon = inv.getItemInMainHand();
        Integer wdmg = intTag(weapon, ItemTags.DAMAGE);
        if (wdmg != null) damage = wdmg;
        // some weapons extend melee reach (tags set by GearFactory)
        Double wreach = doubleTag(weapon, ItemTags.REACH);
        if (wreach != null) reach = wreach;
        // rarity adds crit/knockback flavor so builds diverge (SkyBlock-style)
        Rarity wr = rarityOf(weapon);
        if (wr != null) {
            critChance += 0.02 * wr.ordinal();
            critMult = Math.min(3.0, 1.5 + wr.ordinal() * 0.1);
        }
        Integer whealth = intTag(weapon, ItemTags.HEALTH);
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
        // Apply the health affix to the max-heart pool as a symmetric reservoir so it can't be
        // farmed by swapping gear: growing the pool burst-heals the gained amount (equipping a big
        // chestpiece feels good), and shrinking it refunds exactly that amount (no free healing).
        int oldMax = maxHearts;
        maxHearts = 100 + healthBonus;
        if (maxHearts > oldMax) {
            hearts += (maxHearts - oldMax);
        } else if (maxHearts < oldMax) {
            hearts -= (oldMax - maxHearts);
        }
        hearts = Math.max(0.0, Math.min(hearts, maxHearts));
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
        mana = Math.min(maxMana, mana); // clamp current mana to the (possibly reduced) max
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
        invulnUntil = System.currentTimeMillis() + 1000;
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