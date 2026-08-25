package com.ccwolf.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.api.CommandBus;
import com.ccwolf.core.api.PlayerCommand;
import com.ccwolf.core.api.WorldView;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.event.GameEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Selling, repairing, primary factories and power blackouts — the base-management loop. */
class BaseManagementTest {

    private GameWorld world;
    private CommandBus bus;
    private WorldView view;
    private Player me;

    @BeforeEach
    void setUp() {
        world = new GameWorld(TestMaps.openField(40, 40), 11L);
        me = world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, true, "B");
        world.setFogEnabled(false);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 34, 34, true);
        bus = new CommandBus(world);
        view = new WorldView(world, 0);
    }

    // --- selling --------------------------------------------------------------------------

    @Test
    void sellingAStructureRefundsHalfAndFreesTheGround() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);
        Building barracks = world.placeBuilding(0, BuildingType.BARRACKS, 10, 5, true);
        int before = me.credits();

        assertTrue(bus.submit(0, new PlayerCommand.Sell(barracks.id())).isAccepted());
        world.step();

        assertEquals(before + BuildingType.BARRACKS.cost() / 2, me.credits());
        assertFalse(barracks.isAlive());
        assertFalse(world.grid().isBlocked(10, 5), "the footprint should be walkable again");
    }

    @Test
    void aDamagedStructureIsWorthLess() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);
        Building barracks = world.placeBuilding(0, BuildingType.BARRACKS, 10, 5, true);
        barracks.applyDamage(barracks.maxHp() / 2, -1, 1);
        int before = me.credits();

        bus.submit(0, new PlayerCommand.Sell(barracks.id()));

        int refund = me.credits() - before;
        assertTrue(refund > 0);
        assertTrue(refund < BuildingType.BARRACKS.cost() / 2,
                "a half-wrecked structure should not pay out like an intact one");
    }

    @Test
    void youCannotSellSomeoneElsesBase() {
        Building theirs = world.buildings().get(0);
        assertFalse(bus.submit(0, new PlayerCommand.Sell(theirs.id())).isAccepted());
        assertTrue(theirs.isAlive());
    }

    @Test
    void sellingReportsItSoTheInterfaceCanReact() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);
        Building barracks = world.placeBuilding(0, BuildingType.BARRACKS, 10, 5, true);
        world.clearEvents();

        bus.submit(0, new PlayerCommand.Sell(barracks.id()));

        List<GameEvent> events = world.drainEvents(new ArrayList<GameEvent>());
        boolean sold = false;
        for (GameEvent e : events) {
            if (e.type() == GameEvent.Type.BUILDING_SOLD && e.entityId() == barracks.id()) {
                sold = true;
                assertTrue(e.amount() > 0, "the event should carry the refund");
            }
        }
        assertTrue(sold);
    }

    // --- repairing ------------------------------------------------------------------------

    @Test
    void repairingMendsAStructureAndChargesForIt() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);
        Building barracks = world.placeBuilding(0, BuildingType.BARRACKS, 10, 5, true);
        barracks.applyDamage(400, -1, 1);
        int damagedHp = barracks.hp();
        int creditsBefore = me.credits();

        assertTrue(bus.submit(0, new PlayerCommand.Repair(barracks.id(), true)).isAccepted());
        for (int i = 0; i < 1500 && barracks.hp() < barracks.maxHp(); i++) {
            world.step();
        }

        assertEquals(barracks.maxHp(), barracks.hp());
        assertTrue(me.credits() < creditsBefore, "repairs are not free");
        assertTrue(me.creditsSpentOnRepairs() > 0);
        assertFalse(barracks.isRepairing(), "it should switch itself off when whole");
        assertTrue(damagedHp < barracks.maxHp());
    }

    @Test
    void repairsStopWhenTheMoneyRunsOut() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);
        Building barracks = world.placeBuilding(0, BuildingType.BARRACKS, 10, 5, true);
        barracks.applyDamage(600, -1, 1);
        me.spend(me.credits()); // Broke.

        bus.submit(0, new PlayerCommand.Repair(barracks.id(), true));
        int hpBefore = barracks.hp();
        for (int i = 0; i < 200; i++) {
            world.step();
        }

        assertEquals(hpBefore, barracks.hp(), "no money, no repairs");
        assertFalse(barracks.isRepairing());
        assertEquals(0, me.credits(), "and certainly no debt");
    }

    @Test
    void repairCostScalesWithTheDamageDone() {
        int small = GameWorld.repairCost(BuildingType.BARRACKS, 10);
        int large = GameWorld.repairCost(BuildingType.BARRACKS, 400);
        assertTrue(large > small);
        assertTrue(small >= 1, "even a scratch costs something");
    }

    // --- primary factory ------------------------------------------------------------------

    @Test
    void newUnitsComeOutOfTheChosenFactory() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);
        world.placeBuilding(0, BuildingType.BARRACKS, 9, 5, true);
        Building far = world.placeBuilding(0, BuildingType.BARRACKS, 30, 20, true);

        assertTrue(bus.submit(0, new PlayerCommand.SetPrimary(far.id())).isAccepted());
        assertTrue(view.isPrimary(far));

        bus.submit(0, new PlayerCommand.QueueUnit(UnitType.PARTISAN));
        for (int i = 0; i < UnitType.PARTISAN.buildTicks() + 10; i++) {
            world.step();
        }

        Unit trained = null;
        for (Unit u : world.units()) {
            if (u.type() == UnitType.PARTISAN) {
                trained = u;
            }
        }
        assertNotNull(trained);
        assertTrue(trained.distanceTo(far) < 6f,
                "the recruit should appear at the primary barracks, not the other one");
    }

    @Test
    void losingThePrimaryFallsBackToWhateverIsStanding() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);
        Building spare = world.placeBuilding(0, BuildingType.BARRACKS, 9, 5, true);
        Building chosen = world.placeBuilding(0, BuildingType.BARRACKS, 30, 20, true);
        bus.submit(0, new PlayerCommand.SetPrimary(chosen.id()));

        chosen.kill();
        world.step();
        bus.submit(0, new PlayerCommand.QueueUnit(UnitType.PARTISAN));
        for (int i = 0; i < UnitType.PARTISAN.buildTicks() + 10; i++) {
            world.step();
        }

        assertEquals(1, me.unitsBuilt(), "production must not stall on a dead primary");
        assertTrue(spare.isAlive());
    }

    @Test
    void onlyFactoriesCanBePrimary() {
        Building post = world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);
        assertFalse(bus.submit(0, new PlayerCommand.SetPrimary(post.id())).isAccepted());
    }

    // --- power ----------------------------------------------------------------------------

    @Test
    void defencesGoOfflineInAPowerDeficitAndComeBackWithAGenerator() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);
        Building turret = world.placeBuilding(0, BuildingType.FLAK_TURRET, 12, 5, true);
        // Command post makes 100; pile on draw until the base browns out.
        world.placeBuilding(0, BuildingType.WAR_WORKS, 14, 10, true);
        world.placeBuilding(0, BuildingType.REFINERY, 20, 10, true);
        world.step();

        assertTrue(me.isLowPower());
        assertFalse(turret.isPowered());
        assertNull(turret.weapon(), "an unpowered turret cannot shoot");

        world.placeBuilding(0, BuildingType.GENERATOR, 24, 5, true);
        world.placeBuilding(0, BuildingType.GENERATOR, 27, 5, true);
        world.step();

        assertFalse(me.isLowPower());
        assertTrue(turret.isPowered());
        assertNotNull(turret.weapon());
    }

    @Test
    void anUnpoweredTurretHoldsItsFire() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);
        Building turret = world.placeBuilding(0, BuildingType.FLAK_TURRET, 12, 5, true);
        world.placeBuilding(0, BuildingType.WAR_WORKS, 14, 10, true);
        world.placeBuilding(0, BuildingType.REFINERY, 20, 10, true);
        Unit intruder = world.spawnUnit(1, UnitType.SOLDAT, 13.5f, 6.5f);
        intruder.clearOrders();

        for (int i = 0; i < 100; i++) {
            world.step();
        }

        assertFalse(turret.isPowered());
        assertEquals(intruder.maxHp(), intruder.hp(),
                "a blacked-out turret should not have fired a shot");
    }
}
