package com.ccwolf.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.combat.Earthworks;
import com.ccwolf.core.combat.Weapon;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.map.Terrain;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.core.order.EntrenchOrder;
import com.ccwolf.core.squad.Squad;
import com.ccwolf.core.squad.SquadOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Ground that men have worked on.
 *
 * <p>The mechanic is small — one number per tile, moved up by shovels and down by shells — so
 * these tests are about the three consequences that number is supposed to have and the two
 * exclusions that stop it being free: it shelters, it slows an attacker down, it can be taken
 * away, and it cannot be earned while shooting or while moving.
 */
public class TrenchTest {

    private GameWorld world(long seed) {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), seed);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, false, "B");
        world.setFogEnabled(false);
        world.placeBuilding(0, BuildingType.COMMAND_POST, 8, 10, true);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        return world;
    }

    /** Somewhere flat, empty and away from both bases, so nothing wanders into the test. */
    private int[] quietGrass(TileMap map) {
        for (int y = 24; y < 40; y++) {
            for (int x = 24; x < 40; x++) {
                if (map.terrain(x, y) == Terrain.GRASS && map.cover(x, y) == 0) {
                    return new int[] {x, y};
                }
            }
        }
        throw new IllegalStateException("kreisau has no open grass in the middle of it");
    }

    @Test
    public void aManLeftAloneDigsHimselfIn() {
        GameWorld world = world(1L);
        int[] spot = quietGrass(world.map());
        Unit digger = world.spawnUnit(0, UnitType.PARTISAN, spot[0] + 0.5f, spot[1] + 0.5f);
        digger.setOrder(new EntrenchOrder(spot[0], spot[1]));

        assertEquals(0, world.map().cover(spot[0], spot[1]));
        for (int i = 0; i < Earthworks.TICKS_PER_LEVEL + 5; i++) {
            world.step();
        }
        assertEquals(1, world.map().cover(spot[0], spot[1]),
                "one man, one uninterrupted stretch of digging, one level of cover");

        for (int i = 0; i < Earthworks.TICKS_PER_LEVEL * 5; i++) {
            world.step();
        }
        // FULL_COVER rather than MAX_COVER, and the difference is the point: the map now allows
        // a fifth level, but only Deep Works digs one. A man with no doctrine stops where every
        // man used to stop, in a proper trench with nothing over his head.
        assertEquals(TileMap.FULL_COVER, world.map().cover(spot[0], spot[1]),
                "left alone long enough he should end up in a proper trench");
        assertTrue(TileMap.MAX_COVER > TileMap.FULL_COVER,
                "and there should be somewhere deeper that he cannot reach");
    }

    @Test
    public void aTrenchIsSlowerToCrossThanOpenGround() {
        GameWorld world = world(2L);
        int[] spot = quietGrass(world.map());
        float open = world.map().moveCost(spot[0], spot[1]);

        world.map().addCover(spot[0], spot[1], TileMap.MAX_COVER);
        float dug = world.map().moveCost(spot[0], spot[1]);

        assertTrue(dug > open, "earthworks should cost something to cross");
        assertEquals(open + Earthworks.moveCostFor(TileMap.MAX_COVER), dug, 0.0001f);
        // This is the number that makes storming a line expensive rather than merely awkward.
        assertTrue(dug > open * 2f, "a full trench should more than double the cost of a tile");
    }

    @Test
    public void groundTheGroundAlreadyOffersIsNotChargedForTwice() {
        GameWorld world = world(3L);
        TileMap map = world.map();
        int[] spot = quietGrass(map);
        map.setTerrain(spot[0], spot[1], Terrain.RUBBLE);
        map.setCover(spot[0], spot[1], Terrain.RUBBLE.baseCover());

        assertTrue(map.cover(spot[0], spot[1]) > 0, "rubble is cover on its own");
        assertEquals(0, map.entrenchment(spot[0], spot[1]),
                "nobody dug that - it is what the ground was");
        assertEquals(Terrain.RUBBLE.moveCost(), map.moveCost(spot[0], spot[1]), 0.0001f,
                "rubble is already slow; charging for it again would make ruins impassable");
    }

    @Test
    public void aShellPutsATrenchBackInTheGround() {
        GameWorld world = world(4L);
        int[] spot = quietGrass(world.map());
        world.map().setCover(spot[0], spot[1], TileMap.MAX_COVER);
        int before = world.map().cover(spot[0], spot[1]);

        // A rocket landing on a man standing in the trench.
        Unit victim = world.spawnUnit(1, UnitType.SOLDAT, spot[0] + 0.5f, spot[1] + 0.5f);
        Unit rocketeer = world.spawnUnit(0, UnitType.ROCKETEER,
                spot[0] + 0.5f, spot[1] + 3.5f);
        world.tryAttack(rocketeer, victim);

        int after = world.map().cover(spot[0], spot[1]);
        assertTrue(after < before,
                "high explosive should flatten earthworks, was " + before + " now " + after);
        assertTrue(after <= before - Earthworks.flattening(Weapon.PANZERSCHRECK.weaponClass()) + 1,
                "a direct hit should take the full effect, not a share of it");
    }

    @Test
    public void rifleFireDoesNotFillInATrench() {
        GameWorld world = world(5L);
        int[] spot = quietGrass(world.map());
        world.map().setCover(spot[0], spot[1], TileMap.MAX_COVER);

        Unit victim = world.spawnUnit(1, UnitType.SOLDAT, spot[0] + 0.5f, spot[1] + 0.5f);
        Unit rifleman = world.spawnUnit(0, UnitType.PARTISAN, spot[0] + 0.5f, spot[1] + 3.5f);
        for (int i = 0; i < 40; i++) {
            world.tryAttack(rifleman, victim);
            world.step();
        }

        assertEquals(TileMap.MAX_COVER, world.map().cover(spot[0], spot[1]),
                "small arms make a trench a bad place to be, they do not remove it");
    }

    @Test
    public void aManUnderFireGetsNoDigging() {
        GameWorld world = world(6L);
        int[] spot = quietGrass(world.map());
        Unit digger = world.spawnUnit(0, UnitType.PARTISAN, spot[0] + 0.5f, spot[1] + 0.5f);
        digger.setOrder(new EntrenchOrder(spot[0], spot[1]));

        // Something for him to shoot at, well inside his range and no threat to the test.
        Unit enemy = world.spawnUnit(1, UnitType.SOLDAT, spot[0] + 2.5f, spot[1] + 0.5f);
        enemy.setOrder(null);

        for (int i = 0; i < Earthworks.TICKS_PER_LEVEL * 2 && enemy.isAlive(); i++) {
            world.step();
        }

        // Either he was busy shooting the whole time, or he killed the man and started
        // digging afterwards. What must not happen is a finished hole while the fight is on.
        if (enemy.isAlive()) {
            assertEquals(0, world.map().cover(spot[0], spot[1]),
                    "a man in a firefight is not also digging");
        }
    }

    @Test
    public void aSquadOrderedToDigInEndsUpInTheGround() {
        GameWorld world = world(7L);
        int[] spot = quietGrass(world.map());

        List<Unit> members = new ArrayList<Unit>();
        for (int i = 0; i < 6; i++) {
            members.add(world.spawnUnit(0, UnitType.PARTISAN,
                    spot[0] + 0.5f + i * 0.9f, spot[1] + 0.5f));
        }
        Squad squad = world.formSquad(0, members);
        world.orderSquadTo(0, squad, SquadOrder.ENTRENCH, spot[0] + 2, spot[1]);

        for (int i = 0; i < Earthworks.TICKS_PER_LEVEL * 3; i++) {
            world.step();
        }

        int dugTiles = 0;
        for (int y = spot[1] - 4; y <= spot[1] + 4; y++) {
            for (int x = spot[0] - 4; x <= spot[0] + 8; x++) {
                if (world.map().entrenchment(x, y) > 0) {
                    dugTiles++;
                }
            }
        }
        assertTrue(dugTiles >= 3,
                "six men digging for two minutes should leave a line, got " + dugTiles
                        + " dug tiles");
    }

    @Test
    public void movingOnAbandonsTheHole() {
        GameWorld world = world(8L);
        int[] spot = quietGrass(world.map());
        Unit digger = world.spawnUnit(0, UnitType.PARTISAN, spot[0] + 0.5f, spot[1] + 0.5f);

        // Most of a level's worth of work, then sent somewhere else before it is finished.
        digger.setOrder(new EntrenchOrder(spot[0], spot[1]));
        for (int i = 0; i < Earthworks.TICKS_PER_LEVEL - 10; i++) {
            world.step();
        }
        assertEquals(0, world.map().cover(spot[0], spot[1]), "not finished yet");
        assertTrue(digger.digProgress() > 0, "but work was banked");

        digger.setOrder(new EntrenchOrder(spot[0] + 4, spot[1]));
        for (int i = 0; i < 15; i++) {
            world.step();
        }
        assertEquals(0, world.map().cover(spot[0], spot[1]),
                "the half-finished hole must not complete itself after he has gone");
    }
}
