package com.ccwolf.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.combat.Weapon;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.map.Terrain;
import com.ccwolf.core.map.TileMap;
import org.junit.jupiter.api.Test;

/**
 * Rounds that exist between being fired and arriving.
 *
 * <p>Everything else in this game is hitscan — the damage is decided on the tick the trigger is
 * pulled, so there has never been anything to dodge. These tests are about the consequences of
 * that stopping being true: a shell can outlive the gun, land where nobody is any more, and be
 * walked out from under.
 *
 * <p>No unit fires artillery yet. The gun here is a rifleman handed a field piece, which is
 * exactly what {@code tryBombard} is for and keeps these tests about the shell rather than about
 * the roster.
 */
public class ShellTest {

    private GameWorld world(long seed) {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), seed);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, false, "B");
        world.setFogEnabled(false);
        world.placeBuilding(0, BuildingType.COMMAND_POST, 8, 10, true);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        return world;
    }

    /** Open ground in the middle of the map, well away from either base. */
    private int[] quietGrass(TileMap map) {
        for (int y = 26; y < 40; y++) {
            for (int x = 26; x < 40; x++) {
                if (map.terrain(x, y) == Terrain.GRASS) {
                    return new int[] {x, y};
                }
            }
        }
        throw new IllegalStateException("no open grass in the middle of kreisau");
    }

    /** A unit standing in for a battery, so these tests do not depend on the roster. */
    private Unit gunAt(GameWorld world, float x, float y) {
        return world.spawnUnit(0, UnitType.PARTISAN, x, y);
    }

    @Test
    public void aShellTakesTimeToArrive() {
        GameWorld world = world(1L);
        int[] spot = quietGrass(world.map());
        Unit gun = gunAt(world, spot[0] + 0.5f, spot[1] + 8.5f);

        assertTrue(world.tryBombard(gun, spot[0], spot[1], Weapon.FELDKANONE), "the gun should fire");
        assertEquals(1, world.shells().count(), "one round in the air");

        int firedAt = world.tick();
        while (world.shells().count() > 0 && world.tick() - firedAt < 200) {
            world.step();
        }
        int flight = world.tick() - firedAt;
        assertTrue(flight > 8, "eight tiles should take real time, took " + flight + " ticks");
        assertEquals(0, world.shells().count(), "and then it lands");
    }

    @Test
    public void aLongerShotHangsInTheAirLonger() {
        // The reason artillery is answered by moving: the further the shot, the more warning
        // the target has. A fixed flight time would make close shots dodgeable and far ones not.
        int near = ShellLayer.flightTicks(0f, 0f, 4f, 0f, GameWorld.TICKS_PER_SECOND);
        int far = ShellLayer.flightTicks(0f, 0f, 14f, 0f, GameWorld.TICKS_PER_SECOND);
        assertTrue(far > near, "a shot across the map should be slower than one across a street");
        assertTrue(near >= 1, "and nothing may collapse back into being hitscan");
    }

    @Test
    public void aSquadThatMovesWalksOutFromUnderIt() {
        GameWorld world = world(2L);
        int[] spot = quietGrass(world.map());
        Unit gun = gunAt(world, spot[0] + 0.5f, spot[1] + 9.5f);

        Unit stayer = world.spawnUnit(1, UnitType.SOLDAT, spot[0] + 0.5f, spot[1] + 0.5f);
        Unit runner = world.spawnUnit(1, UnitType.SOLDAT, spot[0] + 0.5f, spot[1] + 0.5f);
        runner.setOrder(new com.ccwolf.core.order.MoveOrder(spot[0] + 8, spot[1]));

        world.tryBombard(gun, spot[0], spot[1], Weapon.FELDKANONE);
        for (int i = 0; i < 60 && world.shells().count() > 0; i++) {
            world.step();
        }

        int stayerLost = stayer.type().maxHp() - Math.max(0, stayer.hp());
        int runnerLost = runner.type().maxHp() - Math.max(0, runner.hp());
        assertTrue(stayerLost > runnerLost,
                "the man who stood still should come off worse: standing " + stayerLost
                        + " vs running " + runnerLost);
    }

    @Test
    public void aShellOutlivesTheGunThatFiredIt() {
        GameWorld world = world(3L);
        int[] spot = quietGrass(world.map());
        Unit gun = gunAt(world, spot[0] + 0.5f, spot[1] + 9.5f);
        // An unarmed victim, so that nothing in this test is shooting anything: the friendly
        // unit has to stand inside the blast radius to prove the shell spares it, and any armed
        // enemy that close would simply shoot him instead - which is what the first version of
        // this test actually measured.
        Unit victim = world.spawnUnit(1, UnitType.HARVESTER, spot[0] + 0.5f, spot[1] + 0.5f);
        Unit friend = world.spawnUnit(0, UnitType.PARTISAN, spot[0] + 1.0f, spot[1] + 0.5f);
        int friendHpBefore = friend.hp();

        world.tryBombard(gun, spot[0], spot[1], Weapon.FELDKANONE);
        // The battery is destroyed while its round is still climbing. A shell holding a
        // reference rather than two integers would be reaching into a removed entity here.
        gun.applyDamage(9999, -1, world.tick());

        for (int i = 0; i < 60 && world.shells().count() > 0; i++) {
            world.step();
        }
        assertTrue(victim.hp() < victim.type().maxHp(), "the round should still have landed");
        assertEquals(friendHpBefore, friend.hp(),
                "and it should still know whose side it was on");
    }

    @Test
    public void aSalvoBeatsAFrontageRatherThanAPoint() {
        GameWorld world = world(4L);
        int[] spot = quietGrass(world.map());
        Unit gun = gunAt(world, spot[0] + 0.5f, spot[1] + 8.5f);

        world.tryBombard(gun, spot[0], spot[1], Weapon.NEBELWERFER);
        assertEquals(Weapon.NEBELWERFER.salvo(), world.shells().count(),
                "one decision, four rounds");

        // Four distinct landing points, or it is a burst rather than a beaten zone.
        int distinct = 0;
        for (int i = 0; i < world.shells().count(); i++) {
            boolean seen = false;
            for (int j = 0; j < i; j++) {
                if (world.shells().toTileX(i) == world.shells().toTileX(j)
                        && world.shells().toTileY(i) == world.shells().toTileY(j)) {
                    seen = true;
                }
            }
            if (!seen) {
                distinct++;
            }
        }
        assertTrue(distinct >= 3, "a salvo should cover ground, hit " + distinct + " tiles");
    }

    @Test
    public void aSalvoLandsInTheSamePlaceEveryTime() {
        // No random numbers are drawn anywhere in the shell layer, and this is what says so.
        // A scatter would have to come from world.random(), which must be drawn from the same
        // number of times per tick regardless of what the simulation did - so a salvo is a
        // fixed pattern rather than a spread of misses.
        GameWorld a = world(5L);
        GameWorld b = world(5L);
        int[] spot = quietGrass(a.map());
        Unit gunA = gunAt(a, spot[0] + 0.5f, spot[1] + 8.5f);
        Unit gunB = gunAt(b, spot[0] + 0.5f, spot[1] + 8.5f);

        a.tryBombard(gunA, spot[0], spot[1], Weapon.NEBELWERFER);
        b.tryBombard(gunB, spot[0], spot[1], Weapon.NEBELWERFER);

        assertEquals(a.shells().count(), b.shells().count());
        for (int i = 0; i < a.shells().count(); i++) {
            assertEquals(a.shells().toTileX(i), b.shells().toTileX(i));
            assertEquals(a.shells().toTileY(i), b.shells().toTileY(i));
            assertEquals(a.shells().impactTick(i), b.shells().impactTick(i));
        }
    }

    @Test
    public void aGunWillNotFireIntoItsOwnDeadZone() {
        GameWorld world = world(6L);
        int[] spot = quietGrass(world.map());
        Unit gun = gunAt(world, spot[0] + 0.5f, spot[1] + 0.5f);

        assertFalse(world.tryBombard(gun, spot[0] + 1, spot[1], Weapon.FELDKANONE),
                "a field gun cannot be depressed onto its own position");
        assertEquals(0, world.shells().count());
        assertTrue(world.tryBombard(gun, spot[0] + 9, spot[1], Weapon.FELDKANONE), "but it can reach out");
    }

    @Test
    public void firingTellsTheEnemyWhereTheGunIs() {
        GameWorld world = world(7L);
        int[] spot = quietGrass(world.map());
        Unit gun = gunAt(world, spot[0] + 0.5f, spot[1] + 9.5f);

        assertFalse(world.canObserve(1, gun.tileX(), gun.tileY()),
                "nobody has been anywhere near it");

        world.tryBombard(gun, spot[0], spot[1], Weapon.FELDKANONE);

        // Both halves of counter-battery. The reveal lets the enemy see it; the sight-memory
        // stamp lets them shoot back, because indirect fire needs somewhere it may aim.
        assertTrue(gun.isRevealed(world.tick()), "a gun that fires has given itself away");
        assertTrue(world.canObserve(1, gun.tileX(), gun.tileY()),
                "and the enemy may now call fire on it");
    }

    @Test
    public void theAlienGunIgnoresCoverAndTheGroundEntirely() {
        GameWorld world = world(8L);
        int[] spot = quietGrass(world.map());
        world.map().setCover(spot[0], spot[1], TileMap.MAX_COVER);

        Unit gun = gunAt(world, spot[0] + 0.5f, spot[1] + 12.5f);
        Unit dugIn = world.spawnUnit(1, UnitType.SOLDAT, spot[0] + 0.5f, spot[1] + 0.5f);

        world.tryBombard(gun, spot[0], spot[1], Weapon.RESONANZKANONE);
        for (int i = 0; i < 90 && world.shells().count() > 0; i++) {
            world.step();
        }

        assertTrue(dugIn.hp() < dugIn.type().maxHp(), "a trench is no shelter from it");
        assertTrue(dugIn.suppression() > 0, "and what it mostly does is to nerve");
        assertEquals(0, world.map().entrenchment(spot[0], spot[1]),
                "it does not crater a trench, it erases one");
    }
}
