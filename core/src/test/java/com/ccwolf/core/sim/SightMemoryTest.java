package com.ccwolf.core.sim;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.map.MapCatalog;
import org.junit.jupiter.api.Test;

/**
 * What a side remembers having seen.
 *
 * <p>Nothing reads this yet — artillery will. It is tested now, on its own, because the two
 * ways it could be quietly wrong are both invisible from a match: it could be switched off in
 * the runs that measure the game, or it could report the whole map as seen before anybody has
 * looked at anything.
 */
public class SightMemoryTest {

    private GameWorld world(boolean fog) {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), 3L);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, false, "B");
        world.setFogEnabled(fog);
        world.placeBuilding(0, BuildingType.COMMAND_POST, 8, 10, true);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        return world;
    }

    @Test
    public void nothingIsRememberedBeforeAnybodyHasLooked() {
        GameWorld world = world(true);
        // The trap this guards: a -1 sentinel would make (tick - lastSeen) small for the whole
        // opening of a match, so every tile on the map would read as recently seen until the
        // clock caught up. Artillery would have been omniscient for the first few minutes.
        assertFalse(world.canObserve(0, 40, 40),
                "the far side of the map has never been seen by anyone");
        world.step();
        assertFalse(world.canObserve(0, 40, 40),
                "and one tick later it still has not");
    }

    @Test
    public void aManSeesTheGroundHeIsStandingOn() {
        GameWorld world = world(true);
        world.spawnUnit(0, UnitType.PARTISAN, 30.5f, 30.5f);
        for (int i = 0; i < 6; i++) {
            world.step();
        }
        assertTrue(world.canObserve(0, 30, 30), "he is standing there");
        assertFalse(world.canObserve(1, 30, 30), "the other side has nobody near it");
    }

    @Test
    public void groundIsRememberedAfterTheManHasGone() {
        GameWorld world = world(true);
        int id = world.spawnUnit(0, UnitType.PARTISAN, 30.5f, 30.5f).id();
        for (int i = 0; i < 6; i++) {
            world.step();
        }
        assertTrue(world.canObserve(0, 30, 30));

        world.entity(id).applyDamage(9999, -1, world.tick());
        for (int i = 0; i < 40; i++) {
            world.step();
        }
        // The loose rule: having looked earns the right to shell for a while afterwards. That
        // is what makes a scout worth sending rather than worth parking.
        assertTrue(world.canObserve(0, 30, 30),
                "somewhere seen two seconds ago is still worth shelling");
    }

    @Test
    public void memoryFadesEventually() {
        GameWorld world = world(true);
        int id = world.spawnUnit(0, UnitType.PARTISAN, 30.5f, 30.5f).id();
        for (int i = 0; i < 6; i++) {
            world.step();
        }
        world.entity(id).applyDamage(9999, -1, world.tick());
        for (int i = 0; i < GameWorld.SPOTTING_MEMORY + 20; i++) {
            world.step();
        }
        assertFalse(world.canObserve(0, 30, 30),
                "a battery should not keep firing at a map it walked across once");
    }

    @Test
    public void aBaseSeesTheGroundInFrontOfIt() {
        GameWorld world = world(true);
        for (int i = 0; i < 6; i++) {
            world.step();
        }
        // Without buildings stamping, a base whose army is away could not call fire on somebody
        // walking up to its own gate - the one moment it most wants to.
        assertTrue(world.canObserve(0, 8, 10), "its own ground");
    }

    @Test
    public void spottingSurvivesFogBeingSwitchedOff() {
        // The reason this is not built on FogGrid. updateFog returns immediately when fog is
        // off, and the harness, the golden-digest generator and every AI test run that way - so
        // a fog-based rule would be inert in exactly the runs the balance numbers come from.
        GameWorld world = world(false);
        world.spawnUnit(0, UnitType.PARTISAN, 30.5f, 30.5f);
        for (int i = 0; i < 6; i++) {
            world.step();
        }
        assertTrue(world.canObserve(0, 30, 30), "he can still see his own boots");
        assertFalse(world.canObserve(1, 30, 30),
                "and fog being off must not hand the other side the whole map");
    }
}
