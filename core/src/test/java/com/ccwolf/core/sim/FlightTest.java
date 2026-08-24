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
import com.ccwolf.core.order.MoveOrder;
import org.junit.jupiter.api.Test;

/**
 * The air layer's contract, one clause per test.
 *
 * <p>Flight is a different physics, not a fast vehicle: a straight line over anything, touched
 * only by weapons that say they can reach up, holding no ground and blocked by nothing. Each of
 * these is a rule the rest of the simulation quietly depends on - the moment a rifleman can hit
 * a gyro, or a gyro can hold a front cell, the layer has collapsed back into the ground game.
 */
public class FlightTest {

    private GameWorld world(long seed) {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), seed);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, false, "B");
        world.setFogEnabled(false);
        world.placeBuilding(0, BuildingType.COMMAND_POST, 8, 10, true);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        return world;
    }

    /** Somewhere flat, empty and away from both bases, so nothing wanders into the test. */
    private int[] quietGrass(TileMap map) {
        for (int y = 20; y < 44; y++) {
            for (int x = 4; x < 28; x++) {
                boolean clear = true;
                for (int dy = -2; dy <= 2 && clear; dy++) {
                    for (int dx = -4; dx <= 4 && clear; dx++) {
                        clear = map.terrain(x + dx, y + dy) == Terrain.GRASS
                                && map.cover(x + dx, y + dy) == 0;
                    }
                }
                if (clear) {
                    return new int[] {x, y};
                }
            }
        }
        throw new IllegalStateException("kreisau has no quiet grass patch west of the river");
    }

    /** A water tile with grass either side, from the river kreisau is drawn around. */
    private int[] riverBank(TileMap map) {
        for (int y = 4; y < map.height() - 4; y++) {
            for (int x = 4; x < map.width() - 4; x++) {
                if (map.terrain(x, y) == Terrain.WATER
                        && map.terrain(x - 3, y) == Terrain.GRASS
                        && map.terrain(x + 3, y) == Terrain.GRASS) {
                    return new int[] {x, y};
                }
            }
        }
        throw new IllegalStateException("kreisau has no crossable river stretch");
    }

    @Test
    public void anAircraftFliesStraightOverTheRiver() {
        GameWorld world = world(1L);
        int[] bank = riverBank(world.map());
        Unit gyro = world.spawnUnit(0, UnitType.GYROCOPTER, bank[0] - 3 + 0.5f, bank[1] + 0.5f);
        gyro.setOrder(new MoveOrder(bank[0] + 3, bank[1]));

        boolean crossedWater = false;
        for (int i = 0; i < 400 && !gyro.isIdle(); i++) {
            world.step();
            crossedWater |= world.map().terrain(gyro.tileX(), gyro.tileY()) == Terrain.WATER;
        }
        assertEquals(bank[0] + 3, gyro.tileX(), "it arrives");
        assertTrue(crossedWater,
                "the straight line runs over the river - an aircraft that detours to a bridge"
                        + " is a jeep with a rotor painted on");
    }

    @Test
    public void onlyAntiAirWeaponsTouchIt() {
        GameWorld world = world(1L);
        Unit gyro = world.spawnUnit(0, UnitType.GYROCOPTER, 30.5f, 30.5f);
        Unit rifleman = world.spawnUnit(1, UnitType.SOLDAT, 31.5f, 30.5f);

        assertFalse(world.tryAttack(rifleman, gyro, Weapon.RIFLE),
                "a rifle cannot elevate to an aircraft");
        assertFalse(world.tryAttack(rifleman, gyro, Weapon.FLAMMENWERFER),
                "flame lives on the ground");
        assertEquals(gyro.type().maxHp(), gyro.hp(), "and it took nothing from either");

        assertTrue(world.tryAttack(rifleman, gyro, Weapon.NEST_MG),
                "an MG hoses the sky");
        assertTrue(gyro.hp() < gyro.type().maxHp(), "and that one lands");
    }

    @Test
    public void aGroundBlastPassesUnderTheRotor() {
        GameWorld world = world(1L);
        Unit gyro = world.spawnUnit(0, UnitType.GYROCOPTER, 30.5f, 30.5f);
        Unit victim = world.spawnUnit(0, UnitType.PARTISAN, 30.5f, 31.5f);
        Unit grenadier = world.spawnUnit(1, UnitType.GRENADIER, 32.5f, 31.5f);

        assertTrue(world.tryAttack(grenadier, victim, Weapon.GRENADE_BUNDLE),
                "the bundle lands on the man");
        assertTrue(victim.hp() < victim.type().maxHp(), "and hurts him");
        assertEquals(gyro.type().maxHp(), gyro.hp(),
                "the blast under the rotor touches nothing at altitude");
    }

    @Test
    public void anAircraftHoldsNoGround() {
        // Twin worlds, because the field is never zero anywhere: the bases' influence blurs
        // across the whole map. The claim is that the aircraft ADDS nothing - the field with a
        // gyro parked mid-map must be identical to the field without it.
        GameWorld bare = world(1L);
        GameWorld flown = world(1L);
        int[] spot = quietGrass(bare.map());
        flown.spawnUnit(0, UnitType.GYROCOPTER, spot[0] + 0.5f, spot[1] + 0.5f);
        for (int i = 0; i < 12; i++) {
            bare.step();
            flown.step();
        }
        int cellX = spot[0] / InfluenceGrid.CELL_TILES;
        int cellY = spot[1] / InfluenceGrid.CELL_TILES;
        assertEquals(bare.controlAtCell(0, cellX, cellY), flown.controlAtCell(0, cellX, cellY),
                0.0001f, "ground is held by what stands on it, and nothing stands here");
    }

    @Test
    public void aRiflemanOnAttackMoveIgnoresWhatHeCannotHit() {
        GameWorld world = world(1L);
        int[] spot = quietGrass(world.map());
        Unit gyro = world.spawnUnit(0, UnitType.GYROCOPTER, spot[0] + 2.5f, spot[1] + 0.5f);
        Unit soldat = world.spawnUnit(1, UnitType.SOLDAT, spot[0] + 0.5f, spot[1] + 0.5f);
        soldat.setOrder(new com.ccwolf.core.order.AttackMoveOrder(spot[0] - 3, spot[1]));

        for (int i = 0; i < 300 && !soldat.isIdle(); i++) {
            world.step();
        }
        assertEquals(spot[0] - 3, soldat.tileX(),
                "he walks his axis instead of chasing an aircraft he can never hit");
        assertEquals(gyro.type().maxHp(), gyro.hp(), "and never scratched it");
    }

    @Test
    public void theFlakTurretDoesWhatItsNameAlwaysPromised() {
        GameWorld world = world(1L);
        int[] spot = quietGrass(world.map());
        // Powered, because an unpowered defence is switched off and would pass this test's
        // negative for the wrong reason.
        world.placeBuilding(1, BuildingType.GENERATOR, spot[0] - 4, spot[1] - 2, true);
        world.placeBuilding(1, BuildingType.FLAK_TURRET, spot[0], spot[1], true);
        Unit gyro = world.spawnUnit(0, UnitType.GYROCOPTER, spot[0] + 3.5f, spot[1] + 0.5f);

        for (int i = 0; i < 100 && gyro.isAlive(); i++) {
            world.step();
        }
        assertTrue(gyro.hp() < gyro.type().maxHp(),
                "the flak turret acquires and hits an aircraft on its own");
    }
}
