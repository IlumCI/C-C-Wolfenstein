package com.ccwolf.core.map;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Random;

/**
 * Generates {@code germania.map}: the capital, 256 by 256, to the plan.
 *
 * <p>The plan is the real one, at roughly twenty metres to the tile. A north-south axis five
 * kilometres long and a hundred and twenty metres wide — six tiles of poured highway — runs
 * from the Great Hall to the South Station. The Hall's dome is sixteen tiles across, sitting
 * in its podium on the north bank basin where the river was bent around it. South of it the
 * Great Plaza, flanked by the Palace and the Chancellery in monumental stone. Halfway down
 * the axis, the Round Plaza; the east-west axis crosses just north of it. Then the Arch —
 * nine tiles wide, three times the one in Paris — with the avenue running between its legs,
 * and at the far south the Station and its yards. The new city flanks the axis in
 * superconcrete monoliths cut by alleys; the old city survives at the edges as the ruins it
 * was left as; the great park lies west of the axis where it always did.
 *
 * <p>Operationally the map asks the Germania question: the axis is the fastest road in the
 * game and a five-kilometre kill zone; the monolith quarters are slow, blind, alley-fought
 * flanks; the park is the soft route with the money in it. The state's architecture is
 * indestructible — SUPERCRETE and MARBLE fall to nothing — so the city itself is the
 * fortification.
 *
 * <p>Same contract as every generated map: the shipped file is the artifact, the seed is a
 * constant, and the generator proves the map is a map before writing a byte.
 */
public final class GermaniaGenerator {

    public static final int SIZE = 256;

    /** The shipped file was generated with this seed. Change it and the map is a new map. */
    private static final long SEED = 3L;

    /** The axis' centreline and the half-width of its carriageway. */
    private static final int AXIS = 128;

    private final char[][] tiles = new char[SIZE][SIZE];
    private final Random random = new Random(SEED);

    public static void main(String[] args) throws IOException {
        GermaniaGenerator generator = new GermaniaGenerator();
        generator.build();
        generator.validate();
        generator.write(System.out);
    }

    void build() {
        // 1. Ground. The capital's earth is still earth, and it still rains on it.
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                tiles[y][x] = '.';
            }
        }
        for (int i = 0; i < 500; i++) {
            blob(random.nextInt(SIZE), random.nextInt(SIZE), 1 + random.nextInt(2), ':', 0.4f);
        }

        // 2. The old city, at the edges: what Berlin was, left as ruins past the parade
        // ground's sightlines. Everything the new city did not deem worth rebuilding.
        oldCity(4, 52);
        oldCity(204, 252);

        // 3. The great park, west of the axis along the east-west spine: the one green
        // relief in the capital, and not coincidentally where a harvester can work in peace.
        park(60, 84, 116, 120);

        // 4. The new city: superconcrete monolith quarters flanking the axis, cut by alleys
        // and service roads. The state built them blind and identical.
        monolithQuarter(58, 118, 130, 226);
        monolithQuarter(138, 198, 52, 226);
        monolithQuarter(58, 118, 52, 84);

        // 5. The monuments, north to south down the axis, in the stone the state polishes.
        greatHall();
        greatPlaza();
        roundPlaza();
        triumphalArch();
        southStation();

        // 6. The river, bent across the north around the Hall's basin, drawn after the
        // monuments so the basin edge is the podium's edge.
        for (int x = 0; x < SIZE; x++) {
            double centre = 18 + 6 * Math.sin(x / 29.0)
                    + (Math.abs(x - AXIS) < 30 ? -6 : 0);
            for (int dy = -3; dy <= 3; dy++) {
                set(x, (int) Math.round(centre) + dy, '~');
            }
        }

        // 7. The roads. The axis first - six tiles of highway from the Great Plaza to the
        // Station forecourt, running under the Arch. Then the east-west axis, the ring
        // avenues, the service grid, and two bridges to the north bank.
        for (int y = 76; y <= 232; y++) {
            for (int x = AXIS - 3; x <= AXIS + 2; x++) {
                if (tiles[y][x] != 'M') {
                    tiles[y][x] = 'H';
                }
            }
        }
        for (int x = 0; x < SIZE; x++) {
            for (int dy = 0; dy < 3; dy++) {
                if (tiles[102 + dy][x] != 'M') {
                    tiles[102 + dy][x] = 'H';
                }
            }
        }
        eastWestRoad(140);
        eastWestRoad(196);
        eastWestRoad(232);
        northSouthRoad(58);
        northSouthRoad(198);
        // Bridges: the only dry ways to the north bank strip.
        northSouthBridge(58);
        northSouthBridge(198);

        // 8. Ore. Home fields by each base; the rich fields in the park, the rail yards,
        // and one hard one on the Round Plaza's apron - money inside the kill zone.
        oreField(176, 44, 5);
        oreField(190, 56, 4);
        oreField(66, 232, 5);
        oreField(94, 240, 4);
        // North of the east-west axis, not on it: the first roll put a uranium seam in the
        // middle of the autobahn.
        oreField(84, 90, 8);
        oreField(150, 244, 7);
        oreField(112, 124, 5);

        // 9. The ground the bases stand on: the government quarter's north-east corner for
        // one army, the rail districts' south-west for the other.
        clearForBase(176, 64);
        clearForBase(80, 220);
    }

    /** Old Berlin: the Frontline city recipe, because it is the same dead city. */
    private void oldCity(int left, int right) {
        int streetEvery = 9;
        for (int by = 30; by < SIZE - 8; by += streetEvery) {
            for (int bx = left; bx < right; bx += streetEvery) {
                if (random.nextFloat() > 0.55f) {
                    continue;
                }
                int w = 5 + random.nextInt(3);
                int h = 5 + random.nextInt(3);
                int x = bx + 1 + random.nextInt(2);
                int y = by + 1 + random.nextInt(2);
                float fate = random.nextFloat();
                if (fate < 0.55f) {
                    rect(x, y, w, h, '#');
                    rect(x + 1, y + 1, w - 2, h - 2, ':');
                    if (random.nextFloat() < 0.5f) {
                        set(x + w / 2, y + h - 1, ':');
                        set(x + w / 2, y, ':');
                    }
                } else if (fate < 0.85f) {
                    blob(x + w / 2, y + h / 2, Math.max(w, h) / 2 + 1, ':', 0.8f);
                } else {
                    rect(x, y, w, h, '.');
                    blob(x + w / 2, y + h / 2, 2, ':', 0.6f);
                }
            }
        }
    }

    private void park(int left, int top, int right, int bottom) {
        // Kept as ground, deliberately: the park is the absence of the city. Paths only.
        for (int y = top; y <= bottom; y += 12) {
            for (int x = left; x <= right; x++) {
                set(x, y + (int) Math.round(2 * Math.sin(x / 13.0)), '=');
            }
        }
    }

    /**
     * A monolith quarter: superconcrete blocks on the state's grid, separated by alleys two
     * tiles wide and a service road every third block. No block has a door the map can see.
     */
    private void monolithQuarter(int left, int right, int top, int bottom) {
        int cell = 14;
        for (int by = top; by + 11 < bottom; by += cell) {
            for (int bx = left; bx + 11 < right; bx += cell) {
                if (random.nextFloat() < 0.12f) {
                    // A block the state never finished: its footprint in rubble.
                    blob(bx + 6, by + 6, 5, ':', 0.7f);
                    continue;
                }
                int w = 9 + random.nextInt(3);
                int h = 9 + random.nextInt(3);
                rect(bx + 1, by + 1, w, h, 'W');
            }
        }
        // Service roads through the quarter, one each way, so armies can move in it at all.
        int sy = (top + bottom) / 2;
        for (int x = left; x <= right; x++) {
            set(x, sy, '=');
            set(x, sy + 1, '=');
        }
        int sx = (left + right) / 2;
        for (int y = top; y <= bottom; y++) {
            set(sx, y, '=');
            set(sx + 1, y, '=');
        }
    }

    /** The Great Hall: the dome on its podium, in the river basin, at the head of the axis. */
    private void greatHall() {
        // Podium: a marble terrace the size of a district.
        rect(AXIS - 15, 28, 31, 26, '_');
        // The Hall itself: a square marble mass with the dome's disc inside it.
        rect(AXIS - 11, 30, 23, 20, 'M');
        disc(AXIS, 40, 8, 'M');
    }

    /** The Great Plaza, flanked by the Palace and the Chancellery. */
    private void greatPlaza() {
        rect(AXIS - 20, 54, 41, 22, '_');
        // The Palace, west; the Chancellery, east. Both in the stone the state polishes.
        rect(AXIS - 34, 56, 12, 18, 'M');
        rect(AXIS + 23, 56, 12, 18, 'M');
    }

    /** The Round Plaza, halfway down the axis, ringed by the ministries. */
    private void roundPlaza() {
        disc(AXIS, 122, 11, '_');
        rect(AXIS - 18, 112, 6, 8, 'W');
        rect(AXIS + 13, 112, 6, 8, 'W');
        rect(AXIS - 18, 128, 6, 8, 'W');
        rect(AXIS + 13, 128, 6, 8, 'W');
    }

    /** The Arch: two marble legs either side of the avenue, and the avenue runs under it. */
    private void triumphalArch() {
        rect(AXIS - 20, 172, 41, 14, '_');
        rect(AXIS - 9, 173, 6, 9, 'M');
        rect(AXIS + 4, 173, 6, 9, 'M');
    }

    /** The South Station: the terminal monolith and the yards that feed it. */
    private void southStation() {
        rect(AXIS - 20, 226, 41, 8, '_');
        rect(AXIS - 16, 236, 33, 10, 'W');
        // Rail yards east and west of the terminal, as churned service ground.
        for (int y = 238; y <= 246; y += 3) {
            for (int x = 20; x < AXIS - 20; x++) {
                set(x, y, '=');
            }
            for (int x = AXIS + 20; x < 236; x++) {
                set(x, y, '=');
            }
        }
    }

    private void oreField(int cx, int cy, int radius) {
        blob(cx, cy, radius, '*', 0.85f);
    }

    private void eastWestRoad(int y) {
        for (int x = 0; x < SIZE; x++) {
            int wobble = (int) Math.round(2 * Math.sin(x / 23.0));
            setRoad(x, y + wobble);
            setRoad(x, y + wobble + 1);
        }
    }

    private void northSouthRoad(int x) {
        for (int y = 30; y < SIZE; y++) {
            int wobble = (int) Math.round(2 * Math.sin(y / 27.0));
            setRoad(x + wobble, y);
            setRoad(x + wobble + 1, y);
        }
    }

    /** Straight over the river: a bridge that wobbles misses its water. */
    private void northSouthBridge(int x) {
        for (int y = 4; y <= 32; y++) {
            set(x, y, '=');
            set(x + 1, y, '=');
        }
    }

    /** Roads defer to the state's stone: an avenue ends at a monument, it does not cut it. */
    private void setRoad(int x, int y) {
        if (in(x, y) && tiles[y][x] != 'M' && tiles[y][x] != 'H') {
            tiles[y][x] = '=';
        }
    }

    private void clearForBase(int cx, int cy) {
        for (int y = cy - 12; y <= cy + 12; y++) {
            for (int x = cx - 12; x <= cx + 12; x++) {
                if (!in(x, y)) {
                    continue;
                }
                char t = tiles[y][x];
                if (t == '#' || t == '~' || t == ':' || t == 'W' || t == 'M') {
                    tiles[y][x] = '.';
                }
            }
        }
    }

    private void disc(int cx, int cy, int radius, char glyph) {
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                int dx = x - cx;
                int dy = y - cy;
                if (dx * dx + dy * dy <= radius * radius) {
                    set(x, y, glyph);
                }
            }
        }
    }

    private void blob(int cx, int cy, int radius, char glyph, float density) {
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                int dx = x - cx;
                int dy = y - cy;
                if (dx * dx + dy * dy <= radius * radius && random.nextFloat() < density) {
                    set(x, y, glyph);
                }
            }
        }
    }

    private void rect(int x, int y, int w, int h, char glyph) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                set(xx, yy, glyph);
            }
        }
    }

    private void set(int x, int y, char glyph) {
        if (in(x, y)) {
            tiles[y][x] = glyph;
        }
    }

    private boolean in(int x, int y) {
        return x >= 0 && y >= 0 && x < SIZE && y < SIZE;
    }

    void validate() {
        int[][] spawns = {{176, 64}, {80, 220}};
        for (int[] spawn : spawns) {
            for (int y = spawn[1] - 4; y <= spawn[1] + 4; y++) {
                for (int x = spawn[0] - 4; x <= spawn[0] + 4; x++) {
                    char t = tiles[y][x];
                    if (t == '#' || t == '~' || t == 'W' || t == 'M') {
                        throw new IllegalStateException("Blocked ground at " + x + "," + y
                                + " inside the spawn clearing at " + spawn[0] + "," + spawn[1]);
                    }
                }
            }
        }
        boolean[][] seen = new boolean[SIZE][SIZE];
        ArrayDeque<int[]> queue = new ArrayDeque<int[]>();
        queue.add(spawns[0]);
        seen[spawns[0][1]][spawns[0][0]] = true;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] step : steps) {
                int x = at[0] + step[0];
                int y = at[1] + step[1];
                if (in(x, y) && !seen[y][x] && passable(tiles[y][x])) {
                    seen[y][x] = true;
                    queue.add(new int[] {x, y});
                }
            }
        }
        if (!seen[spawns[1][1]][spawns[1][0]]) {
            throw new IllegalStateException(
                    "The rail district is unreachable from the government quarter");
        }
    }

    private static boolean passable(char t) {
        return t != '~' && t != '#' && t != 'W' && t != 'M';
    }

    void write(PrintStream out) {
        out.println("# Germania - 256x256 1v1 capital map for C&C: Wolfenstein.");
        out.println("# The plan at ~20m per tile: the Hall, the Great Plaza, the axis, the");
        out.println("# Round Plaza, the Arch, the South Station, monolith quarters, the park,");
        out.println("# the river bent around the basin, and old Berlin as ruins at the edges.");
        out.println("# Generated by GermaniaGenerator (seed " + SEED + "), then reviewed");
        out.println("# and checked in. Regenerate with:");
        out.println("#   java -cp core/build/classes/java/main:core/build/classes/java/test \\");
        out.println("#     com.ccwolf.core.map.GermaniaGenerator > "
                + "core/src/main/resources/maps/germania.map");
        out.println("# glyphs: . grass  = road  : rubble  * uranium  ~ water  # ruins");
        out.println("#         W superconcrete  M monument stone  _ pavement  H highway");
        out.println("name Germania");
        out.println("size " + SIZE + " " + SIZE);
        out.println("spawn 176 64");
        out.println("spawn 80 220");
        out.println("tiles");
        StringBuilder row = new StringBuilder(SIZE);
        for (int y = 0; y < SIZE; y++) {
            row.setLength(0);
            for (int x = 0; x < SIZE; x++) {
                row.append(tiles[y][x]);
            }
            out.println(row);
        }
    }
}
