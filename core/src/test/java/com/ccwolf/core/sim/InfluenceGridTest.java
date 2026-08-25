package com.ccwolf.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.map.TileMap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Who holds what.
 *
 * <p>These carry all the coverage there is. Nothing reads the influence field yet, so a grid
 * that quietly computed zero everywhere would pass the determinism gate perfectly and ship dead
 * — the digest only sees consequences, and a field nobody consults has none. Every assertion
 * here is on an actual number for that reason.
 */
public class InfluenceGridTest {

    private GameWorld world(long seed) {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), seed);
        world.addPlayer(Faction.RESISTANCE, false, "A");
        world.addPlayer(Faction.REGIME, false, "B");
        world.setFogEnabled(false);
        return world;
    }

    /** Far enough for the influence phase to have run at least once. */
    private void settle(GameWorld world) {
        for (int i = 0; i < 8; i++) {
            world.step();
        }
    }

    /** Both sides need something standing or the match ends before the first influence pass. */
    private GameWorld withBases(long seed) {
        GameWorld world = world(seed);
        world.placeBuilding(0, BuildingType.COMMAND_POST, 6, 8, true);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 49, 46, true);
        return world;
    }

    /**
     * What one thing adds to the ground its side holds.
     *
     * <p>Measured as a difference between two otherwise identical worlds rather than as an
     * absolute reading, because influence reaches eight cells and both command posts therefore
     * contribute something almost everywhere. Asking "is the field zero here" answers a question
     * about the bases, not about the thing under test.
     */
    private interface Setup {
        void apply(GameWorld world);
    }

    private float contribution(long seed, Setup setup, int tileX, int tileY) {
        GameWorld without = withBases(seed);
        settle(without);
        GameWorld with = withBases(seed);
        setup.apply(with);
        settle(with);
        return with.influence(0, tileX, tileY) - without.influence(0, tileX, tileY);
    }

    @Test
    public void aManHoldsTheGroundHeIsOnAndLessOfWhatIsFurtherOff() {
        Setup oneMan = new Setup() {
            @Override
            public void apply(GameWorld w) {
                w.spawnUnit(0, UnitType.PARTISAN, 28.5f, 30.5f);
            }
        };
        float here = contribution(1L, oneMan, 28, 30);
        float near = contribution(1L, oneMan, 36, 30);
        float far = contribution(1L, oneMan, 48, 30);

        assertTrue(here > 0f, "he holds the ground he is standing on");
        assertTrue(near < here, "and less of the ground further off, was " + near + " vs " + here);
        assertTrue(far < near, "and less again beyond that");
    }

    @Test
    public void aFrontExistsBetweenTwoBasesFromTheFirstTick() {
        // The failure this pins is total rather than cosmetic. The two spawn points on this map
        // are fourteen cells apart; a narrow kernel gives each base a field three cells wide
        // with eleven cells of exact zero between, so control is zero everywhere in the middle
        // and there is no sign change to find. The front would simply not exist until the
        // armies touched.
        GameWorld world = world(2L);
        world.placeBuilding(0, BuildingType.COMMAND_POST, 6, 8, true);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 49, 46, true);
        world.step();

        boolean sawMine = false;
        boolean sawTheirs = false;
        for (int cell = 0; cell < world.controlCellsAcross(); cell++) {
            float control = world.controlAtCell(0, cell, cell);
            if (control > 0f) {
                sawMine = true;
            }
            if (control < 0f) {
                sawTheirs = true;
            }
        }
        assertTrue(sawMine && sawTheirs,
                "the diagonal between two bases must change hands somewhere");

        boolean front = false;
        for (int y = 0; y < world.controlCellsDown() && !front; y++) {
            for (int x = 0; x < world.controlCellsAcross(); x++) {
                if (world.isFrontCell(0, x, y)) {
                    front = true;
                    break;
                }
            }
        }
        assertTrue(front, "and there must be a front cell on it");
    }

    @Test
    public void aCommandPostHoldsMoreGroundThanAnMgNest() {
        // Pinned because the obvious weight - cost - makes this false. The Command Post costs
        // nothing to build, so a cost-weighted field reads the most important building on the
        // map as neutral ground and puts the strongpoint on the tank factory instead.
        // Dry land: tile 30,30 is the river, and a building that fails to place holds exactly
        // as much ground as any other building that fails to place.
        GameWorld a = world(3L);
        assertNotNull(a.placeBuilding(0, BuildingType.COMMAND_POST, 12, 12, true));
        a.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        a.step();

        GameWorld b = world(3L);
        assertNotNull(b.placeBuilding(0, BuildingType.MG_NEST, 12, 12, true));
        b.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        b.step();

        assertTrue(a.influence(0, 12, 12) > b.influence(0, 12, 12),
                "a headquarters holds more than a machine gun post");
    }

    @Test
    public void thingsThatCannotFightHoldNothing() {
        // A harvester is worth a thousand credits and holds no ground whatsoever, which is the
        // clearest argument against weighting by cost.
        float added = contribution(4L, new Setup() {
            @Override
            public void apply(GameWorld w) {
                w.spawnUnit(0, UnitType.HARVESTER, 28.5f, 30.5f);
            }
        }, 28, 30);
        assertEquals(0f, added, 0.001f, "a harvester occupies nothing");
    }

    @Test
    public void anUnfinishedBuildingHoldsNothing() {
        float added = contribution(5L, new Setup() {
            @Override
            public void apply(GameWorld w) {
                w.placeBuilding(0, BuildingType.BARRACKS, 12, 12, false);
            }
        }, 12, 12);
        assertEquals(0f, added, 0.001f, "a foundation holds nothing until it is finished");
    }

    @Test
    public void aBatteryHoldsAlmostNothingWhereItStands() {
        GameWorld world = world(6L);
        world.placeBuilding(0, BuildingType.COMMAND_POST, 8, 10, true);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        Unit gun = world.spawnUnit(0, UnitType.FELDKANONE, 30.5f, 30.5f);
        settle(world);
        float withGun = world.influence(0, 30, 30);

        GameWorld other = world(6L);
        other.placeBuilding(0, BuildingType.COMMAND_POST, 8, 10, true);
        other.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        Unit rifleman = other.spawnUnit(0, UnitType.PARTISAN, 30.5f, 30.5f);
        settle(other);
        float withRifleman = other.influence(0, 30, 30);

        assertTrue(gun.hp() > rifleman.hp(), "the gun is the tougher of the two");
        assertTrue(withGun < withRifleman,
                "and still holds less ground: " + withGun + " vs " + withRifleman);
    }

    @Test
    public void aDugInPositionHoldsMoreThanTheSameMenInTheOpen() {
        GameWorld open = world(7L);
        open.placeBuilding(0, BuildingType.COMMAND_POST, 8, 10, true);
        open.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        for (int i = 0; i < 4; i++) {
            open.spawnUnit(0, UnitType.PARTISAN, 30.5f + i, 30.5f);
        }
        settle(open);

        GameWorld dug = world(7L);
        dug.placeBuilding(0, BuildingType.COMMAND_POST, 8, 10, true);
        dug.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        for (int i = 0; i < 4; i++) {
            dug.spawnUnit(0, UnitType.PARTISAN, 30.5f + i, 30.5f);
        }
        // Half the cell dug out, which is a frontage rather than one man's hole.
        for (int ty = 28; ty < 32; ty++) {
            for (int tx = 28; tx < 30; tx++) {
                dug.map().setCover(tx, ty, TileMap.MAX_COVER);
            }
        }
        settle(dug);

        assertTrue(dug.influence(0, 30, 30) > open.influence(0, 30, 30),
                "earthworks are worth something to the men in them");
    }

    @Test
    public void anEmptyTrenchHoldsNothingForAnybody() {
        // Entrenchment records how deep a tile is dug but not who dug it, so it cannot be a
        // source of control - only a multiplier on whoever is actually there. Nought times
        // anything is nought, and that is the point.
        float added = contribution(8L, new Setup() {
            @Override
            public void apply(GameWorld w) {
                for (int ty = 24; ty < 28; ty++) {
                    for (int tx = 24; tx < 28; tx++) {
                        w.map().setCover(tx, ty, TileMap.MAX_COVER);
                    }
                }
            }
        }, 26, 26);
        assertEquals(0f, added, 0.001f, "an empty trench multiplies nobody's presence");
    }

    @Test
    public void oneManInADeepHoleIsStillOneMan() {
        // The failure the per-cell mean exists to prevent: reading entrenchment from the tile
        // underfoot lets a lone scout on one deeply dug tile multiply his whole weight and
        // project as much as a platoon.
        GameWorld world = world(9L);
        world.placeBuilding(0, BuildingType.COMMAND_POST, 8, 10, true);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        world.spawnUnit(0, UnitType.PARTISAN, 30.5f, 30.5f);
        world.map().setCover(30, 30, TileMap.MAX_COVER);
        settle(world);
        float lone = world.influence(0, 30, 30);

        GameWorld crowd = world(9L);
        crowd.placeBuilding(0, BuildingType.COMMAND_POST, 8, 10, true);
        crowd.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        for (int i = 0; i < 4; i++) {
            crowd.spawnUnit(0, UnitType.PARTISAN, 29.5f + i * 0.5f, 30.5f);
        }
        settle(crowd);

        assertTrue(lone < crowd.influence(0, 30, 30),
                "one man in a hole must not out-hold four men in the open");
    }

    @Test
    public void menWhoAreRunningHoldNothing() {
        GameWorld world = world(10L);
        world.placeBuilding(0, BuildingType.COMMAND_POST, 8, 10, true);
        world.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);

        List<Unit> members = new ArrayList<Unit>();
        for (int i = 0; i < 6; i++) {
            members.add(world.spawnUnit(0, UnitType.PARTISAN, 30.5f + i * 0.9f, 30.5f));
        }
        com.ccwolf.core.squad.Squad squad = world.formSquad(0, members);
        settle(world);
        float standing = world.influence(0, 30, 30);
        assertTrue(standing > 0f, "they hold it while they are there");

        squad.breakAt(world.tick() + 10000);
        settle(world);
        assertTrue(world.influence(0, 30, 30) < standing,
                "a squad whose nerve has gone is not holding anything");
    }

    @Test
    public void theSameSeedGivesTheSameGround() {
        GameWorld a = world(11L);
        a.placeBuilding(0, BuildingType.COMMAND_POST, 8, 10, true);
        a.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        GameWorld b = world(11L);
        b.placeBuilding(0, BuildingType.COMMAND_POST, 8, 10, true);
        b.placeBuilding(1, BuildingType.COMMAND_POST, 55, 52, true);
        for (int i = 0; i < 40; i++) {
            a.step();
            b.step();
        }
        for (int y = 0; y < a.controlCellsDown(); y++) {
            for (int x = 0; x < a.controlCellsAcross(); x++) {
                assertEquals(a.controlAtCell(0, x, y), b.controlAtCell(0, x, y), 0f,
                        "cell " + x + "," + y + " must be bit-identical on a replay");
            }
        }
    }
}
