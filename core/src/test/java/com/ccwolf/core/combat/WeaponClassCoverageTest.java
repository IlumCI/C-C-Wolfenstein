package com.ccwolf.core.combat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.map.TileMap;
import org.junit.jupiter.api.Test;

/**
 * Every weapon class must have been thought about, in every table that decides what it does.
 *
 * <p>The real guard is not here — it is the static block in each of {@link DamageTable},
 * {@link Suppression} and {@link Earthworks}, which throws at class load if a row is missing.
 * That is deliberate: a table that fails open cannot be caught from outside, because a zero in
 * a {@code float[][]} and a {@code default:} that returns a legal number both look exactly like
 * an answer somebody meant. Referencing the classes from a test only proves they load.
 *
 * <p>What this test adds is the second half: the values are not merely present but sane. A row
 * of zeros passes a presence check and still means "this weapon does nothing", so each table is
 * asserted to sit inside the band its own documentation describes.
 */
public class WeaponClassCoverageTest {

    @Test
    public void everyWeaponClassHasBeenGivenARowInEveryTable() {
        // Loading the three classes is what runs their static checks. If any table is short a
        // row this throws ExceptionInInitializerError before a single assertion runs.
        for (WeaponClass weapon : WeaponClass.values()) {
            for (ArmorClass armor : ArmorClass.values()) {
                float multiplier = DamageTable.multiplier(weapon, armor);
                assertTrue(multiplier > 0f && multiplier <= 2f,
                        weapon + " vs " + armor + " has an implausible damage multiplier of "
                                + multiplier + " - a zero usually means the row was never set");
            }
            int perShot = Suppression.perShot(weapon);
            assertTrue(perShot > 0 && perShot <= Suppression.MAX,
                    weapon + " suppresses by " + perShot + ", which is outside the table's range");

            int levels = Earthworks.flattening(weapon);
            assertTrue(levels >= 0 && levels <= TileMap.MAX_COVER,
                    weapon + " strips " + levels + " levels of earth, which is not a level count");
        }
    }

    @Test
    public void coverIsWorthMoreAgainstAimedFireThanAgainstThingsThatGoOverIt() {
        // The asymmetry the whole cover model rests on, asserted so that a future weapon class
        // cannot quietly land on the wrong side of it.
        int max = TileMap.MAX_COVER;
        float rifle = Suppression.damageInCover(WeaponClass.SMALL_ARMS, max, max);
        float grenade = Suppression.damageInCover(WeaponClass.GRENADE, max, max);
        float melee = Suppression.damageInCover(WeaponClass.MELEE, max, max);

        assertTrue(rifle < grenade, "a trench should stop more rifle fire than grenade fragments");
        assertTrue(grenade < melee, "and cover should do least of all against something on top of you");
        assertTrue(melee == 1f, "cover is worth nothing in a melee");
    }

    @Test
    public void onlyThingsThatMoveSoilFlattenATrench() {
        assertTrue(Earthworks.flattening(WeaponClass.SMALL_ARMS) == 0,
                "rifle fire does not fill in a trench");
        assertTrue(Earthworks.flattening(WeaponClass.FLAME) == 0,
                "flame makes a trench a bad place to be; it does not remove it");
        assertTrue(Earthworks.movesEarth(WeaponClass.ROCKET),
                "high explosive is the counterplay to dug ground");
    }
}
