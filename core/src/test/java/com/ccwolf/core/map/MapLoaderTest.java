package com.ccwolf.core.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MapLoaderTest {

    private static final String SAMPLE =
            "# a comment\n"
            + "name Test Valley\n"
            + "size 5 3\n"
            + "spawn 1 1\n"
            + "spawn 3 2\n"
            + "tiles\n"
            + ".....\n"
            + ".*#=~\n"
            + ".::..\n";

    @Test
    void parsesHeaderTerrainAndSpawns() {
        TileMap map = MapLoader.parse(SAMPLE);

        assertEquals("Test Valley", map.name());
        assertEquals(5, map.width());
        assertEquals(3, map.height());
        assertEquals(Terrain.ORE, map.terrain(1, 1));
        assertEquals(Terrain.WALL, map.terrain(2, 1));
        assertEquals(Terrain.ROAD, map.terrain(3, 1));
        assertEquals(Terrain.WATER, map.terrain(4, 1));
        assertEquals(Terrain.RUBBLE, map.terrain(1, 2));
        assertEquals(2, map.spawnPoints().size());
        assertEquals(3, map.spawnPoint(1)[0]);
        assertEquals(2, map.spawnPoint(1)[1]);
    }

    @Test
    void oreTilesStartSeededAndCanBeMinedOut() {
        TileMap map = MapLoader.parse(SAMPLE);

        assertEquals(TileMap.ORE_PER_TILE, map.ore(1, 1));
        assertEquals(10, map.takeOre(1, 1, 10));
        assertEquals(TileMap.ORE_PER_TILE - 10, map.ore(1, 1));

        int drained = map.takeOre(1, 1, 10_000);
        assertEquals(TileMap.ORE_PER_TILE - 10, drained);
        assertEquals(0, map.ore(1, 1));
        // The seam stays on the map so it can regrow.
        assertEquals(Terrain.ORE, map.terrain(1, 1));

        map.regrowOre(5);
        assertEquals(5, map.ore(1, 1));
    }

    @Test
    void outOfBoundsReadsAsSolidWall() {
        TileMap map = MapLoader.parse(SAMPLE);

        assertFalse(map.inBounds(-1, 0));
        assertEquals(Terrain.WALL, map.terrain(99, 99));
        assertFalse(map.isPassable(99, 99));
        assertEquals(0, map.ore(-3, 2));
    }

    @Test
    void rejectsMalformedMaps() {
        assertThrows(IllegalArgumentException.class,
                () -> MapLoader.parse("size 2 2\ntiles\n..\n"));
        assertThrows(IllegalArgumentException.class,
                () -> MapLoader.parse("size 3 1\ntiles\n.Z.\n"));
        assertThrows(IllegalArgumentException.class,
                () -> MapLoader.parse("name x\ntiles\n..\n"));
    }

    @Test
    void bundledSkirmishMapLoads() {
        TileMap map = MapCatalog.load(MapCatalog.KREISAU_VALLEY);

        assertEquals(64, map.width());
        assertEquals(64, map.height());
        assertEquals(2, map.spawnPoints().size());
        assertTrue(map.totalOre() > 50_000, "map should carry enough uranium for a match");
    }
}
