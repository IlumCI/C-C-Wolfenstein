package com.ccwolf.core.sim;

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
import com.ccwolf.core.order.HarvestOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EconomyTest {

    private GameWorld world;
    private Player player;

    @BeforeEach
    void setUp() {
        world = new GameWorld(TestMaps.withOrePatch(40, 40, 20, 5, 4), 99L);
        player = world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, true, "B");
        world.setFogEnabled(false);
        // Give the opponent something in a far corner: with nothing left alive the victory
        // check fires on the first tick and the world stops stepping.
        world.placeBuilding(1, BuildingType.COMMAND_POST, 35, 35, true);
    }

    @Test
    void queueingAUnitChargesUpFrontAndCancellingRefundsInFull() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);
        world.placeBuilding(0, BuildingType.BARRACKS, 10, 5, true);
        int before = player.credits();

        assertTrue(world.enqueueUnit(0, UnitType.PARTISAN));
        assertEquals(before - UnitType.PARTISAN.cost(), player.credits());

        world.cancelLast(0, player.infantryQueue());
        assertEquals(before, player.credits());
        assertTrue(player.infantryQueue().isEmpty());
    }

    @Test
    void unitsAppearOnceTheirBuildTimeElapses() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);
        world.placeBuilding(0, BuildingType.BARRACKS, 10, 5, true);
        world.enqueueUnit(0, UnitType.PARTISAN);

        for (int i = 0; i < UnitType.PARTISAN.buildTicks() + 5; i++) {
            world.step();
        }

        assertTrue(player.infantryQueue().isEmpty(), "the queue should have drained");
        assertEquals(1, player.unitsBuilt());
        boolean found = false;
        for (Unit u : world.units()) {
            if (u.ownerId() == 0 && u.type() == UnitType.PARTISAN) {
                found = true;
            }
        }
        assertTrue(found, "a partisan should be standing outside the barracks");
    }

    @Test
    void productionRequiresTheRightStructuresAndTechLevel() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);

        assertFalse(world.canProduce(0, UnitType.PARTISAN), "no barracks, no infantry");
        world.placeBuilding(0, BuildingType.BARRACKS, 10, 5, true);
        assertTrue(world.canProduce(0, UnitType.PARTISAN));

        // Regime-only unit, and it needs the War Works even for the Regime.
        assertFalse(world.canProduce(0, UnitType.UBERSOLDAT), "wrong faction");
        assertFalse(world.canProduce(0, UnitType.SCOUT_JEEP), "no war works");
    }

    @Test
    void structuresQueueThenWaitToBePlaced() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);
        assertTrue(world.enqueueBuilding(0, BuildingType.BARRACKS));

        for (int i = 0; i < BuildingType.BARRACKS.buildTicks() + 5; i++) {
            world.step();
        }
        assertTrue(player.structureQueue().isHeadReady(), "it should be waiting on a site");

        assertNull(world.placeQueued(0, 35, 35), "placement must stay near our own base");
        Building placed = world.placeQueued(0, 9, 5);
        assertNotNull(placed);
        assertEquals(BuildingType.BARRACKS, placed.type());
        assertTrue(player.structureQueue().isEmpty());
    }

    @Test
    void aRefineryArrivesWithItsHarvester() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);
        world.enqueueBuilding(0, BuildingType.REFINERY);
        for (int i = 0; i < BuildingType.REFINERY.buildTicks() + 5; i++) {
            world.step();
        }
        world.placeQueued(0, 9, 5);

        int harvesters = 0;
        for (Unit u : world.units()) {
            if (u.ownerId() == 0 && u.type().isHarvester()) {
                harvesters++;
            }
        }
        assertEquals(1, harvesters);
    }

    @Test
    void harvestersMineUraniumAndBankIt() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);
        world.placeBuilding(0, BuildingType.REFINERY, 14, 5, true);
        Unit harvester = world.spawnUnit(0, UnitType.HARVESTER, 15.5f, 8.5f);
        harvester.setOrder(new HarvestOrder());

        int oreBefore = world.map().totalOre();
        int creditsBefore = player.credits();
        for (int i = 0; i < 2000; i++) {
            world.step();
        }

        assertTrue(world.map().totalOre() < oreBefore, "the seam should be getting worked");
        assertTrue(player.creditsEarned() > 0, "uranium should have reached the refinery");
        assertTrue(player.credits() > creditsBefore);
    }

    @Test
    void lowPowerSlowsProductionInsteadOfStoppingIt() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);
        world.placeBuilding(0, BuildingType.BARRACKS, 10, 5, true);
        // Draw far more power than the command post produces.
        world.placeBuilding(0, BuildingType.WAR_WORKS, 10, 10, true);
        world.placeBuilding(0, BuildingType.REFINERY, 14, 14, true);
        world.placeBuilding(0, BuildingType.FLAK_TURRET, 18, 18, true);
        world.step();

        assertTrue(player.isLowPower(), "test setup should be browned out");
        assertTrue(player.powerFactor() < 1f);
        assertTrue(player.powerFactor() >= 0.25f, "a brownout must never stop production dead");
    }

    @Test
    void placementIsRejectedOnOccupiedOrDistantGround() {
        world.placeBuilding(0, BuildingType.COMMAND_POST, 5, 5, true);

        assertFalse(world.isValidPlacement(0, BuildingType.BARRACKS, 5, 5), "on top of a building");
        assertFalse(world.isValidPlacement(0, BuildingType.BARRACKS, 30, 30), "miles away");
        assertTrue(world.isValidPlacement(0, BuildingType.BARRACKS, 9, 5));
    }
}
