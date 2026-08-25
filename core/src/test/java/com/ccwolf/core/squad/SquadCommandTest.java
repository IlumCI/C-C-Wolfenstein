package com.ccwolf.core.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.api.CommandBus;
import com.ccwolf.core.api.PlayerCommand;
import com.ccwolf.core.api.WorldView;
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
 * Squads across the command seam.
 *
 * <p>The seam is the one place the interface and the simulation meet, and the rule there has
 * always been that the interface asks and the simulation decides. Squads add a second kind of
 * id crossing it, so these check the asking is validated as carefully as unit orders are — an
 * id that means one thing on one side and another on the other would be a live hazard.
 */
public class SquadCommandTest {

    private GameWorld world;
    private CommandBus bus;

    private void setUp(long seed) {
        world = new GameWorld(MapCatalog.load("kreisau"), seed);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, false, "B");
        world.setFogEnabled(false);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        bus = new CommandBus(world);
    }

    private Squad squadFor(int owner, UnitType type, int count, float x, float y) {
        List<Unit> members = new ArrayList<Unit>();
        for (int i = 0; i < count; i++) {
            members.add(world.spawnUnit(owner, type, x + i * 0.9f, y));
        }
        return world.formSquad(owner, members);
    }

    @Test
    public void aSquadMoveOrderIsAcceptedAndActedOn() {
        setUp(1L);
        Squad squad = squadFor(0, UnitType.PARTISAN, 6, 14.5f, 16.5f);

        assertTrue(bus.submit(0, new PlayerCommand.SquadMove(
                new int[] {squad.id()}, 34, 16)).isAccepted());
        for (int i = 0; i < 500; i++) {
            world.step();
            world.clearEvents();
        }

        assertTrue(squad.anchorX() > 30f, "the squad ignored its orders");
    }

    @Test
    public void youCannotOrderSomebodyElsesSquad() {
        setUp(2L);
        Squad theirs = squadFor(1, UnitType.SOLDAT, 4, 40.5f, 44.5f);

        assertFalse(bus.submit(0, new PlayerCommand.SquadMove(
                new int[] {theirs.id()}, 20, 20)).isAccepted());
        assertEquals(SquadOrder.HOLD, theirs.order());
    }

    @Test
    public void aSquadIdIsNotAUnitId() {
        setUp(3L);
        Squad squad = squadFor(0, UnitType.PARTISAN, 4, 14.5f, 16.5f);
        int memberId = squad.memberAt(0);

        // Handing a unit id to a squad command must be rejected rather than doing something
        // arbitrary, and vice versa. The two id spaces share a counter precisely so that this
        // is a clean miss instead of a collision.
        assertFalse(bus.submit(0, new PlayerCommand.SquadMove(
                new int[] {memberId}, 30, 30)).isAccepted());
        assertFalse(bus.submit(0, new PlayerCommand.SquadStop(
                new int[] {squad.id() + 5000})).isAccepted());
    }

    @Test
    public void attackingYourOwnSquadIsRejected() {
        setUp(4L);
        Squad squad = squadFor(0, UnitType.PARTISAN, 4, 14.5f, 16.5f);
        Unit own = world.spawnUnit(0, UnitType.SOLDAT, 20.5f, 16.5f);

        assertFalse(bus.submit(0, new PlayerCommand.SquadAttack(
                new int[] {squad.id()}, own.id())).isAccepted());
    }

    @Test
    public void changingFormationTakesEffect() {
        setUp(5L);
        Squad squad = squadFor(0, UnitType.PARTISAN, 6, 14.5f, 16.5f);
        assertEquals(Formation.WEDGE, squad.formation());

        assertTrue(bus.submit(0, new PlayerCommand.SetFormation(
                new int[] {squad.id()}, Formation.LINE)).isAccepted());
        assertEquals(Formation.LINE, squad.formation());
    }

    @Test
    public void breakingUpASquadThroughTheSeamWorks() {
        setUp(6L);
        Squad squad = squadFor(0, UnitType.PARTISAN, 6, 14.5f, 16.5f);
        int[] leaving = {squad.memberAt(4), squad.memberAt(5)};

        assertTrue(bus.submit(0, new PlayerCommand.SplitSquad(squad.id(), leaving))
                .isAccepted());
        assertEquals(4, squad.strength());
        assertEquals(2, world.squads().size());
    }

    @Test
    public void theViewShowsOnlyYourOwnSquads() {
        setUp(7L);
        squadFor(0, UnitType.PARTISAN, 4, 14.5f, 16.5f);
        squadFor(0, UnitType.PARTISAN, 4, 20.5f, 16.5f);
        squadFor(1, UnitType.SOLDAT, 4, 40.5f, 44.5f);

        WorldView mine = new WorldView(world, 0);
        WorldView theirs = new WorldView(world, 1);

        assertEquals(2, mine.mySquads().size());
        assertEquals(1, theirs.mySquads().size());
    }

    @Test
    public void theViewCanFindTheSquadAUnitBelongsTo() {
        setUp(8L);
        Squad squad = squadFor(0, UnitType.PARTISAN, 4, 14.5f, 16.5f);
        Unit member = (Unit) world.entity(squad.memberAt(2));
        Unit loner = world.spawnUnit(0, UnitType.SABOTEUR, 30.5f, 30.5f);

        WorldView view = new WorldView(world, 0);
        assertEquals(squad.id(), view.squadOf(member).id());
        assertEquals(null, view.squadOf(loner));
    }
}
