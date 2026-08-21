package com.ccwolf.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DamageTableTest {

    @Test
    void smallArmsShredInfantryAndBounceOffPlate() {
        int vsFlesh = DamageTable.damage(100, WeaponClass.SMALL_ARMS, ArmorClass.FLESH);
        int vsHeavy = DamageTable.damage(100, WeaponClass.SMALL_ARMS, ArmorClass.HEAVY);

        assertEquals(100, vsFlesh);
        assertTrue(vsHeavy < vsFlesh / 4, "rifles should be nearly useless against armour");
    }

    @Test
    void rocketsDoTheOppositeOfSmallArms() {
        int vsFlesh = DamageTable.damage(100, WeaponClass.ROCKET, ArmorClass.FLESH);
        int vsHeavy = DamageTable.damage(100, WeaponClass.ROCKET, ArmorClass.HEAVY);

        assertTrue(vsHeavy > vsFlesh * 2, "rockets are the anti-armour answer");
    }

    @Test
    void nothingIsEverFullyImmune() {
        for (WeaponClass w : WeaponClass.values()) {
            for (ArmorClass a : ArmorClass.values()) {
                assertTrue(DamageTable.damage(1, w, a) >= 1,
                        w + " vs " + a + " should always land at least 1 damage");
            }
        }
    }

    @Test
    void weaponsResolveTheirOwnDamage() {
        assertEquals(DamageTable.damage(Weapon.RIFLE.damage(), WeaponClass.SMALL_ARMS,
                ArmorClass.FLESH), Weapon.RIFLE.damageAgainst(ArmorClass.FLESH));
    }

    @Test
    void occultWeaponsIgnoreArmourEntirely() {
        for (ArmorClass a : ArmorClass.values()) {
            assertEquals(1f, DamageTable.multiplier(WeaponClass.OCCULT, a), 0.0001f);
        }
    }
}
