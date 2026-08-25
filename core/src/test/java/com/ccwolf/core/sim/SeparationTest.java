package com.ccwolf.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.order.MoveOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Holds separation steering to what it is for.
 *
 * <p>It has two jobs that pull against each other: units must not stand inside one another, and
 * pushing them apart must not stop them getting where they are going. The second used to lose —
 * separation moved units bodily backwards, so in a crowd they made no headway, decided they
 * were stuck, and threw their routes away. At five hundred a side that was about half of all
 * the pathfinding in the game.
 */
public class SeparationTest {

    @Test
    public void stackedUnitsComeApart() {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), 3L);
        world.addPlayer(Faction.RESISTANCE, false, "A");

        // Twelve men on one tile, which is what a factory exit looks like for a moment.
        List<Unit> squad = new ArrayList<Unit>();
        for (int i = 0; i < 12; i++) {
            squad.add(world.spawnUnit(0, UnitType.PARTISAN, 20.5f, 20.5f));
        }

        for (int i = 0; i < 40; i++) {
            world.step();
        }

        int overlapping = 0;
        for (int i = 0; i < squad.size(); i++) {
            for (int j = i + 1; j < squad.size(); j++) {
                Unit a = squad.get(i);
                Unit b = squad.get(j);
                float gap = a.distanceTo(b.x(), b.y());
                if (gap < (a.radius() + b.radius()) * 0.8f) {
                    overlapping++;
                }
            }
        }
        assertEquals(0, overlapping,
                "a stacked squad should have come apart within two seconds");
    }

    @Test
    public void aCrowdStillGetsWhereItWasSent() {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), 4L);
        world.addPlayer(Faction.RESISTANCE, false, "A");

        // A press of infantry, all ordered to the same distant point. This is the case that
        // used to generate a repath storm: everyone shoving everyone, nobody advancing.
        List<Unit> crowd = new ArrayList<Unit>();
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 6; x++) {
                crowd.add(world.spawnUnit(0, UnitType.PARTISAN, 10.5f + x * 0.4f,
                        10.5f + y * 0.4f));
            }
        }
        for (int i = 0; i < crowd.size(); i++) {
            crowd.get(i).setOrder(new MoveOrder(30, 30));
        }

        float startDistance = averageDistanceTo(crowd, 30.5f, 30.5f);
        for (int i = 0; i < 400; i++) {
            world.step();
        }
        float endDistance = averageDistanceTo(crowd, 30.5f, 30.5f);

        assertTrue(endDistance < startDistance * 0.35f,
                "a crowd ordered across the map should have closed most of the distance, but "
                        + "went from " + startDistance + " to " + endDistance);
    }

    @Test
    public void separationNeverMovesAUnitFurtherThanItsOwnRadius() {
        GameWorld world = new GameWorld(MapCatalog.load("kreisau"), 5L);
        world.addPlayer(Faction.RESISTANCE, false, "A");

        // Deliberately pathological: forty men on one spot, so the summed push is enormous.
        List<Unit> pile = new ArrayList<Unit>();
        for (int i = 0; i < 40; i++) {
            pile.add(world.spawnUnit(0, UnitType.PARTISAN, 25.5f, 25.5f));
        }

        float[] beforeX = new float[pile.size()];
        float[] beforeY = new float[pile.size()];
        for (int i = 0; i < pile.size(); i++) {
            beforeX[i] = pile.get(i).x();
            beforeY[i] = pile.get(i).y();
        }

        world.step();

        for (int i = 0; i < pile.size(); i++) {
            Unit u = pile.get(i);
            float moved = (float) Math.sqrt(
                    (u.x() - beforeX[i]) * (u.x() - beforeX[i])
                            + (u.y() - beforeY[i]) * (u.y() - beforeY[i]));
            // A little over one radius: the unit may also have walked under its own power.
            assertTrue(moved <= u.radius() * 1.5f + 0.01f,
                    "unit " + u.id() + " was flung " + moved + " tiles in a single tick");
        }
    }

    private float averageDistanceTo(List<Unit> units, float x, float y) {
        float total = 0f;
        for (int i = 0; i < units.size(); i++) {
            total += units.get(i).distanceTo(x, y);
        }
        return total / units.size();
    }
}
