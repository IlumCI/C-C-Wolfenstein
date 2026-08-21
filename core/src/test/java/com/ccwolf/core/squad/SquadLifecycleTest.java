package com.ccwolf.core.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.order.MoveOrder;
import com.ccwolf.core.sim.GameWorld;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The ways a squad can lose a member, and what has to happen each time.
 *
 * <p>Membership is held in two places on purpose — an id on the unit, a roster on the squad —
 * so every one of these is a chance for the two to disagree. A squad holding the id of a unit
 * that no longer exists is the bug this whole file exists to prevent.
 */
public class SquadLifecycleTest {

    private GameWorld world(long seed) {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), seed);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, false, "B");
        return world;
    }

    private List<Unit> spawnSquadMembers(GameWorld world, int owner, UnitType type, int count,
            float x, float y) {
        List<Unit> members = new ArrayList<Unit>();
        for (int i = 0; i < count; i++) {
            members.add(world.spawnUnit(owner, type, x + i * 0.9f, y));
        }
        return members;
    }

    @Test
    public void formingASquadPutsEveryoneInASlot() {
        GameWorld world = world(1L);
        List<Unit> members = spawnSquadMembers(world, 0, UnitType.PARTISAN, 6, 20.5f, 20.5f);

        Squad squad = world.formSquad(0, members);
        assertNotNull(squad);
        assertEquals(6, squad.strength());
        for (int i = 0; i < members.size(); i++) {
            assertEquals(squad.id(), members.get(i).squadId());
            assertEquals(i, members.get(i).squadSlot());
        }
    }

    @Test
    public void squadIdsNeverCollideWithEntityIds() {
        GameWorld world = world(2L);
        List<Unit> members = spawnSquadMembers(world, 0, UnitType.PARTISAN, 4, 20.5f, 20.5f);
        Squad squad = world.formSquad(0, members);

        // Squad ids and entity ids cross the same seam in the command layer, so an id that
        // means one thing to one side and another to the other would be a live hazard.
        assertNull(world.entity(squad.id()),
                "a squad id was also a live entity id");
    }

    @Test
    public void aDeadMemberLeavesTheSquadButTheSquadKeepsItsShape() {
        GameWorld world = world(3L);
        List<Unit> members = spawnSquadMembers(world, 0, UnitType.PARTISAN, 6, 20.5f, 20.5f);
        Squad squad = world.formSquad(0, members);

        Unit middle = members.get(2);
        middle.kill();
        world.step();

        assertEquals(5, squad.strength());
        assertFalse(squad.contains(middle.id()));
        // The men either side keep their slots; the line thins rather than shuffling up.
        assertEquals(1, squad.slotOf(members.get(1).id()));
        assertEquals(3, squad.slotOf(members.get(3).id()));
    }

    @Test
    public void theLastCasualtyRemovesTheSquadEntirely() {
        GameWorld world = world(4L);
        List<Unit> members = spawnSquadMembers(world, 0, UnitType.PARTISAN, 3, 20.5f, 20.5f);
        Squad squad = world.formSquad(0, members);

        for (int i = 0; i < members.size(); i++) {
            members.get(i).kill();
        }
        world.step();

        assertNull(world.squads().byId(squad.id()));
        assertEquals(0, world.squads().size());
    }

    @Test
    public void givingAMemberItsOwnOrderTakesItOutOfTheSquad() {
        GameWorld world = world(5L);
        List<Unit> members = spawnSquadMembers(world, 0, UnitType.PARTISAN, 5, 20.5f, 20.5f);
        Squad squad = world.formSquad(0, members);

        Unit loner = members.get(1);
        world.issueOrder(0, loner, new MoveOrder(30, 30));

        assertFalse(loner.isInSquad(), "an individual order should have broken him out");
        assertFalse(squad.contains(loner.id()));
        assertEquals(4, squad.strength());
        // Everyone else is untouched.
        assertTrue(members.get(0).isInSquad());
        assertTrue(members.get(4).isInSquad());
    }

    @Test
    public void aHijackedMemberLeavesItsOldSquad() {
        // The most easily forgotten path in the whole feature: ownership changes under a unit
        // that is standing in somebody else's formation.
        GameWorld world = world(6L);
        List<Unit> members = spawnSquadMembers(world, 1, UnitType.SOLDAT, 4, 20.5f, 20.5f);
        Squad squad = world.formSquad(1, members);
        Unit stolen = members.get(0);

        world.transferOwnership(stolen, 0);

        assertFalse(stolen.isInSquad(), "a unit that changed sides is still in its old squad");
        assertFalse(squad.contains(stolen.id()));
        assertEquals(3, squad.strength());
        assertEquals(1, squad.ownerId(), "the squad itself should not have changed hands");
    }

    @Test
    public void splittingTwoOrMoreMakesANewSquad() {
        GameWorld world = world(7L);
        List<Unit> members = spawnSquadMembers(world, 0, UnitType.PARTISAN, 6, 20.5f, 20.5f);
        Squad original = world.formSquad(0, members);

        Squad broken = world.splitSquad(original,
                Arrays.asList(members.get(4), members.get(5)));

        assertNotNull(broken);
        assertEquals(2, broken.strength());
        assertEquals(4, original.strength());
        assertTrue(broken.id() != original.id());
        assertEquals(2, world.squads().size());
    }

    @Test
    public void splittingASingleManMakesHimAnIndividual() {
        GameWorld world = world(8L);
        List<Unit> members = spawnSquadMembers(world, 0, UnitType.PARTISAN, 5, 20.5f, 20.5f);
        Squad original = world.formSquad(0, members);

        Squad broken = world.splitSquad(original, Arrays.asList(members.get(3)));

        assertNull(broken, "one man is not a squad");
        assertFalse(members.get(3).isInSquad());
        assertEquals(4, original.strength());
        assertEquals(1, world.squads().size());
    }

    @Test
    public void aSquadIsNeverMixedType() {
        GameWorld world = world(9L);
        List<Unit> mixed = new ArrayList<Unit>();
        mixed.add(world.spawnUnit(0, UnitType.PARTISAN, 20.5f, 20.5f));
        mixed.add(world.spawnUnit(0, UnitType.PARTISAN, 21.5f, 20.5f));
        mixed.add(world.spawnUnit(0, UnitType.ROCKETEER, 22.5f, 20.5f));

        Squad squad = world.formSquad(0, mixed);

        assertEquals(2, squad.strength(), "the rocketeer should not have joined a rifle squad");
        assertEquals(UnitType.PARTISAN, squad.type());
        assertFalse(mixed.get(2).isInSquad());
    }

    @Test
    public void aBadlyReducedSquadClosesItsGaps() {
        GameWorld world = world(10L);
        List<Unit> members = spawnSquadMembers(world, 0, UnitType.PARTISAN, 8, 20.5f, 20.5f);
        Squad squad = world.formSquad(0, members);

        // Down to three of eight, with the survivors scattered across the slots.
        for (int i : new int[] {0, 2, 4, 6, 7}) {
            members.get(i).kill();
        }
        world.step();

        assertTrue(squad.shouldReform(), "three of eight should be worth re-forming");
        squad.reform();
        assertEquals(0, squad.slotOf(members.get(1).id()));
        assertEquals(1, squad.slotOf(members.get(3).id()));
        assertEquals(2, squad.slotOf(members.get(5).id()));
    }
}
