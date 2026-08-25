package com.ccwolf.core.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.path.AStar;
import com.ccwolf.core.path.OccupancyGrid;
import org.junit.jupiter.api.Test;

/**
 * The standing claims of the two newer generated maps, identical in kind to Frontline's:
 * loadable at size, spawns on clear ground, a spawn-to-spawn march inside the real
 * pathfinder's budget, and the shipped file byte-reproduced by its generator.
 */
public class NewMapsTest {

    @Test
    public void schwarzbruckLoadsAndMarches() {
        TileMap map = MapCatalog.load(MapCatalog.SCHWARZBRUCK);
        assertEquals(128, map.width());
        assertEquals(128, map.height());
        assertMarchable(map);
    }

    @Test
    public void aschefeldLoadsAndMarches() {
        TileMap map = MapCatalog.load(MapCatalog.ASCHEFELD);
        assertEquals(96, map.width());
        assertEquals(96, map.height());
        assertMarchable(map);
    }

    private static void assertMarchable(TileMap map) {
        assertEquals(2, map.spawnPoints().size());
        for (int[] spawn : map.spawnPoints()) {
            assertTrue(map.terrain(spawn[0], spawn[1]).isPassable(),
                    "spawn at " + spawn[0] + "," + spawn[1] + " must be passable");
        }
        OccupancyGrid grid = new OccupancyGrid(map);
        int[] a = map.spawnPoint(0);
        int[] b = map.spawnPoint(1);
        int[] path = new AStar().findPath(grid, a[0], a[1], b[0], b[1]);
        assertTrue(path.length > 0, "no route from spawn to spawn inside the node budget");
    }

    @Test
    public void theGeneratorsReproduceTheShippedFiles() throws Exception {
        SchwarzbruckGenerator city = new SchwarzbruckGenerator();
        city.build();
        city.validate();
        assertShippedMatches("schwarzbruck", writerOutput(city));

        AschefeldGenerator plain = new AschefeldGenerator();
        plain.build();
        plain.validate();
        assertShippedMatches("aschefeld", writerOutput(plain));
    }

    private static String writerOutput(Object generator) throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        java.io.PrintStream out = new java.io.PrintStream(bytes, false, "UTF-8");
        if (generator instanceof SchwarzbruckGenerator) {
            ((SchwarzbruckGenerator) generator).write(out);
        } else {
            ((AschefeldGenerator) generator).write(out);
        }
        return bytes.toString("UTF-8").replace("\r\n", "\n");
    }

    private static void assertShippedMatches(String name, String generated) throws Exception {
        java.io.InputStream shipped =
                MapCatalog.class.getResourceAsStream("/maps/" + name + ".map");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        int b;
        while ((b = shipped.read()) >= 0) {
            bytes.write(b);
        }
        assertEquals(bytes.toString("UTF-8").replace("\r\n", "\n"), generated,
                name + " drifted from its generator");
    }
}
