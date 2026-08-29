package com.lieyabull.dung.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic tests for {@link PlayerState} that do not require a Bukkit server.
 * Since PlayerState's constructor requires a Player, we create instances via a
 * helper that passes null and then sets the public fields directly.
 */
public class PlayerStateTest {

    /** Create a PlayerState with null player (safe for pure-logic tests). */
    private static PlayerState makeState() {
        PlayerState st = new PlayerState(null);
        // Set defaults explicitly so tests are independent of any constructor changes.
        st.maxHearts = 100;
        st.hearts = 100.0;
        st.mana = 100;
        st.maxMana = 100;
        st.defense = 0;
        st.damage = 3;
        st.critChance = 0.05;
        st.critMult = 1.5;
        st.dead = false;
        st.invulnUntil = 0;
        st.damageBoostUntil = 0;
        st.guaranteedCritUntil = 0;
        return st;
    }

    // ---- constructor defaults ----

    @Test
    void constructorSetsDefaultValues() {
        PlayerState st = new PlayerState(null);
        assertEquals(100, st.maxHearts);
        assertEquals(100.0, st.hearts, 0.001);
        assertEquals(100.0, st.mana, 0.001);
        assertEquals(100.0, st.maxMana, 0.001);
        assertEquals(5.0, st.manaRegen, 0.001);
        assertEquals(2.0, st.healPerSecond, 0.001);
        assertEquals(0, st.coins);
        assertEquals(0, st.keys);
        assertEquals(0, st.bombs);
        assertEquals(3.0, st.damage, 0.001);
        assertEquals(0.0, st.defense, 0.001);
        assertEquals(3.0, st.reach, 0.001);
        assertEquals(0.05, st.critChance, 0.001);
        assertEquals(1.5, st.critMult, 0.001);
        assertEquals(1.0, st.speedMult, 0.001);
        assertEquals(7, st.fireRateTicks);
        assertEquals("warrior", st.classId);
        assertFalse(st.dead);
        assertEquals(0, st.invulnUntil);
    }

    // ---- hurt() ----

    @Test
    void hurtReducesHearts() {
        PlayerState st = makeState();
        st.hurt(30);
        assertEquals(70.0, st.hearts, 0.001);
        assertFalse(st.dead);
    }

    @Test
    void hurtSetsDeadWhenHeartsZero() {
        PlayerState st = makeState();
        st.hurt(100);
        assertTrue(st.hearts <= 0);
        assertTrue(st.dead);
    }

    @Test
    void hurtSetsDeadWhenHeartsBelowZero() {
        PlayerState st = makeState();
        st.hurt(200);
        assertTrue(st.hearts <= 0);
        assertTrue(st.dead);
    }

    @Test
    void hurtAppliesDefenseReduction() {
        PlayerState st = makeState();
        st.defense = 100; // 100 defense -> 50% damage reduction
        st.hurt(100);
        // mitigated = max(1, 100 * (100 / (100 + 100))) = max(1, 100 * 0.5) = 50
        assertEquals(50.0, st.hearts, 0.001);
    }

    @Test
    void hurtWithHighDefenseStillDealsMinimumDamage() {
        PlayerState st = makeState();
        st.defense = 10000; // very high defense
        st.hurt(5);
        // mitigated = max(1, 5 * (100 / (100 + 10000))) ≈ max(1, 0.0495) = 1
        assertEquals(99.0, st.hearts, 0.001);
    }

    @Test
    void hurtDoesNothingWhenDead() {
        PlayerState st = makeState();
        st.dead = true;
        st.hurt(50);
        assertEquals(100.0, st.hearts, 0.001);
    }

    @Test
    void hurtDoesNothingDuringInvulnerability() {
        PlayerState st = makeState();
        st.invulnUntil = System.currentTimeMillis() + 10_000; // invuln for 10s
        st.hurt(50);
        assertEquals(100.0, st.hearts, 0.001);
    }

    @Test
    void hurtSetsInvulnerability() {
        PlayerState st = makeState();
        st.hurt(10);
        assertTrue(st.invulnUntil > System.currentTimeMillis());
    }

    // ---- isInvuln() ----

    @Test
    void isInvulnReturnsTrueDuringInvulnerabilityPeriod() {
        PlayerState st = makeState();
        st.invulnUntil = System.currentTimeMillis() + 10_000;
        assertTrue(st.isInvuln());
    }

    @Test
    void isInvulnReturnsFalseAfterInvulnerabilityExpires() {
        PlayerState st = makeState();
        st.invulnUntil = System.currentTimeMillis() - 1;
        assertFalse(st.isInvuln());
    }

    @Test
    void isInvulnReturnsFalseWhenNoInvulnerability() {
        PlayerState st = makeState();
        st.invulnUntil = 0;
        assertFalse(st.isInvuln());
    }

    // ---- spendMana() ----

    @Test
    void spendManaReducesMana() {
        PlayerState st = makeState();
        st.spendMana(30);
        assertEquals(70.0, st.mana, 0.001);
    }

    @Test
    void spendManaDoesNotGoBelowZero() {
        PlayerState st = makeState();
        st.spendMana(200);
        assertEquals(0.0, st.mana, 0.001);
    }

    @Test
    void spendManaWithExactAmount() {
        PlayerState st = makeState();
        st.spendMana(100);
        assertEquals(0.0, st.mana, 0.001);
    }

    // ---- canCast() ----

    @Test
    void canCastReturnsTrueWhenSufficientManaAndNoCooldown() {
        PlayerState st = makeState();
        assertTrue(st.canCast("slash", 30, 1000));
    }

    @Test
    void canCastReturnsFalseWhenInsufficientMana() {
        PlayerState st = makeState();
        st.mana = 10;
        assertFalse(st.canCast("slash", 30, 1000));
    }

    @Test
    void canCastReturnsFalseWhenOnCooldown() {
        PlayerState st = makeState();
        st.cooldowns.put("slash", System.currentTimeMillis() + 10_000);
        assertFalse(st.canCast("slash", 30, 1000));
    }

    @Test
    void canCastReturnsTrueWhenCooldownExpired() {
        PlayerState st = makeState();
        st.cooldowns.put("slash", System.currentTimeMillis() - 1);
        assertTrue(st.canCast("slash", 30, 1000));
    }

    @Test
    void canCastReturnsTrueWhenExactMana() {
        PlayerState st = makeState();
        assertTrue(st.canCast("slash", 100, 1000));
    }

    // ---- startCooldown() ----

    @Test
    void startCooldownSetsCooldown() {
        PlayerState st = makeState();
        st.startCooldown("slash", 5000);
        Long until = st.cooldowns.get("slash");
        assertNotNull(until);
        assertTrue(until > System.currentTimeMillis());
    }

    @Test
    void startCooldownAndExpiry() throws InterruptedException {
        PlayerState st = makeState();
        st.startCooldown("quick", 10); // 10ms cooldown
        assertFalse(st.canCast("quick", 10, 10));
        Thread.sleep(20);
        assertTrue(st.canCast("quick", 10, 10));
    }

    @Test
    void startCooldownOverwritesPrevious() {
        PlayerState st = makeState();
        st.startCooldown("slash", 1000);
        long first = st.cooldowns.get("slash");
        st.startCooldown("slash", 5000);
        long second = st.cooldowns.get("slash");
        assertTrue(second > first);
    }

    // ---- recomputeStats() ----
    // Note: full recomputeStats() requires a Bukkit Player with inventory.
    // We test only the class-bonus portion via applyClassPassives().

    @Test
    void recomputeStatsAppliesWarriorClassBonus() {
        PlayerState st = makeState();
        st.classId = "warrior";
        st.applyClassPassives();
        // warrior: damage *= 1.15, defense += 2
        assertEquals(3.0 * 1.15, st.damage, 0.001);
        assertEquals(2.0, st.defense, 0.001);
        assertEquals(100.0, st.maxMana, 0.001);
    }

    @Test
    void recomputeStatsAppliesMageClassBonus() {
        PlayerState st = makeState();
        st.classId = "mage";
        st.applyClassPassives();
        // mage: maxMana = 160, manaRegen = 8.0
        assertEquals(160.0, st.maxMana, 0.001);
        assertEquals(8.0, st.manaRegen, 0.001);
    }

    @Test
    void recomputeStatsAppliesRangerClassBonus() {
        PlayerState st = makeState();
        st.classId = "ranger";
        st.applyClassPassives();
        // ranger: critChance += 0.10, fireRateTicks = max(5, 7-2) = 5
        assertEquals(0.15, st.critChance, 0.001);
        assertEquals(5, st.fireRateTicks);
    }

    @Test
    void mageClassSetsMaxMana() {
        PlayerState st = makeState();
        st.classId = "mage";
        st.applyClassPassives();
        assertEquals(160.0, st.maxMana, 0.001);
        assertEquals(8.0, st.manaRegen, 0.001);
    }

    @Test
    void mageClassDoesNotClampManaInApplyClassPassives() {
        // Mana clamping is now done in recomputeStats() after upgrades are applied,
        // so applyClassPassives() alone should not clamp mana.
        PlayerState st = makeState();
        st.mana = 200; // above default maxMana of 100
        st.classId = "mage";
        st.applyClassPassives();
        assertEquals(160.0, st.maxMana, 0.001);
        assertEquals(200.0, st.mana, 0.001); // not clamped — clamp happens in recomputeStats()
    }

    // ---- hasDamageBoost() ----

    @Test
    void hasDamageBoostReturnsTrueWhenActive() {
        PlayerState st = makeState();
        st.damageBoostUntil = System.currentTimeMillis() + 10_000;
        assertTrue(st.hasDamageBoost());
    }

    @Test
    void hasDamageBoostReturnsFalseWhenExpired() {
        PlayerState st = makeState();
        st.damageBoostUntil = System.currentTimeMillis() - 1;
        assertFalse(st.hasDamageBoost());
    }

    @Test
    void hasDamageBoostReturnsFalseWhenNeverActivated() {
        PlayerState st = makeState();
        st.damageBoostUntil = 0;
        assertFalse(st.hasDamageBoost());
    }

    // ---- hasGuaranteedCrit() ----

    @Test
    void hasGuaranteedCritReturnsTrueWhenActive() {
        PlayerState st = makeState();
        st.guaranteedCritUntil = System.currentTimeMillis() + 10_000;
        assertTrue(st.hasGuaranteedCrit());
    }

    @Test
    void hasGuaranteedCritReturnsFalseWhenExpired() {
        PlayerState st = makeState();
        st.guaranteedCritUntil = System.currentTimeMillis() - 1;
        assertFalse(st.hasGuaranteedCrit());
    }

    @Test
    void hasGuaranteedCritReturnsFalseWhenNeverActivated() {
        PlayerState st = makeState();
        st.guaranteedCritUntil = 0;
        assertFalse(st.hasGuaranteedCrit());
    }

    // ---- heal() ----

    @Test
    void healRestoresHearts() {
        PlayerState st = makeState();
        st.hearts = 50;
        st.heal(30);
        assertEquals(80.0, st.hearts, 0.001);
    }

    @Test
    void healDoesNotExceedMaxHearts() {
        PlayerState st = makeState();
        st.hearts = 90;
        st.heal(30);
        assertEquals(100.0, st.hearts, 0.001);
    }

    @Test
    void healFromZero() {
        PlayerState st = makeState();
        st.hearts = 0;
        st.heal(50);
        assertEquals(50.0, st.hearts, 0.001);
    }

    @Test
    void healExactToMax() {
        PlayerState st = makeState();
        st.hearts = 80;
        st.heal(20);
        assertEquals(100.0, st.hearts, 0.001);
    }

    // ---- regenMana() ----

    @Test
    void regenManaRestoresMana() {
        PlayerState st = makeState();
        st.mana = 50;
        st.regenMana();
        assertEquals(50 + 5.0 / 20.0, st.mana, 0.001);
    }

    @Test
    void regenManaDoesNotExceedMax() {
        PlayerState st = makeState();
        st.mana = 99.9;
        st.regenMana();
        assertTrue(st.mana <= st.maxMana);
    }

    // ---- regenHearts() ----

    @Test
    void regenHeartsDoesNothingWhileDead() {
        PlayerState st = makeState();
        st.hearts = 50;
        st.dead = true;
        st.regenHearts();
        assertEquals(50.0, st.hearts, 0.001);
    }

    @Test
    void regenHeartsDoesNothingWhenHeartsAreFull() {
        PlayerState st = makeState();
        st.regenHearts();
        assertEquals(100.0, st.hearts, 0.001);
    }

    @Test
    void regenHeartsDoesNothingWhenHealPerSecondIsZero() {
        PlayerState st = makeState();
        st.hearts = 50;
        st.healPerSecond = 0;
        st.regenHearts();
        assertEquals(50.0, st.hearts, 0.001);
    }
}