package com.ccwolf.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.order.AttackOrder;
import com.ccwolf.core.order.MoveOrder;
import org.junit.jupiter.api.Test;

/**
 * Holds pathfinding to a budget, not just to correctness.
 *
 * <p>A* was never wrong, it was expensive in ways that had nothing to do with the problem being
 * solved: chasers threw their route away every time the quarry crossed a tile boundary, and a
 * unit wedged behind a building discarded a whole cross-map route to get round an obstruction
 * one building wide. Both cost thousands of node expansions to answer questions that were
 * already answered.
 *
 * <p>These assert on the profiler's counters rather than on time. The counters are a function
 * of the seed, so they are reproducible; a stopwatch is not.
 */
public class PathfindingCostTest {

    @Test
    public void chasingAMovingTargetDoesNotRepathEveryTile() {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), 7L);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, false, "B");

        Unit runner = world.spawnUnit(1, UnitType.SOLDAT, 12.5f, 20.5f);
        Unit chaser = world.spawnUnit(0, UnitType.PARTISAN, 40.5f, 20.5f);
        // Out of weapon range and running, so the chaser spends the whole test pursuing.
        runner.setOrder(new MoveOrder(12, 45));
        chaser.setOrder(new AttackOrder(runner.id()));

        world.profiler().reset();
        for (int i = 0; i < 300; i++) {
            world.step();
        }

        long searches = world.profiler().astarSearches();
        // The quarry crosses far more than ten tiles in fifteen seconds. Before the aim point
        // was allowed to lag, that meant a full search for each of them.
        assertTrue(searches < 20,
                "chasing cost " + searches + " full searches - the aim point is not lagging");
    }

    @Test
    public void aBlockedRouteIsDetouredRatherThanDiscarded() {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), 8L);
        world.addPlayer(Faction.RESISTANCE, false, "A");

        // y=16 is a corridor of open ground from x=10 to x=40 on this map. Picked
        // deliberately: the first attempt at this test walled a column that turned out to be
        // river, so nothing was ever built and the walker routed round water instead.
        Unit walker = world.spawnUnit(0, UnitType.PARTISAN, 14.5f, 16.5f);
        walker.setOrder(new MoveOrder(38, 16));

        // Let it commit to a route, then drop a wall across it.
        for (int i = 0; i < 20; i++) {
            world.step();
        }
        world.profiler().reset();

        // Wall the whole column the route has to cross. Guessing five tiles around the straight
        // line was not enough the first time: A* had already arced the route north around
        // terrain, so the wall went up beside the path rather than across it and the walker
        // sailed past without needing to do anything.
        int built = 0;
        for (int y = 8; y <= 24; y++) {
            if (world.placeBuilding(0, BuildingType.MG_NEST, 26, y, true) != null) {
                built++;
            }
        }
        assertTrue(built >= 10, "the wall did not go up - only " + built + " placed");

        for (int i = 0; i < 400; i++) {
            world.step();
        }

        assertTrue(world.profiler().detours() > 0,
                "a route blocked mid-journey should have been detoured around");
        assertEquals(0L, world.profiler().stuckRepaths(),
                "a detour should have handled it without discarding the whole route");
        assertTrue(walker.x() > 36f,
                "the walker should have got past the wall, but stopped at " + walker.x());
    }

    @Test
    public void aDetourStillProducesAWalkableRoute() {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), 9L);
        world.addPlayer(Faction.RESISTANCE, false, "A");

        // A press of units all crossing the same ground: plenty of wedging, plenty of detours.
        Unit[] crowd = new Unit[24];
        for (int i = 0; i < crowd.length; i++) {
            crowd[i] = world.spawnUnit(0, UnitType.PARTISAN,
                    18.5f + (i % 6) * 0.5f, 28.5f + (i / 6) * 0.5f);
            crowd[i].setOrder(new MoveOrder(44, 30));
        }

        for (int i = 0; i < 600; i++) {
            world.step();
        }

        // Splicing a detour into an existing route is the kind of thing that quietly produces a
        // path with a hole in it, which shows up as units that never arrive.
        int arrived = 0;
        for (int i = 0; i < crowd.length; i++) {
            if (crowd[i].x() > 40f) {
                arrived++;
            }
        }
        assertTrue(arrived >= crowd.length - 2,
                "only " + arrived + " of " + crowd.length + " crossed the map");
    }
}
