package com.ccwolf.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.api.CommandBus;
import com.ccwolf.core.api.PlayerCommand;
import com.ccwolf.core.api.WorldView;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Hijacking, sabotage and stealth — the three things the Resistance actually wins with. */
class InfiltrationTest {

    private GameWorld world;
    private CommandBus bus;
    private WorldView mine;
    private WorldView theirs;

    @BeforeEach
    void setUp() {
        world = new GameWorld(TestMaps.withOrePatch(48, 48, 30, 6, 5), 3L);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, true, "B");
        world.setFogEnabled(false);
        world.placeBuilding(0, BuildingType.COMMAND_POST, 2, 2, true);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 42, 42, true);
        bus = new CommandBus(world);
        mine = new WorldView(world, 0);
        theirs = new WorldView(world, 1);
    }

    private void run(int ticks) {
        for (int i = 0; i < ticks; i++) {
            world.step();
        }
    }

    // --- hijack -----------------------------------------------------------------------------

    @Test
    void anInfiltratorStealsAVehicleAndIsSpentDoingIt() {
        Unit infiltrator = world.spawnUnit(0, UnitType.INFILTRATOR, 10.5f, 10.5f);
        Unit hound = world.spawnUnit(1, UnitType.PANZERHUND, 14.5f, 10.5f);
        hound.clearOrders();

        assertTrue(bus.submit(0,
                new PlayerCommand.Infiltrate(new int[] {infiltrator.id()}, hound.id()))
                .isAccepted());
        run(400);

        assertEquals(0, hound.ownerId(), "the hound should have changed sides");
        assertTrue(hound.isAlive(), "and should still be alive to fight for us");
        assertFalse(infiltrator.isAlive(), "the infiltrator is inside it now");
    }

    /**
     * A Panzerhund is more than twice an Infiltrator's speed, so hijacking one means catching
     * it while it is busy with something nearby — the ambush this test stages. Chasing a
     * hound across open ground never works, and should not.
     */
    @Test
    void aStolenVehicleForgetsItsOldOrders() {
        Unit infiltrator = world.spawnUnit(0, UnitType.INFILTRATOR, 10.5f, 10.5f);
        Unit hound = world.spawnUnit(1, UnitType.PANZERHUND, 13.5f, 10.5f);
        Unit victim = world.spawnUnit(0, UnitType.PARTISAN, 9.5f, 10.5f);
        world.issueOrder(1, hound, new com.ccwolf.core.order.AttackOrder(victim.id()));

        bus.submit(0, new PlayerCommand.Infiltrate(new int[] {infiltrator.id()}, hound.id()));
        run(400);

        assertEquals(0, hound.ownerId());
        assertTrue(hound.isIdle(),
                "a captured hound that kept its orders would drive back and kill its new owner");
        assertTrue(victim.isAlive());
    }

    @Test
    void aStolenHarvesterGoesBackToWork() {
        Unit infiltrator = world.spawnUnit(0, UnitType.INFILTRATOR, 10.5f, 10.5f);
        Unit harvester = world.spawnUnit(1, UnitType.HARVESTER, 13.5f, 10.5f);

        bus.submit(0, new PlayerCommand.Infiltrate(new int[] {infiltrator.id()}, harvester.id()));
        run(400);

        assertEquals(0, harvester.ownerId());
        assertFalse(harvester.isIdle(), "it should be harvesting for its new owner");
    }

    @Test
    void anInfiltratorCannotBoardABuilding() {
        Unit infiltrator = world.spawnUnit(0, UnitType.INFILTRATOR, 10.5f, 10.5f);
        Building theirPost = world.buildings().get(1);

        assertFalse(bus.submit(0,
                new PlayerCommand.Infiltrate(new int[] {infiltrator.id()}, theirPost.id()))
                .isAccepted());
    }

    // --- sabotage ---------------------------------------------------------------------------

    @Test
    void aSaboteurShutsAStructureDownAndItComesBack() {
        Unit saboteur = world.spawnUnit(0, UnitType.SABOTEUR, 20.5f, 20.5f);
        Building generator = world.placeBuilding(1, BuildingType.GENERATOR, 22, 20, true);
        world.step();
        assertEquals(200, world.player(1).powerProduced(),
                "command post plus generator");

        bus.submit(0, new PlayerCommand.Infiltrate(new int[] {saboteur.id()}, generator.id()));
        run(200);

        assertTrue(generator.isSabotaged(), "the generator should be off line");
        assertFalse(generator.isOperational());
        assertEquals(100, world.player(1).powerProduced(),
                "the sabotaged generator contributes nothing; the command post still runs");
        assertTrue(saboteur.isAlive(), "planting a charge is not a suicide run");

        run(20 * GameWorld.TICKS_PER_SECOND + 20);
        assertFalse(generator.isSabotaged(), "sabotage is temporary");
        assertEquals(200, world.player(1).powerProduced());
    }

    @Test
    void aSabotagedTurretHoldsItsFire() {
        Unit saboteur = world.spawnUnit(0, UnitType.SABOTEUR, 20.5f, 20.5f);
        world.placeBuilding(1, BuildingType.GENERATOR, 30, 30, true);
        Building turret = world.placeBuilding(1, BuildingType.FLAK_TURRET, 22, 20, true);
        Unit bait = world.spawnUnit(0, UnitType.PARTISAN, 24.5f, 20.5f);
        bait.clearOrders();

        bus.submit(0, new PlayerCommand.Infiltrate(new int[] {saboteur.id()}, turret.id()));
        run(120);
        int hpAfterSabotage = bait.hp();
        run(100);

        assertTrue(turret.isSabotaged());
        assertEquals(hpAfterSabotage, bait.hp(), "a sabotaged turret cannot shoot");
    }

    @Test
    void aSabotagedWalkerFreezesWhereItStands() {
        Unit saboteur = world.spawnUnit(0, UnitType.SABOTEUR, 20.5f, 20.5f);
        Unit uber = world.spawnUnit(1, UnitType.UBERSOLDAT, 23.5f, 20.5f);
        world.issueOrder(1, uber, new com.ccwolf.core.order.MoveOrder(40, 40));

        bus.submit(0, new PlayerCommand.Infiltrate(new int[] {saboteur.id()}, uber.id()));
        run(120);
        float frozenX = uber.x();
        run(80);

        assertTrue(uber.isDisabled(world.tick()));
        assertEquals(frozenX, uber.x(), 0.01f, "a frozen walker should not have moved");
    }

    // --- stealth ----------------------------------------------------------------------------

    @Test
    void aStationaryMarksmanIsInvisibleToTheEnemy() {
        Unit marksman = world.spawnUnit(0, UnitType.MARKSMAN, 20.5f, 20.5f);
        marksman.clearOrders();
        run(60);

        assertTrue(marksman.isConcealed(world.tick()));
        assertFalse(theirs.isDiscovered(marksman), "the enemy should not see a hidden marksman");
        assertTrue(mine.isDiscovered(marksman), "but we always see our own");
        assertTrue(mine.isHiddenAlly(marksman));
    }

    @Test
    void firingBreaksConcealment() {
        Unit marksman = world.spawnUnit(0, UnitType.MARKSMAN, 20.5f, 20.5f);
        marksman.clearOrders();
        run(60);
        assertTrue(marksman.isConcealed(world.tick()));

        // Spawned after settling: a marksman that had already auto-fired would still be on
        // cooldown here and the shot under test would never leave the barrel.
        Unit target = world.spawnUnit(1, UnitType.SOLDAT, 25.5f, 20.5f);
        target.clearOrders();
        assertTrue(world.tryAttack(marksman, target), "the shot should actually go off");

        assertFalse(marksman.isConcealed(world.tick()), "the shot gives the position away");
        assertTrue(theirs.isDiscovered(marksman));
    }

    @Test
    void walkingIntoAHiddenUnitRevealsIt() {
        Unit marksman = world.spawnUnit(0, UnitType.MARKSMAN, 20.5f, 20.5f);
        marksman.clearOrders();
        run(60);
        assertTrue(marksman.isConcealed(world.tick()));

        world.spawnUnit(1, UnitType.SOLDAT, 21.5f, 20.5f).clearOrders();
        run(20);

        assertFalse(marksman.isConcealed(world.tick()),
                "an enemy standing on top of it should have found it");
    }

    @Test
    void aHiddenUnitIsNotShotAtFromRange() {
        Unit marksman = world.spawnUnit(0, UnitType.MARKSMAN, 20.5f, 20.5f);
        marksman.clearOrders();
        Unit hunter = world.spawnUnit(1, UnitType.SOLDAT, 23.5f, 20.5f);
        hunter.clearOrders();
        run(80);

        assertEquals(marksman.maxHp(), marksman.hp(),
                "a concealed unit should not be taking fire");
        assertNotEquals(0, marksman.id());
    }
}
