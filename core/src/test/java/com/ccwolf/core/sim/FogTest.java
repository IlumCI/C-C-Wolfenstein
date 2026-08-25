package com.ccwolf.core.sim;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.fog.FogGrid;
import org.junit.jupiter.api.Test;

class FogTest {

    @Test
    void sightRevealsTilesAndMemoryPersistsAfterTheUnitLeaves() {
        GameWorld world = new GameWorld(TestMaps.openField(40, 40), 3L);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, true, "B");
        world.placeBuilding(1, BuildingType.COMMAND_POST, 35, 35, true);

        Unit scout = world.spawnUnit(0, UnitType.SCOUT_JEEP, 10.5f, 10.5f);
        for (int i = 0; i < 10; i++) {
            world.step();
        }

        FogGrid fog = world.fogFor(0);
        assertTrue(fog.isVisible(10, 10), "the tile the scout is standing on must be visible");
        assertFalse(fog.isExplored(30, 30), "the far side of the map is still dark");

        scout.setPosition(30.5f, 30.5f);
        for (int i = 0; i < 10; i++) {
            world.step();
        }

        assertTrue(fog.isExplored(10, 10), "we should remember where we have been");
        assertFalse(fog.isVisible(10, 10), "but not still see it");
        assertTrue(fog.isVisible(30, 30));
    }

    @Test
    void fogIsPerPlayer() {
        GameWorld world = new GameWorld(TestMaps.openField(40, 40), 3L);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, true, "B");
        world.spawnUnit(0, UnitType.PARTISAN, 5.5f, 5.5f);
        world.spawnUnit(1, UnitType.SOLDAT, 30.5f, 30.5f);

        for (int i = 0; i < 10; i++) {
            world.step();
        }

        assertTrue(world.fogFor(0).isVisible(5, 5));
        assertFalse(world.fogFor(1).isVisible(5, 5));
        assertTrue(world.fogFor(1).isVisible(30, 30));
        assertFalse(world.fogFor(0).isVisible(30, 30));
    }

    @Test
    void disablingFogRevealsEverything() {
        GameWorld world = new GameWorld(TestMaps.openField(20, 20), 3L);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.setFogEnabled(false);

        assertTrue(world.fogFor(0).isVisible(19, 19));
    }
}
