package com.ccwolf.core.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.path.AStar;
import com.ccwolf.core.path.OccupancyGrid;
import org.junit.jupiter.api.Test;

/**
 * The large map's standing claims.
 *
 * <p>Loadable, the size it says, two spawns on clear ground, and — the one that matters — an
 * army can walk from one spawn to the other within the pathfinder's budget. The last is not the
 * generator's breadth-first check re-run: it goes through the real A* with the real node limit,
 * because that is the code a march actually uses, and the map-scaled budget exists precisely so
 * a cross-map path on this map is not a pathology.
 */
public class FrontlineMapTest {

    @Test
    public void loadsAtSizeWithSpawnsOnClearGround() {
        TileMap map = MapCatalog.load(MapCatalog.FRONTLINE);
        assertEquals(256, map.width());
        assertEquals(256, map.height());
        assertEquals(2, map.spawnPoints().size());
        for (int[] spawn : map.spawnPoints()) {
            assertTrue(map.terrain(spawn[0], spawn[1]).isPassable(),
                    "spawn at " + spawn[0] + "," + spawn[1] + " must be passable");
        }
    }

    @Test
    public void aCrossMapMarchFitsThePathBudget() {
        TileMap map = MapCatalog.load(MapCatalog.FRONTLINE);
        OccupancyGrid grid = new OccupancyGrid(map);
        int[] west = map.spawnPoint(0);
        int[] east = map.spawnPoint(1);
        int[] path = new AStar().findPath(grid, west[0], west[1], east[0], east[1]);
        assertTrue(path.length > 0,
                "no route from spawn to spawn inside the node budget - either a crossing is"
                        + " gone or the budget stopped scaling with the map");
    }

    @Test
    public void theGeneratorReproducesTheShippedFile() throws Exception {
        // The map is the text file; the generator is how it was made. If the two drift apart,
        // either the file was hand-edited (fine - then the generator's claim must go) or the
        // generator changed without regenerating (not fine), and both deserve a red bar.
        FrontlineGenerator generator = new FrontlineGenerator();
        generator.build();
        generator.validate();
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        generator.write(new java.io.PrintStream(bytes, false, "UTF-8"));
        java.io.InputStream shipped =
                MapCatalog.class.getResourceAsStream("/maps/frontline.map");
        java.io.ByteArrayOutputStream shippedBytes = new java.io.ByteArrayOutputStream();
        int b;
        while ((b = shipped.read()) >= 0) {
            shippedBytes.write(b);
        }
        assertEquals(shippedBytes.toString("UTF-8").replace("\r\n", "\n"),
                bytes.toString("UTF-8").replace("\r\n", "\n"));
    }
}
