package com.ccwolf.core.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.api.CommandBus;
import com.ccwolf.core.api.PlayerCommand;
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
 * Squads coming off the production line, and being rebuilt after they have been chewed up.
 *
 * <p>This is what makes several hundred a side reachable by a player rather than only by a
 * benchmark: one queue entry puts eight men on the field, and a worn squad is repaired instead
 * of thrown away.
 */
public class SquadProductionTest {

    private GameWorld world(long seed) {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), seed);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, false, "B");
        world.setFogEnabled(false);
        world.createStartingBase(0, 8, 10);
        // The opponent needs something, or victory fires on tick one and the world stops.
        world.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        world.placeBuilding(0, BuildingType.BARRACKS, 12, 14, true);
        return world;
    }

    private void run(GameWorld world, int ticks) {
        for (int i = 0; i < ticks; i++) {
            world.step();
            world.clearEvents();
        }
    }

    @Test
    public void oneQueueEntryPutsAWholeSquadOnTheField() {
        GameWorld world = world(1L);
        assertTrue(world.enqueueSquad(0, UnitType.PARTISAN));

        run(world, 1200);

        List<Squad> squads = world.squads().all();
        assertEquals(1, squads.size(), "one entry should have produced exactly one squad");
        assertEquals(UnitType.PARTISAN.squadSize(), squads.get(0).strength());
    }

    @Test
    public void aSquadCostsWhatItsMenWouldCost() {
        GameWorld world = world(2L);
        int before = world.player(0).credits();
        assertTrue(world.enqueueSquad(0, UnitType.PARTISAN));
        int spent = before - world.player(0).credits();

        assertEquals(UnitType.PARTISAN.cost() * UnitType.PARTISAN.squadSize(), spent,
                "a squad should cost its members' price, no more and no less");
    }

    @Test
    public void aTypeThatFightsAloneStillTrainsAsOneMan() {
        GameWorld world = world(3L);
        // Saboteurs slip away from everybody; putting them in a formation defeats the point.
        assertFalse(UnitType.SABOTEUR.formsSquads());
        assertTrue(world.enqueueSquad(0, UnitType.SABOTEUR));

        run(world, 1200);

        assertEquals(0, world.squads().size(), "a saboteur should not have formed a squad");
        assertTrue(countUnits(world, 0, UnitType.SABOTEUR) >= 1);
    }

    @Test
    public void aSquadIsNeverPutOutHalfFormed() {
        GameWorld world = world(4L);
        world.enqueueSquad(0, UnitType.PARTISAN);

        // Watch the whole production run: at no point should a squad exist below full strength,
        // because a squad that trickled out would need reinforcement logic to finish itself.
        for (int i = 0; i < 1200; i++) {
            world.step();
            world.clearEvents();
            List<Squad> squads = world.squads().all();
            for (int s = 0; s < squads.size(); s++) {
                assertEquals(UnitType.PARTISAN.squadSize(), squads.get(s).strength(),
                        "a squad appeared at partial strength on tick " + i);
            }
        }
    }

    @Test
    public void replacementsWalkToTheSquadAndFallIn() {
        GameWorld world = world(5L);
        List<Unit> members = new ArrayList<Unit>();
        for (int i = 0; i < 8; i++) {
            members.add(world.spawnUnit(0, UnitType.PARTISAN, 20.5f + i * 0.9f, 16.5f));
        }
        Squad squad = world.formSquad(0, members);
        assertNotNull(squad);

        // Three casualties.
        members.get(1).kill();
        members.get(3).kill();
        members.get(5).kill();
        run(world, 5);
        assertEquals(5, squad.strength());
        assertEquals(3, squad.shortfall());

        CommandBus bus = new CommandBus(world);
        assertTrue(bus.submit(0, new PlayerCommand.Reinforce(squad.id())).isAccepted());

        run(world, 2500);

        assertEquals(8, squad.strength(),
                "replacements should have marched up and filled the gaps");
    }

    @Test
    public void reinforcingAFullSquadIsRejected() {
        GameWorld world = world(6L);
        List<Unit> members = new ArrayList<Unit>();
        for (int i = 0; i < 4; i++) {
            members.add(world.spawnUnit(0, UnitType.PARTISAN, 20.5f + i * 0.9f, 16.5f));
        }
        Squad squad = world.formSquad(0, members);

        CommandBus bus = new CommandBus(world);
        assertFalse(bus.submit(0, new PlayerCommand.Reinforce(squad.id())).isAccepted());
    }

    @Test
    public void aReplacementWhoseSquadDiedOnTheWayBecomesAnIndividual() {
        GameWorld world = world(7L);
        List<Unit> members = new ArrayList<Unit>();
        for (int i = 0; i < 4; i++) {
            members.add(world.spawnUnit(0, UnitType.PARTISAN, 40.5f + i * 0.9f, 44.5f));
        }
        Squad squad = world.formSquad(0, members);
        members.get(0).kill();
        run(world, 5);

        CommandBus bus = new CommandBus(world);
        bus.submit(0, new PlayerCommand.Reinforce(squad.id()));

        // Wipe the squad out while the replacement is still walking to it.
        run(world, 200);
        for (int i = 1; i < members.size(); i++) {
            members.get(i).kill();
        }
        run(world, 800);

        assertEquals(0, world.squads().size());
        // The replacement is still alive and standing somewhere, not lost with the squad.
        assertTrue(countUnits(world, 0, UnitType.PARTISAN) >= 1,
                "a replacement should outlive the squad it was walking to");
    }

    private int countUnits(GameWorld world, int owner, UnitType type) {
        int count = 0;
        List<Unit> units = world.units();
        for (int i = 0; i < units.size(); i++) {
            if (units.get(i).ownerId() == owner && units.get(i).type() == type) {
                count++;
            }
        }
        return count;
    }
}
