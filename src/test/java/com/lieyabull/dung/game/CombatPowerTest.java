package com.lieyabull.dung.game;

import com.lieyabull.dung.items.Rarity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic tests for {@link CombatPower} — no Bukkit server needed. Covers the locked-in
 * section 14 weights (including the calibration targets) and the section 15 modifier math.
 */
public class CombatPowerTest {

    // ---- per-item weights ----

    @Test
    void starterWeaponCalibratesNear20Cp() {
        // COMMON Frayed Blade: 5 damage, no affixes, COMMON crit (ordinal 0 -> none).
        double cp = CombatPower.itemCpCore("weapon", 5, 0, 0, 0, 0, 0.0, 0, Rarity.COMMON);
        assertEquals(20.0, cp, 0.001);
    }

    @Test
    void starterArmorCalibratesNear2Cp() {
        // COMMON Cloth: 1 defense, COMMON crit -> none.
        double cp = CombatPower.itemCpCore("armor", 0, 0, 1, 0, 0, 0.0, 0, Rarity.COMMON);
        assertEquals(2.0, cp, 0.001);
    }

    @Test
    void magicWeaponIgnoresNegligibleMelee() {
        double magic = CombatPower.itemCpCore("weapon", 1, 40, 0, 0, 0, 0.0, 0, Rarity.COMMON);
        double meleeOnly = CombatPower.itemCpCore("weapon", 40, 0, 0, 0, 0, 0.0, 0, Rarity.COMMON);
        assertEquals(meleeOnly, magic, 0.001);
        assertEquals(160.0, magic, 0.001);
    }

    @Test
    void affixesAddSeparatelyFromBaseStats() {
        double base = CombatPower.itemCpCore("weapon", 10, 0, 0, 0, 0, 0.0, 0, Rarity.COMMON);
        double with = CombatPower.itemCpCore("weapon", 10, 0, 0, 0, 0, 0.0, 5, Rarity.COMMON);
        assertEquals(base + 5.0 * CombatPower.W_AFFIX, with, 0.001);
    }

    @Test
    void rarityCritFoldsIntoWeaponCp() {
        // MYTHIC: chance 0.10, mult 2.0 -> bonus = dmg * 0.10 * 1.0 valued at 4.0/point.
        double cp = CombatPower.weaponCritCp(100.0, Rarity.MYTHIC);
        assertEquals(100.0 * 0.10 * 1.0 * 4.0, cp, 0.001);
        assertEquals(0.0, CombatPower.weaponCritCp(100.0, Rarity.COMMON), 0.001);
        assertEquals(0.0, CombatPower.weaponCritCp(0.0, Rarity.MYTHIC), 0.001);
    }

    @Test
    void rarityCritFoldsIntoArmorCp() {
        // Armor crit = chance increment at 3.0 CP per 1%: 0.01*ordinal*100*3.0.
        assertEquals(0.0, CombatPower.armorCritCp(Rarity.COMMON), 0.001);
        assertEquals(15.0, CombatPower.armorCritCp(Rarity.MYTHIC), 0.001);
        assertEquals(6.0, CombatPower.armorCritCp(Rarity.RARE), 0.001);
    }

    @Test
    void shieldCpIsCapacityPlusRarityCrit() {
        double cp = CombatPower.itemCpCore("shield", 0, 0, 0, 0, 60, 0.0, 0, Rarity.RARE);
        assertEquals(60.0 * 1.0 + 6.0, cp, 0.001);
    }

    // ---- upgrade tracks ----

    @Test
    void upgradeTracksFollowWeights() {
        // damage +1/lvl at 4.0, defense +1/lvl at 2.0, crit +0.5%/lvl at 3.0/1%,
        // speed +0.03/lvl at 40.0, mana +5/lvl at 0.15, hearts +5/lvl at 0.5.
        assertEquals(4.0, CombatPower.upgradeCp(Map.of("damage", 1), false), 0.001);
        assertEquals(0.0, CombatPower.upgradeCp(Map.of("damage", 1), true), 0.001);
        assertEquals(4.0, CombatPower.upgradeCp(Map.of("magic_damage", 1), true), 0.001);
        assertEquals(2.0, CombatPower.upgradeCp(Map.of("defense", 1), false), 0.001);
        assertEquals(1.5, CombatPower.upgradeCp(Map.of("crit", 1), false), 0.001);
        assertEquals(1.2, CombatPower.upgradeCp(Map.of("speed", 1), false), 0.001);
        assertEquals(0.75, CombatPower.upgradeCp(Map.of("mana", 1), false), 0.001);
        assertEquals(2.5, CombatPower.upgradeCp(Map.of("hearts", 1), false), 0.001);
        assertEquals(0.0, CombatPower.upgradeCp(Map.of(), false), 0.001);
        assertEquals(0.0, CombatPower.upgradeCp(null, false), 0.001);
    }

    // ---- party weighting ----

    @Test
    void weightedPartyFavorsWeakest() {
        assertEquals(100.0, CombatPower.weightedPartyCp(List.of(100.0)), 0.001);
        // weakest 100% + next 80%: order of input must not matter.
        assertEquals(100 + 0.8 * 200, CombatPower.weightedPartyCp(List.of(200.0, 100.0)), 0.001);
        assertEquals(100 + 0.8 * 200 + 0.6 * 300 + 0.4 * 400,
                CombatPower.weightedPartyCp(List.of(400.0, 100.0, 300.0, 200.0)), 0.001);
        assertEquals(0.0, CombatPower.weightedPartyCp(List.of()), 0.001);
    }

    // ---- reference + modifier ----

    @Test
    void referenceCpCurve() {
        assertEquals(25.0, CombatPower.referenceCp(1), 0.001);
        assertEquals(73.0, CombatPower.referenceCp(5), 0.001);
        assertEquals(133.0, CombatPower.referenceCp(10), 0.001);
    }

    @Test
    void modifierIsNeutralOnCurve() {
        assertEquals(0.0, CombatPower.difficultyModifier(25.0, 1), 0.001);
    }

    @Test
    void modifierPartyReferenceUsesWeightSum() {
        // A party whose members all sit ON the reference curve must be neutral: its weighted sum
        // is 1.0/1.8/2.4/2.8 x the per-member reference (100/80/60/40 weights), and the reference
        // is scaled by the same weight sum — so ratio is always 1.0 for an on-curve group.
        assertEquals(0.0, CombatPower.difficultyModifier(25.0 * 1.8, 1, 2), 0.0001);
        assertEquals(0.0, CombatPower.difficultyModifier(25.0 * 2.4, 1, 3), 0.0001);
        assertEquals(0.0, CombatPower.difficultyModifier(25.0 * 2.8, 1, 4), 0.0001);
        // Solo flows through the same path (weight sum 1.0 -> bare reference).
        assertEquals(0.0, CombatPower.difficultyModifier(25.0, 1, 1), 0.0001);
    }

    @Test
    void modifierPartySensesOffCurveMembers() {
        // Four members each at 30 CP (20% over curve) -> ratio 1.2, then +cap.
        assertEquals(0.10, CombatPower.difficultyModifier(30.0 * 2.8, 1, 4), 0.001);
        // Four members each at 20 CP (20% under curve) -> ratio 0.8, then -cap.
        assertEquals(-0.10, CombatPower.difficultyModifier(20.0 * 2.8, 1, 4), 0.001);
        // Below-curve pair gets the intended "fairer floor" easing, not the +cap.
        assertEquals(-0.10, CombatPower.difficultyModifier(25.0 * 1.8 * 0.9, 1, 2), 0.001);
        // Mixed pair: on-curve weakest + strong strongest is still clearly above curve.
        assertEquals(0.10, CombatPower.difficultyModifier(25.0 + 0.8 * 60.0, 1, 2), 0.001);
    }

    @Test
    void modifierMemberCountAboveFourCapsReference() {
        // Weighting stops at the four-member sum; 5+ members must not inflate the reference
        // further (Dung parties cap at 4 anyway).
        assertEquals(0.0, CombatPower.difficultyModifier(25.0 * 2.8, 1, 8), 0.0001);
    }

    @Test
    void modifierCapsEarlyAtTenPercent() {
        // Way over CP on floor 1 -> +10%, way under -> -10%.
        assertEquals(0.10, CombatPower.difficultyModifier(1000.0, 1), 0.001);
        assertEquals(-0.10, CombatPower.difficultyModifier(0.0, 1), 0.001);
        // Small mismatch passes through with sensitivity 1.0.
        assertEquals(0.04, CombatPower.difficultyModifier(25.0 * 1.04, 1), 0.001);
    }

    @Test
    void modifierCapsLateAtTwentyPercent() {
        assertEquals(0.20, CombatPower.difficultyModifier(10000.0, 6), 0.001);
        assertEquals(-0.20, CombatPower.difficultyModifier(0.0, 10), 0.001);
    }

    @Test
    void modifierSplitsSeventyFiveTwentyFive() {
        double m = 0.08;
        assertEquals(0.06, CombatPower.complexityOf(m), 0.0001);
        assertEquals(0.02, CombatPower.hpDmgOf(m), 0.0001);
        assertEquals(m, CombatPower.complexityOf(m) + CombatPower.hpDmgOf(m), 0.0001);
    }

    // ---- breakdown ----

    @Test
    void breakdownTotalIsSumOfParts() {
        CombatPower.Breakdown b = CombatPower.Breakdown.of(150, 120, 30, 0, 10, 4);
        assertEquals(314.0, b.total(), 0.001);
    }
}
