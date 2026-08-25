package com.ccwolf.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.map.Terrain;
import com.ccwolf.core.map.TileMap;
import org.junit.jupiter.api.Test;

/**
 * Gas: the counter-doctrine, checked one sentence at a time.
 *
 * <p>The doctrine's line is "gas that sinks into a trench and stays — cover is no help", and
 * each test here is one clause of it. It sinks: a cloud beside dug ground drains into the
 * digging. It stays: the same dose outlives its open-ground twin. Cover is no help: a man under
 * full cover dies exactly as fast as a man in the open. And the two exemptions that make it a
 * doctrine rather than a hazard: the Regime is masked, and a hull is sealed.
 */
public class GasTest {

    private GameWorld world(long seed) {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), seed);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, false, "B");
        world.setFogEnabled(false);
        world.placeBuilding(0, BuildingType.COMMAND_POST, 8, 10, true);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        return world;
    }

    private int[] quietGrass(TileMap map) {
        for (int y = 24; y < 40; y++) {
            for (int x = 24; x < 40; x++) {
                if (map.terrain(x, y) == Terrain.GRASS && map.cover(x, y) == 0
                        && map.terrain(x + 1, y) == Terrain.GRASS && map.cover(x + 1, y) == 0) {
                    return new int[] {x, y};
                }
            }
        }
        throw new IllegalStateException("kreisau has no open grass in the middle of it");
    }

    @Test
    public void itSinksIntoTheTrench() {
        GameWorld world = world(1L);
        int[] spot = quietGrass(world.map());
        int x = spot[0];
        int y = spot[1];
        // A dug tile next to a clean one, and the cloud vented on the clean one.
        world.map().addCover(x + 1, y, 3);
        world.gas().release(x, y, GasLayer.PER_SHELL);

        int before = world.gas().at(x + 1, y);
        for (int i = 0; i < GasLayer.SETTLE_EVERY * 2 + 1; i++) {
            world.step();
        }
        assertTrue(world.gas().at(x + 1, y) > before,
                "a cloud beside dug ground drains into the digging - it is heavier than air,"
                        + " and that is the doctrine's whole idea");
    }

    @Test
    public void itStaysInTheTrenchLongerThanInTheOpen() {
        GameWorld world = world(1L);
        int[] spot = quietGrass(world.map());
        int x = spot[0];
        int y = spot[1];
        world.map().addCover(x + 1, y, 4);
        // The same dose on each, released directly so neither feeds the other.
        world.gas().raiseForTest(x, y, 4);
        world.gas().raiseForTest(x + 1, y, 4);

        int openLifetime = 0;
        int trenchLifetime = 0;
        for (int i = 0; i < 2000; i++) {
            world.step();
            if (world.gas().at(x, y) > 0) {
                openLifetime = i;
            }
            if (world.gas().at(x + 1, y) > 0) {
                trenchLifetime = i;
            }
        }
        assertTrue(trenchLifetime > openLifetime * 2,
                "the trench holds its gas at least twice as long as the open ground beside it:"
                        + " open " + openLifetime + " ticks, trench " + trenchLifetime);
    }

    @Test
    public void coverIsNoHelpAtAll() {
        GameWorld world = world(1L);
        int[] spot = quietGrass(world.map());
        Unit inTheOpen = world.spawnUnit(0, UnitType.PARTISAN, spot[0] + 0.5f, spot[1] + 0.5f);
        Unit dugIn = world.spawnUnit(0, UnitType.PARTISAN, spot[0] + 1.5f, spot[1] + 0.5f);
        world.map().addCover(spot[0] + 1, spot[1], TileMap.FULL_COVER);

        world.gas().raiseForTest(spot[0], spot[1], 4);
        world.gas().raiseForTest(spot[0] + 1, spot[1], 4);
        // Step to the first dose. The settle that runs just before it decays the open tile and
        // spares the trench, so the two doses legitimately differ - what must NOT differ is the
        // price per dose, because that is where a cover multiplier would show up.
        int doseOpen = -1;
        int doseDug = -1;
        for (int i = 0; i < GameWorld.GAS_HURT_EVERY + 1; i++) {
            world.step();
            if (doseOpen < 0 && inTheOpen.hp() < inTheOpen.type().maxHp()) {
                doseOpen = world.gas().at(spot[0], spot[1]);
                doseDug = world.gas().at(spot[0] + 1, spot[1]);
            }
        }
        int openLost = inTheOpen.type().maxHp() - inTheOpen.hp();
        int dugLost = dugIn.type().maxHp() - dugIn.hp();
        assertTrue(openLost > 0, "gas hurts");
        assertEquals(doseOpen * GameWorld.GAS_DAMAGE_PER_DOSE, openLost,
                "a man in the open pays exactly the per-dose price");
        assertEquals(doseDug * GameWorld.GAS_DAMAGE_PER_DOSE, dugLost,
                "full cover buys no discount at all - the trench is where gas pools");
    }

    @Test
    public void masksAndHullsAreTheTwoExemptions() {
        GameWorld world = world(1L);
        int[] spot = quietGrass(world.map());
        Unit soldat = world.spawnUnit(1, UnitType.SOLDAT, spot[0] + 0.5f, spot[1] + 0.5f);
        // The panzer belongs to the same player: the first draft gave it to the Resistance and
        // it shot the man whose gas immunity was being measured.
        Unit panzer = world.spawnUnit(1, UnitType.CAPTURED_PANZER, spot[0] + 1.5f,
                spot[1] + 0.5f);
        world.gas().raiseForTest(spot[0], spot[1], GasLayer.MAX_LEVEL);
        world.gas().raiseForTest(spot[0] + 1, spot[1], GasLayer.MAX_LEVEL);

        for (int i = 0; i < 60; i++) {
            world.step();
        }
        assertEquals(soldat.type().maxHp(), soldat.hp(),
                "the Regime marched into this doctrine wearing the answer");
        assertEquals(panzer.type().maxHp(), panzer.hp(), "a hull is sealed");
    }

    @Test
    public void aCleanMatchNeverTouchesTheLayer() {
        GameWorld world = world(7L);
        for (int i = 0; i < 500; i++) {
            world.step();
        }
        assertTrue(!world.gas().any(),
                "nothing in the baseline game vents gas, so the layer must stay empty -"
                        + " the determinism goldens depend on it");
    }
}
