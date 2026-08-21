package com.ccwolf.core.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.sim.GameWorld;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What a squad has to do to be worth having.
 *
 * <p>It must get where it is sent, arrive as a body rather than a queue, stop to fight rather
 * than walking through a firefight — and cost a fraction of what its members would cost acting
 * individually. The last one is the whole point, so it is asserted on the profiler's counters
 * rather than taken on trust.
 */
public class SquadMovementTest {

    private GameWorld world(long seed) {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), seed);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, false, "B");
        world.setFogEnabled(false);
        // The opponent needs something, somewhere. A player who owns nothing has already lost,
        // victory fires on the first tick, and step() then returns immediately - so the squad
        // under test simply never moves and every assertion here fails for the wrong reason.
        world.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        return world;
    }

    private Squad squadAt(GameWorld world, int owner, UnitType type, int count, float x,
            float y) {
        List<Unit> members = new ArrayList<Unit>();
        for (int i = 0; i < count; i++) {
            members.add(world.spawnUnit(owner, type, x + (i % 4) * 0.9f, y + (i / 4) * 0.9f));
        }
        return world.formSquad(owner, members);
    }

    private List<Unit> membersOf(GameWorld world, Squad squad) {
        List<Unit> out = new ArrayList<Unit>();
        for (int slot = 0; slot < squad.slotCount(); slot++) {
            int id = squad.memberAt(slot);
            if (id >= 0) {
                out.add((Unit) world.entity(id));
            }
        }
        return out;
    }

    @Test
    public void aSquadWalksToWhereItIsSent() {
        GameWorld world = world(1L);
        // y=16 is a corridor of open ground on this map.
        Squad squad = squadAt(world, 0, UnitType.PARTISAN, 8, 14.5f, 16.5f);
        world.orderSquadTo(0, squad, SquadOrder.MOVE, 36, 16);

        for (int i = 0; i < 600; i++) {
            world.step();
        }

        assertTrue(squad.anchorX() > 34f,
                "the anchor stopped at " + squad.anchorX() + " short of its destination");
        List<Unit> members = membersOf(world, squad);
        for (int i = 0; i < members.size(); i++) {
            assertTrue(members.get(i).x() > 30f,
                    "member " + i + " was left behind at " + members.get(i).x());
        }
    }

    @Test
    public void aSquadArrivesAsABodyNotAQueue() {
        GameWorld world = world(2L);
        Squad squad = squadAt(world, 0, UnitType.PARTISAN, 8, 14.5f, 16.5f);
        world.orderSquadTo(0, squad, SquadOrder.MOVE, 34, 16);

        for (int i = 0; i < 600; i++) {
            world.step();
        }

        // Every man within a formation's width of the anchor. A squad that has strung out into
        // single file has stopped being a formation, which is the failure this guards.
        List<Unit> members = membersOf(world, squad);
        for (int i = 0; i < members.size(); i++) {
            float gap = squad.anchorDistanceTo(members.get(i).x(), members.get(i).y());
            assertTrue(gap < 5f,
                    "member " + i + " is " + gap + " tiles from the anchor - the squad strung out");
        }
    }

    @Test
    public void anAttackMovingSquadStopsToFight() {
        GameWorld world = world(3L);
        Squad squad = squadAt(world, 0, UnitType.PARTISAN, 6, 14.5f, 16.5f);
        // Directly in the way.
        Unit enemy = world.spawnUnit(1, UnitType.SOLDAT, 24.5f, 16.5f);
        world.orderSquadTo(0, squad, SquadOrder.ATTACK_MOVE, 36, 16);

        for (int i = 0; i < 400; i++) {
            world.step();
            if (!enemy.isAlive()) {
                break;
            }
        }

        assertTrue(!enemy.isAlive(), "the squad walked past an enemy instead of killing it");
        assertTrue(squad.anchorX() < 30f,
                "the squad should have stopped to fight, not carried on to " + squad.anchorX());
    }

    @Test
    public void aMovingSquadDoesNotStopForEveryEnemyItPasses() {
        GameWorld world = world(4L);
        Squad squad = squadAt(world, 0, UnitType.PARTISAN, 6, 14.5f, 16.5f);
        world.spawnUnit(1, UnitType.SOLDAT, 24.5f, 19.5f);
        // MOVE, not ATTACK_MOVE: this squad has somewhere to be.
        world.orderSquadTo(0, squad, SquadOrder.MOVE, 36, 16);

        for (int i = 0; i < 600; i++) {
            world.step();
        }

        assertTrue(squad.anchorX() > 34f,
                "a move order should not have been derailed by a passing enemy, but stopped at "
                        + squad.anchorX());
    }

    @Test
    public void eightMenInASquadCostOneSearchNotEight() {
        GameWorld world = world(5L);
        Squad squad = squadAt(world, 0, UnitType.PARTISAN, 8, 14.5f, 16.5f);

        world.profiler().reset();
        world.orderSquadTo(0, squad, SquadOrder.MOVE, 36, 16);
        for (int i = 0; i < 300; i++) {
            world.step();
        }
        long squadSearches = world.profiler().astarSearches();

        // The same eight men, same journey, ordered one at a time.
        GameWorld loose = world(5L);
        List<Unit> individuals = new ArrayList<Unit>();
        for (int i = 0; i < 8; i++) {
            individuals.add(loose.spawnUnit(0, UnitType.PARTISAN,
                    14.5f + (i % 4) * 0.9f, 16.5f + (i / 4) * 0.9f));
        }
        loose.profiler().reset();
        for (int i = 0; i < individuals.size(); i++) {
            loose.issueOrder(0, individuals.get(i),
                    new com.ccwolf.core.order.MoveOrder(36, 16));
        }
        for (int i = 0; i < 300; i++) {
            loose.step();
        }
        long looseSearches = loose.profiler().astarSearches();

        assertTrue(squadSearches * 3 < looseSearches,
                "a squad of eight cost " + squadSearches + " searches against " + looseSearches
                        + " for the same men acting alone - the sharing is not working");
    }

    @Test
    public void aSquadWhoseMembersAllDieStopsBeingUpdated() {
        GameWorld world = world(6L);
        Squad squad = squadAt(world, 0, UnitType.PARTISAN, 4, 14.5f, 16.5f);
        world.orderSquadTo(0, squad, SquadOrder.MOVE, 36, 16);

        List<Unit> members = membersOf(world, squad);
        for (int i = 0; i < members.size(); i++) {
            members.get(i).kill();
        }
        for (int i = 0; i < 20; i++) {
            world.step();
        }

        assertEquals(0, world.squads().size());
    }
}
