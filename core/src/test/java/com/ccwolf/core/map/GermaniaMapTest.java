package com.ccwolf.core.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.path.AStar;
import com.ccwolf.core.path.OccupancyGrid;
import org.junit.jupiter.api.Test;

/**
 * The capital's standing claims — the same contract every generated map signs, plus the two
 * facts that make this map this map: the monuments are indestructible ground, and the avenue
 * genuinely runs under the Arch.
 */
public class GermaniaMapTest {

    @Test
    public void loadsAtSizeWithSpawnsOnClearGround() {
        TileMap map = MapCatalog.load(MapCatalog.GERMANIA);
        assertEquals(256, map.width());
        assertEquals(256, map.height());
        assertEquals(2, map.spawnPoints().size());
        for (int[] spawn : map.spawnPoints()) {
            assertTrue(map.terrain(spawn[0], spawn[1]).isPassable());
        }
    }

    @Test
    public void aCrossCapitalMarchFitsThePathBudget() {
        TileMap map = MapCatalog.load(MapCatalog.GERMANIA);
        OccupancyGrid grid = new OccupancyGrid(map);
        int[] a = map.spawnPoint(0);
        int[] b = map.spawnPoint(1);
        int[] path = new AStar().findPath(grid, a[0], a[1], b[0], b[1]);
        assertTrue(path.length > 0, "no route between the quarters inside the node budget");
    }

    @Test
    public void theAvenueRunsUnderTheArch() {
        TileMap map = MapCatalog.load(MapCatalog.GERMANIA);
        // Between the Arch's legs (y 173..181 at the axis), the ground must still be the
        // highway - a solid arch would cut the map's spine in half.
        boolean legWest = false;
        boolean legEast = false;
        for (int y = 174; y <= 180; y++) {
            assertEquals(Terrain.HIGHWAY, map.terrain(128, y),
                    "the avenue must pass under the Arch at y=" + y);
            legWest |= map.terrain(122, y) == Terrain.MARBLE;
            legEast |= map.terrain(135, y) == Terrain.MARBLE;
        }
        assertTrue(legWest && legEast, "the Arch must actually stand over the avenue");
    }

    @Test
    public void theMonumentsAreImpassableAndBlindwalls() {
        TileMap map = MapCatalog.load(MapCatalog.GERMANIA);
        // The Great Hall's mass.
        assertEquals(Terrain.MARBLE, map.terrain(128, 40));
        assertTrue(map.terrain(128, 40).blocksSight());
        // A monolith quarter block: superconcrete, impassable.
        int walls = 0;
        for (int y = 130; y < 226; y++) {
            for (int x = 58; x < 118; x++) {
                if (map.terrain(x, y) == Terrain.SUPERCRETE) {
                    walls++;
                }
            }
        }
        assertTrue(walls > 400, "the monolith quarters should be built of superconcrete");
    }

    @Test
    public void theGeneratorReproducesTheShippedFile() throws Exception {
        GermaniaGenerator generator = new GermaniaGenerator();
        generator.build();
        generator.validate();
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        generator.write(new java.io.PrintStream(bytes, false, "UTF-8"));
        java.io.InputStream shipped =
                MapCatalog.class.getResourceAsStream("/maps/germania.map");
        java.io.ByteArrayOutputStream shippedBytes = new java.io.ByteArrayOutputStream();
        int b;
        while ((b = shipped.read()) >= 0) {
            shippedBytes.write(b);
        }
        assertEquals(shippedBytes.toString("UTF-8").replace("\r\n", "\n"),
                bytes.toString("UTF-8").replace("\r\n", "\n"));
    }
}
