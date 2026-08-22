package com.ccwolf.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.api.CommandBus;
import com.ccwolf.core.api.CommandResult;
import com.ccwolf.core.api.PlayerCommand;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.map.Terrain;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.core.order.BombardOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guns choosing what to shoot, and being told what to shoot.
 *
 * <p>The rules under test are the ones that decide whether artillery is a weapon or a
 * nuisance: it must not spend a reload on one man, it must not fire at ground nobody has seen,
 * and it must give way rather than close when something gets in under it.
 */
public class BombardTest {

    private GameWorld world(long seed) {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), seed);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, false, "B");
        world.setFogEnabled(false);
        world.placeBuilding(0, BuildingType.COMMAND_POST, 8, 10, true);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        return world;
    }

    private int[] quietGrass(TileMap map) {
        for (int y = 26; y < 40; y++) {
            for (int x = 26; x < 40; x++) {
                if (map.terrain(x, y) == Terrain.GRASS) {
                    return new int[] {x, y};
                }
            }
        }
        throw new IllegalStateException("no open grass in the middle of kreisau");
    }

    /** Runs long enough for the sight-memory phase to have recorded what is where. */
    private void settle(GameWorld world) {
        for (int i = 0; i < 8; i++) {
            world.step();
        }
    }

    @Test
    public void aGunDoesNotSpendAReloadOnOneMan() {
        GameWorld world = world(1L);
        int[] spot = quietGrass(world.map());
        world.spawnUnit(0, UnitType.FELDKANONE, spot[0] + 0.5f, spot[1] + 9.5f);
        world.spawnUnit(1, UnitType.SOLDAT, spot[0] + 0.5f, spot[1] + 0.5f);

        for (int i = 0; i < 200; i++) {
            world.step();
        }
        // One man standing on his own is not a fire mission. Without this threshold a battery
        // plinks at every lone scout on the map and becomes miserable to play against.
        assertEquals(0, world.shells().count(), "a lone man should not be worth a shell");
    }

    @Test
    public void aGunFiresOnACrowd() {
        GameWorld world = world(2L);
        int[] spot = quietGrass(world.map());
        world.spawnUnit(0, UnitType.FELDKANONE, spot[0] + 0.5f, spot[1] + 9.5f);
        for (int i = 0; i < 5; i++) {
            world.spawnUnit(1, UnitType.SOLDAT, spot[0] + 0.5f + i * 0.6f, spot[1] + 0.5f);
        }
        // Somebody of ours has to have been over there. The gun's own sight is five tiles and
        // the crowd is nine away, so without a spotter this correctly refuses to fire - which
        // the first version of this test discovered by failing.
        world.spawnUnit(0, UnitType.PARTISAN, spot[0] + 2.5f, spot[1] + 2.5f);

        boolean fired = false;
        for (int i = 0; i < 200 && !fired; i++) {
            world.step();
            fired = world.shells().count() > 0;
        }
        assertTrue(fired, "five men standing together is exactly what a gun is for");
    }

    @Test
    public void aGunWillNotFireAtGroundNobodyHasSeen() {
        GameWorld world = world(3L);
        int[] spot = quietGrass(world.map());
        Unit gun = world.spawnUnit(0, UnitType.FELDKANONE, spot[0] + 0.5f, spot[1] + 9.5f);
        settle(world);

        CommandBus bus = new CommandBus(world);
        // Eleven tiles off, well inside the gun's reach and well outside anyone's sight.
        int farX = spot[0] + 11;
        CommandResult result = bus.submit(0,
                new PlayerCommand.Bombard(new int[] {gun.id()}, farX, spot[1]));

        assertFalse(result.isAccepted(), "there is nobody over there to call the shot");
        // And the refusal has to say so. A gun that silently declined would read as the order
        // not registering at all.
        assertTrue(result.reason() != null && result.reason().length() > 0);
    }

    @Test
    public void aSpotterUnlocksTheGround() {
        GameWorld world = world(4L);
        int[] spot = quietGrass(world.map());
        Unit gun = world.spawnUnit(0, UnitType.FELDKANONE, spot[0] + 0.5f, spot[1] + 9.5f);
        int farX = spot[0] + 11;
        // Somebody of ours standing on it, which is all the loose rule asks for.
        world.spawnUnit(0, UnitType.PARTISAN, farX + 0.5f, spot[1] + 0.5f);
        settle(world);

        CommandBus bus = new CommandBus(world);
        CommandResult result = bus.submit(0,
                new PlayerCommand.Bombard(new int[] {gun.id()}, farX, spot[1]));
        assertTrue(result.isAccepted(), "a man is looking straight at it: " + result.reason());
    }

    @Test
    public void aBombardOrderShellsThePlaceEvenAfterTheEnemyHasLeft() {
        GameWorld world = world(5L);
        int[] spot = quietGrass(world.map());
        Unit gun = world.spawnUnit(0, UnitType.FELDKANONE, spot[0] + 0.5f, spot[1] + 9.5f);
        settle(world);
        gun.setOrder(new BombardOrder(spot[0], spot[1]));

        for (int i = 0; i < 60 && world.shells().count() == 0; i++) {
            world.step();
        }
        // Nobody is there and it fires anyway. That is what makes a barrage a decision the
        // player makes rather than a target they click.
        assertTrue(world.shells().count() > 0, "the order names a place, not a target");
    }

    @Test
    public void aGunGivesGroundWhenSomethingGetsInsideIt() {
        GameWorld world = world(6L);
        int[] spot = quietGrass(world.map());
        Unit gun = world.spawnUnit(0, UnitType.FELDKANONE, spot[0] + 0.5f, spot[1] + 0.5f);
        settle(world);
        gun.setOrder(new BombardOrder(spot[0] + 10, spot[1]));

        // A hound right on top of it, well inside the six-tile dead zone.
        Unit hound = world.spawnUnit(1, UnitType.PANZERHUND, spot[0] + 2.5f, spot[1] + 0.5f);
        hound.setOrder(null);
        float before = gun.distanceTo(hound);

        for (int i = 0; i < 60; i++) {
            world.step();
        }
        float after = gun.distanceTo(hound);
        // Every other order in the game reads "cannot fire" as "walk closer". This one has to
        // do the opposite, or a gun walks further into the one place it cannot shoot from.
        assertTrue(after > before,
                "the gun should back away, was " + before + " now " + after);
    }

    @Test
    public void aTankWillNotAcceptABombardOrder() {
        GameWorld world = world(7L);
        int[] spot = quietGrass(world.map());
        Unit tank = world.spawnUnit(0, UnitType.CAPTURED_PANZER, spot[0] + 0.5f, spot[1] + 0.5f);
        settle(world);

        CommandBus bus = new CommandBus(world);
        CommandResult result = bus.submit(0,
                new PlayerCommand.Bombard(new int[] {tank.id()}, spot[0] + 3, spot[1]));
        assertFalse(result.isAccepted(), "a tank cannot lob anything over a hill");
    }

    @Test
    public void tappingAnEnemyWithAGunSelectedShellsItsGroundRatherThanChargingIt() {
        GameWorld world = world(8L);
        int[] spot = quietGrass(world.map());
        Unit gun = world.spawnUnit(0, UnitType.FELDKANONE, spot[0] + 0.5f, spot[1] + 9.5f);
        Unit enemy = world.spawnUnit(1, UnitType.SOLDAT, spot[0] + 0.5f, spot[1] + 0.5f);
        settle(world);

        CommandBus bus = new CommandBus(world);
        assertTrue(bus.submit(0,
                new PlayerCommand.Attack(new int[] {gun.id()}, enemy.id())).isAccepted());
        assertTrue(gun.currentOrder() instanceof BombardOrder,
                "an attack order would have marched it to point-blank range and left it silent");
    }

    @Test
    public void firingIsTheOnlyWayAGunGivesItselfAway() {
        GameWorld world = world(9L);
        int[] spot = quietGrass(world.map());
        Unit gun = world.spawnUnit(0, UnitType.FELDKANONE, spot[0] + 0.5f, spot[1] + 9.5f);
        settle(world);
        assertFalse(gun.isRevealed(world.tick()), "sitting still gives nothing away");

        gun.setOrder(new BombardOrder(spot[0], spot[1]));
        for (int i = 0; i < 60 && !gun.isRevealed(world.tick()); i++) {
            world.step();
        }
        assertTrue(gun.isRevealed(world.tick()), "the price of shooting is being seen");
    }
}
