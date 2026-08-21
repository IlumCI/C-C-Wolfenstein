package com.ccwolf.core.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AStarTest {

    /** Simple test grid: '#' blocks, '.' is open, 'S' is slow ground. */
    private static final class TestGrid implements PathGrid {
        private final String[] rows;

        TestGrid(String... rows) {
            this.rows = rows;
        }

        @Override
        public int width() {
            return rows[0].length();
        }

        @Override
        public int height() {
            return rows.length;
        }

        @Override
        public boolean isBlocked(int x, int y) {
            if (x < 0 || y < 0 || x >= width() || y >= height()) {
                return true;
            }
            return rows[y].charAt(x) == '#';
        }

        @Override
        public float moveCost(int x, int y) {
            return rows[y].charAt(x) == 'S' ? 8f : 1f;
        }
    }

    @Test
    void findsStraightLineAcrossOpenGround() {
        AStar aStar = new AStar();
        int[] path = aStar.findPath(new TestGrid("......", "......", "......"), 0, 1, 5, 1);

        assertNotNull(path);
        assertEquals(5, path.length);
        assertEquals(5, AStar.packX(path[path.length - 1]));
        assertEquals(1, AStar.packY(path[path.length - 1]));
    }

    @Test
    void routesAroundAWall() {
        AStar aStar = new AStar();
        PathGrid grid = new TestGrid(
                "..#..",
                "..#..",
                "..#..",
                ".....");
        int[] path = aStar.findPath(grid, 0, 0, 4, 0);

        assertNotNull(path);
        for (int packed : path) {
            assertTrue(!grid.isBlocked(AStar.packX(packed), AStar.packY(packed)),
                    "path must never enter a blocked tile");
        }
        // The only way through is the open row at the bottom.
        boolean usedGap = false;
        for (int packed : path) {
            if (AStar.packY(packed) == 3) {
                usedGap = true;
            }
        }
        assertTrue(usedGap, "path should detour through the gap");
    }

    @Test
    void prefersCheapTerrainOverTheShortestLine() {
        AStar aStar = new AStar();
        PathGrid grid = new TestGrid(
                ".....",
                "SSSS.",
                ".....");
        int[] path = aStar.findPath(grid, 0, 1, 4, 1);

        assertNotNull(path);
        for (int packed : path) {
            int x = AStar.packX(packed);
            int y = AStar.packY(packed);
            assertTrue(y != 1 || x >= 4, "should step off the slow row rather than plough through");
        }
    }

    @Test
    void returnsNullWhenCompletelyWalledIn() {
        AStar aStar = new AStar();
        PathGrid grid = new TestGrid(
                "###",
                "#.#",
                "###");
        assertNull(aStar.findPath(grid, 1, 1, 2, 2));
    }

    @Test
    void returnsBestEffortPathWhenGoalIsUnreachable() {
        AStar aStar = new AStar();
        PathGrid grid = new TestGrid(
                ".....",
                ".....",
                "#####",
                "....."); // the goal sits behind a solid wall
        int[] path = aStar.findPath(grid, 0, 0, 4, 3);

        assertNotNull(path, "a partial approach is better than refusing to move");
        int last = path[path.length - 1];
        assertTrue(AStar.packY(last) <= 1, "should stop on our side of the wall");
    }

    @Test
    void emptyPathWhenAlreadyAtTheDestination() {
        AStar aStar = new AStar();
        int[] path = aStar.findPath(new TestGrid("...", "...", "..."), 1, 1, 1, 1);
        assertNotNull(path);
        assertEquals(0, path.length);
    }

    @Test
    void doesNotCutDiagonallyThroughACorner() {
        AStar aStar = new AStar();
        PathGrid grid = new TestGrid(
                ".#.",
                "#..",
                "...");
        // (0,0) is boxed off diagonally; the only neighbours are blocked.
        assertNull(aStar.findPath(grid, 0, 0, 2, 2));
    }

    @Test
    void packingRoundTrips() {
        int packed = AStar.pack(37, 61);
        assertEquals(37, AStar.packX(packed));
        assertEquals(61, AStar.packY(packed));
    }
}
