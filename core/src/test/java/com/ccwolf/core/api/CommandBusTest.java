package com.ccwolf.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.map.MapLoader;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.sim.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the game through the public seam only — no direct calls into the simulation.
 *
 * <p>If these pass, the interface layer can be replaced wholesale without touching game rules,
 * which is the entire point of the command bus.
 */
class CommandBusTest {

    private GameWorld world;
    private CommandBus bus;
    private WorldView view;
    private Player me;

    @BeforeEach
    void setUp() {
        world = new GameWorld(openField(40, 40), 7L);
        me = world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, true, "B");
        world.setFogEnabled(false);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 34, 34, true);
        bus = new CommandBus(world);
        view = new WorldView(world, 0);
    }

    private static TileMap openField(int w, int h) {
        StringBuilder sb = new StringBuilder("name Test\nsize " + w + " " + h + "\ntiles\n");
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                sb.append('.');
            }
            sb.append('\n');
        }
        return MapLoader.parse(sb.toString());
    }

    @Test
    void aMoveCommandMovesTheUnit() {
        Unit u = world.spawnUnit(0, UnitType.PARTISAN, 5.5f, 5.5f);

        assertTrue(bus.submit(0, new PlayerCommand.Move(new int[] {u.id()}, 15, 15, false))
                .isAccepted());
        for (int i = 0; i < 600 && !u.isIdle(); i++) {
            world.step();
        }

        assertEquals(15, u.tileX());
        assertEquals(15, u.tileY());
    }

    @Test
    void commandsAgainstUnitsYouDoNotOwnAreRejected() {
        Unit theirs = world.spawnUnit(1, UnitType.SOLDAT, 30.5f, 30.5f);

        CommandResult result = bus.submit(0,
                new PlayerCommand.Move(new int[] {theirs.id()}, 5, 5, false));

        assertFalse(result.isAccepted());
        assertNotNull(result.reason());
        assertTrue(theirs.isIdle(), "an enemy unit must not take our orders");
    }

    @Test
    void attackingYourOwnSideIsRefusedWithAReason() {
        Unit mine = world.spawnUnit(0, UnitType.PARTISAN, 5.5f, 5.5f);
        Unit alsoMine = world.spawnUnit(0, UnitType.PARTISAN, 6.5f, 5.5f);

        CommandResult result = bus.submit(0,
                new PlayerCommand.Attack(new int[] {mine.id()}, alsoMine.id()));

        assertFalse(result.isAccepted());
        assertEquals("That is one of ours", result.reason());
    }

    @Test
    void theWholeBuildAndPlaceLoopRunsThroughCommands() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);

        assertTrue(bus.submit(0, new PlayerCommand.QueueBuilding(BuildingType.BARRACKS))
                .isAccepted());
        assertFalse(bus.submit(0, new PlayerCommand.PlaceBuilding(10, 6)).isAccepted(),
                "cannot place before it is built");

        for (int i = 0; i < BuildingType.BARRACKS.buildTicks() + 5; i++) {
            world.step();
        }
        assertEquals(BuildingType.BARRACKS, view.readyStructure());
        assertTrue(bus.submit(0, new PlayerCommand.PlaceBuilding(10, 6)).isAccepted());

        assertTrue(world.hasCompletedBuilding(0, BuildingType.BARRACKS));
        assertTrue(bus.submit(0, new PlayerCommand.QueueUnit(UnitType.PARTISAN)).isAccepted());
    }

    @Test
    void queueingSomethingYouCannotBuildExplainsWhy() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);

        CommandResult result = bus.submit(0, new PlayerCommand.QueueUnit(UnitType.PARTISAN));

        assertFalse(result.isAccepted());
        assertEquals("Needs Barracks", result.reason());
        assertEquals("Needs Barracks", view.blockerFor(UnitType.PARTISAN));
    }

    @Test
    void cancellingARefundsTheCreditsInFull() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);
        world.placeBuilding(0, BuildingType.BARRACKS, 10, 5, true);
        int before = view.credits();

        bus.submit(0, new PlayerCommand.QueueUnit(UnitType.PARTISAN));
        assertEquals(before - UnitType.PARTISAN.cost(), view.credits());

        assertTrue(bus.submit(0, new PlayerCommand.CancelQueue(PlayerCommand.Line.INFANTRY))
                .isAccepted());
        assertEquals(before, view.credits());
        assertFalse(bus.submit(0, new PlayerCommand.CancelQueue(PlayerCommand.Line.INFANTRY))
                .isAccepted(), "nothing left to cancel");
    }

    @Test
    void ordersAreRefusedOnceTheMatchIsOver() {
        Unit u = world.spawnUnit(0, UnitType.PARTISAN, 5.5f, 5.5f);
        for (Building b : world.buildings()) {
            b.kill();
        }
        world.step();

        assertTrue(world.isGameOver());
        CommandResult result = bus.submit(0,
                new PlayerCommand.Move(new int[] {u.id()}, 9, 9, false));
        assertFalse(result.isAccepted());
        assertEquals("The match is over", result.reason());
    }

    @Test
    void theViewNeverHandsOutAWayToChangeAnything() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);
        world.step();

        assertEquals(0, view.playerId());
        assertEquals(Faction.RESISTANCE, view.faction());
        assertTrue(view.credits() > 0);
        assertNull(view.readyStructure());
        assertTrue(view.isDiscovered(world.buildings().get(0)));
    }
}
