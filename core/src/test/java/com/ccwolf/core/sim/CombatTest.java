package com.ccwolf.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.event.GameEvent;
import com.ccwolf.core.map.MapLoader;
import com.ccwolf.core.order.AttackOrder;
import com.ccwolf.core.order.MoveOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CombatTest {

    private GameWorld world;

    @BeforeEach
    void setUp() {
        world = new GameWorld(TestMaps.openField(40, 40), 1234L);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, true, "B");
        world.setFogEnabled(false);
    }

    @Test
    void unitWalksToItsTargetAndKillsIt() {
        Unit attacker = world.spawnUnit(0, UnitType.PARTISAN, 5.5f, 5.5f);
        Unit victim = world.spawnUnit(1, UnitType.SOLDAT, 15.5f, 5.5f);
        attacker.setOrder(new AttackOrder(victim.id()));

        for (int i = 0; i < 600 && victim.isAlive(); i++) {
            world.step();
        }

        assertFalse(victim.isAlive(), "the attacker should close in and finish the job");
        assertTrue(attacker.isAlive());
        assertEquals(1, world.player(1).unitsLost());
    }

    @Test
    void attackOrderEndsWhenTheTargetDies() {
        Unit attacker = world.spawnUnit(0, UnitType.PARTISAN, 5.5f, 5.5f);
        Unit victim = world.spawnUnit(1, UnitType.SOLDAT, 7.5f, 5.5f);
        // Keep the enemy alive somewhere far away, otherwise the match ends on the kill and
        // the simulation stops stepping before the order is retired.
        world.spawnUnit(1, UnitType.SOLDAT, 35.5f, 35.5f).clearOrders();
        attacker.setOrder(new AttackOrder(victim.id()));

        for (int i = 0; i < 600 && !attacker.isIdle(); i++) {
            world.step();
        }

        assertFalse(victim.isAlive());
        assertTrue(attacker.isIdle(), "a finished attack order should leave the unit idle");
    }

    @Test
    void idleUnitsShootEnemiesThatWanderIntoRangeButDoNotChase() {
        Unit guard = world.spawnUnit(0, UnitType.PARTISAN, 5.5f, 5.5f);
        Unit intruder = world.spawnUnit(1, UnitType.SOLDAT, 7.0f, 5.5f);
        intruder.clearOrders();

        float startX = guard.x();
        for (int i = 0; i < 40; i++) {
            world.step();
        }

        assertTrue(intruder.hp() < intruder.maxHp(), "the guard should have opened fire");
        assertEquals(startX, guard.x(), 0.001f, "auto-defence must not make the unit advance");
    }

    @Test
    void rocketsBeatArmourWhileRiflesBarelyScratchIt() {
        Unit rifleman = world.spawnUnit(0, UnitType.PARTISAN, 5.5f, 5.5f);
        Unit rocketeer = world.spawnUnit(0, UnitType.ROCKETEER, 5.5f, 20.5f);
        Unit tankA = world.spawnUnit(1, UnitType.UBERSOLDAT, 8.0f, 5.5f);
        Unit tankB = world.spawnUnit(1, UnitType.UBERSOLDAT, 8.0f, 20.5f);
        tankA.clearOrders();
        tankB.clearOrders();
        rifleman.setOrder(new AttackOrder(tankA.id()));
        rocketeer.setOrder(new AttackOrder(tankB.id()));

        for (int i = 0; i < 100; i++) {
            world.step();
        }

        int riflemanDamage = tankA.maxHp() - tankA.hp();
        int rocketDamage = tankB.maxHp() - tankB.hp();
        assertTrue(rocketDamage > riflemanDamage * 3,
                "expected rockets (" + rocketDamage + ") to far outdo rifles ("
                        + riflemanDamage + ") against heavy armour");
    }

    @Test
    void turretsDefendTheirOwnGroundAndReportShots() {
        // The turret needs a generator behind it now: defences go dark in a power deficit.
        world.placeBuilding(1, BuildingType.GENERATOR, 26, 26, true);
        Building turret = world.placeBuilding(1, BuildingType.FLAK_TURRET, 20, 20, true);
        Unit intruder = world.spawnUnit(0, UnitType.PARTISAN, 15.5f, 20.5f);
        intruder.setOrder(new MoveOrder(24, 20));

        boolean sawShot = false;
        List<GameEvent> events = new ArrayList<GameEvent>();
        for (int i = 0; i < 200 && !sawShot; i++) {
            world.step();
            events.clear();
            world.drainEvents(events);
            for (GameEvent e : events) {
                if (e.type() == GameEvent.Type.SHOT_FIRED && e.entityId() == turret.id()) {
                    sawShot = true;
                }
            }
        }

        assertTrue(sawShot, "the turret should have engaged the intruder");
    }

    @Test
    void destroyingEverythingEndsTheMatch() {
        world.spawnUnit(0, UnitType.PARTISAN, 5.5f, 5.5f);
        Unit doomed = world.spawnUnit(1, UnitType.SOLDAT, 6.5f, 5.5f);
        doomed.applyDamage(doomed.maxHp(), -1, 0);

        world.step();

        assertTrue(world.isGameOver());
        assertEquals(0, world.winnerId());
        assertTrue(world.player(1).isDefeated());
    }
}
