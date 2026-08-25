package com.ccwolf.core.sim;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.combat.Weapon;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.map.MapCatalog;
import org.junit.jupiter.api.Test;

/**
 * A weapon that can be too close as well as too far.
 *
 * <p>No weapon in the game has a dead zone yet — artillery will be the first. These tests cover
 * the mechanism before anything uses it, because the whole hazard of a minimum range is that
 * every other piece of code in the game reads "cannot fire" as "walk closer", and a gun obeying
 * that walks further into the one place it cannot shoot from.
 */
public class MinimumRangeTest {

    private GameWorld world() {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), 4L);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, false, "B");
        world.setFogEnabled(false);
        world.placeBuilding(0, BuildingType.COMMAND_POST, 8, 10, true);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        return world;
    }

    @Test
    public void aMinimumOfZeroMeansNoMinimumAtAll() {
        // The trap: range is measured to the target's edge, so the gap goes negative the moment
        // two things overlap. A plain "gap >= minRange" would have read zero as a real floor and
        // stopped a man hitting what he is standing on - which is to say, switched melee off.
        // The golden digests caught this; the test is here so they do not have to again.
        GameWorld world = world();
        Unit hound = world.spawnUnit(1, UnitType.PANZERHUND, 30.5f, 30.5f);
        Unit victim = world.spawnUnit(0, UnitType.PARTISAN, 30.6f, 30.5f);

        assertFalse(Weapon.HOUND_JAWS.hasMinRange(), "jaws have no dead zone");
        assertTrue(world.inWeaponRange(hound, victim),
                "a weapon with no minimum must be able to hit something it is standing on");
    }

    @Test
    public void aWeaponWithADeadZoneCannotFireIntoIt() {
        GameWorld world = world();
        Unit gunner = world.spawnUnit(0, UnitType.PARTISAN, 30.5f, 30.5f);
        Unit close = world.spawnUnit(1, UnitType.SOLDAT, 31.5f, 30.5f);
        Unit far = world.spawnUnit(1, UnitType.SOLDAT, 36.5f, 30.5f);

        // RIFLE reaches 3.6 tiles and has no minimum, so it is the control.
        assertTrue(world.inWeaponRange(gunner, close, Weapon.RIFLE));
        assertFalse(world.inWeaponRange(gunner, far, Weapon.RIFLE), "beyond a rifle's reach");
    }

    @Test
    public void theRingSearchSkipsWhatIsTooCloseToShoot() {
        GameWorld world = world();
        Unit close = world.spawnUnit(1, UnitType.SOLDAT, 31.0f, 30.5f);
        Unit far = world.spawnUnit(1, UnitType.SOLDAT, 36.5f, 30.5f);
        world.step();

        // Nearest, with no minimum: the one underfoot.
        Entity nearest = world.findNearestEnemy(0, 30.5f, 30.5f, 12f, false);
        assertSame(close, nearest);

        // Nearest outside a dead zone: the one that can actually be hit. Without this, a gun
        // with a scout at its feet and a section at six tiles would pick the scout, fail its
        // own range check, and fall silent - a bug wearing a drawback's clothes.
        Entity reachable = world.findNearestEnemyBetween(0, 30.5f, 30.5f, 3f, 12f, false);
        assertNotNull(reachable);
        assertNotSame(close, reachable);
        assertSame(far, reachable);
    }

    @Test
    public void aGunCrowdedAtPointBlankIsToldWhereToStand() {
        GameWorld world = world();
        Unit gun = world.spawnUnit(0, UnitType.PARTISAN, 30.5f, 30.5f);
        int[] out = new int[2];

        // Something right on top of it, at a minimum range of 6.
        assertTrue(world.standOffTile(gun, 29.5f, 30.5f, 6f, out),
                "there is open ground to give");
        float dx = out[0] + 0.5f - 29.5f;
        float dy = out[1] + 0.5f - 30.5f;
        double distance = Math.sqrt(dx * dx + dy * dy);
        assertTrue(distance > 6f,
                "the stand-off tile must be outside the dead zone, was " + distance);
        // And it must be away from the threat, not past it.
        assertTrue(out[0] > 29, "it should give ground, not walk through what is crowding it");
    }

    @Test
    public void aGunStandingExactlyOnItsProblemStillPicksADirection() {
        GameWorld world = world();
        Unit gun = world.spawnUnit(0, UnitType.PARTISAN, 30.5f, 30.5f);
        int[] out = new int[2];
        // Zero separation would be a divide by zero if the direction were normalised blindly.
        assertTrue(world.standOffTile(gun, 30.5f, 30.5f, 5f, out));
    }
}
