package com.ccwolf.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.order.MoveOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MovementTest {

    private GameWorld world;

    @BeforeEach
    void setUp() {
        world = new GameWorld(TestMaps.openField(40, 40), 5L);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, true, "B");
        world.setFogEnabled(false);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 35, 35, true);
    }

    @Test
    void aUnitReachesItsDestinationAndGoesIdle() {
        Unit u = world.spawnUnit(0, UnitType.PARTISAN, 2.5f, 2.5f);
        u.setOrder(new MoveOrder(20, 12));

        for (int i = 0; i < 1000 && !u.isIdle(); i++) {
            world.step();
        }

        assertTrue(u.isIdle(), "the move order should have retired");
        assertEquals(20, u.tileX());
        assertEquals(12, u.tileY());
    }

    @Test
    void aGroupOrderedAcrossTheMapAllArrivesWithoutJamming() {
        List<Unit> group = new ArrayList<Unit>();
        for (int i = 0; i < 12; i++) {
            Unit u = world.spawnUnit(0, UnitType.PARTISAN, 3.5f + (i % 4), 3.5f + (i / 4));
            u.setOrder(new MoveOrder(30, 25));
            group.add(u);
        }

        for (int i = 0; i < 2000; i++) {
            world.step();
        }

        for (Unit u : group) {
            assertTrue(u.distanceTo(30.5f, 25.5f) < 4f,
                    u.displayName() + " stalled at " + u.x() + "," + u.y());
        }
    }

    @Test
    void unitsDoNotEndUpStackedOnTopOfEachOther() {
        List<Unit> group = new ArrayList<Unit>();
        for (int i = 0; i < 8; i++) {
            group.add(world.spawnUnit(0, UnitType.PARTISAN, 10.5f, 10.5f + i * 0.01f));
        }

        for (int i = 0; i < 100; i++) {
            world.step();
        }

        for (int i = 0; i < group.size(); i++) {
            for (int j = i + 1; j < group.size(); j++) {
                float d = group.get(i).distanceTo(group.get(j));
                assertTrue(d > 0.2f, "separation should have pushed units apart, got " + d);
            }
        }
    }

    @Test
    void unitsPathAroundStructuresRatherThanThroughThem() {
        // A wall of structures with one gap.
        for (int y = 5; y < 20; y += 2) {
            if (y == 13) {
                continue;
            }
            world.placeBuilding(0, BuildingType.GENERATOR, 15, y, true);
        }
        Unit u = world.spawnUnit(0, UnitType.PARTISAN, 10.5f, 12.5f);
        u.setOrder(new MoveOrder(25, 12));

        for (int i = 0; i < 1500 && !u.isIdle(); i++) {
            world.step();
            assertTrue(!world.grid().isBlocked(u.tileX(), u.tileY()),
                    "a unit must never stand inside a structure");
        }

        assertTrue(u.x() > 20f, "the unit should have found its way through the gap");
    }

    @Test
    void aUnitCaughtUnderANewStructureIsPushedClear() {
        Unit u = world.spawnUnit(0, UnitType.PARTISAN, 10.5f, 10.5f);
        world.placeBuilding(0, BuildingType.COMMAND_POST, 9, 9, true);

        assertTrue(!world.grid().isBlocked(u.tileX(), u.tileY()),
                "the unit should have been evicted from the footprint");
    }

    @Test
    void spawningNeverStacksUnitsOnOneTile() {
        for (int i = 0; i < 10; i++) {
            world.spawnUnitNear(0, UnitType.PARTISAN, 20, 20);
        }

        for (int i = 0; i < world.units().size(); i++) {
            for (int j = i + 1; j < world.units().size(); j++) {
                Unit a = world.units().get(i);
                Unit b = world.units().get(j);
                assertTrue(a.tileX() != b.tileX() || a.tileY() != b.tileY(),
                        "two units spawned on the same tile");
            }
        }
    }
}
