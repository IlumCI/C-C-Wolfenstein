package com.ccwolf.core.map;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Random;

/**
 * Generates {@code schwarzbruck.map}: the 128-by-128 dead city.
 *
 * <p>Frontline is a war with a city in the middle of it; Schwarzbruck is a city with a war in
 * all of it. The whole map is districts, and two stagnant canals cross at its heart, cutting
 * the city into four quarters. The bases sit in opposite corners, so every axis of advance is
 * a diagonal through streets — and every one of the six bridges is a place somebody will die.
 *
 * <p>The two contested uranium fields sit in the OFF-diagonal quarters, in fire-cleared
 * plazas: the ground neither side is marching through on the way to the other's throat, which
 * is exactly why the economy drags both armies sideways into the rest of the city.
 *
 * <p>Same contract as the other generated maps: the shipped file is the artifact, the seed is
 * a constant, and the generator proves the map is a map before writing a byte.
 */
public final class SchwarzbruckGenerator {

    public static final int SIZE = 128;

    /** The shipped file was generated with this seed. Change it and the map is a new map. */
    private static final long SEED = 5L;

    private final char[][] tiles = new char[SIZE][SIZE];
    private final Random random = new Random(SEED);

    public static void main(String[] args) throws IOException {
        SchwarzbruckGenerator generator = new SchwarzbruckGenerator();
        generator.build();
        generator.validate();
        generator.write(System.out);
    }

    void build() {
        // 1. Ash-churned ground everywhere; the texture of a city's floor after the fires.
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                tiles[y][x] = '.';
            }
        }
        for (int i = 0; i < 320; i++) {
            blob(random.nextInt(SIZE), random.nextInt(SIZE), 1 + random.nextInt(2), ':', 0.5f);
        }

        // 2. The districts, over the whole map, densest at the heart. Same block fates as
        // Frontline's city - shell, collapse, burnt lot - because a dead city is a dead city.
        city();

        // 3. Ore. Small home fields a base can hold from its walls; the rich fields in
        // fire-cleared plazas in the off-diagonal quarters, so the road to money is not the
        // road to the enemy.
        oreField(26, 10, 4);
        oreField(10, 26, 4);
        oreField(102, 118, 4);
        oreField(118, 102, 4);
        plaza(100, 28);
        oreField(100, 28, 7);
        plaza(28, 100);
        oreField(28, 100, 7);

        // 4. The canals, drawn after the districts so their banks are ragged with ruin. They
        // cross at the city's heart; nothing crosses them but the boulevards below.
        for (int y = 0; y < SIZE; y++) {
            double centre = 64 + 5 * Math.sin(y / 19.0) + 2 * Math.sin(y / 7.3);
            for (int dx = -2; dx <= 2; dx++) {
                set((int) Math.round(centre) + dx, y, '~');
            }
        }
        for (int x = 0; x < SIZE; x++) {
            double centre = 64 + 5 * Math.sin(x / 17.0) + 2 * Math.sin(x / 8.1);
            for (int dy = -2; dy <= 2; dy++) {
                set(x, (int) Math.round(centre) + dy, '~');
            }
        }

        // 5. Boulevards, last, overwriting what they cross - and the overwrites ARE the
        // bridges. The first cut ran boulevards straight down the canal lines, which
        // overwrote the water along its whole length: a canal you can cross anywhere is a
        // ditch. So the long roads are a ring, crossing each canal once per side, and the
        // inner crossings are four short bridge stubs set off-centre in a diagonal pair -
        // eight crossings, none of them the one you wanted.
        eastWestRoad(20);
        eastWestRoad(108);
        northSouthRoad(20);
        northSouthRoad(108);
        eastWestSegment(44, 50, 78);
        eastWestSegment(84, 50, 78);
        northSouthSegment(44, 50, 78);
        northSouthSegment(84, 50, 78);

        // 6. The corners the bases stand in.
        clearForBase(16, 16);
        clearForBase(112, 112);
    }

    private void city() {
        int streetEvery = 9;
        for (int by = 4; by < SIZE - 4; by += streetEvery) {
            for (int bx = 4; bx < SIZE - 4; bx += streetEvery) {
                float dx = (bx - SIZE / 2f) / (SIZE / 2f);
                float dy = (by - SIZE / 2f) / (SIZE / 2f);
                float centrality = 1f - (float) Math.sqrt(dx * dx + dy * dy) * 0.8f;
                if (random.nextFloat() > 0.2f + 0.7f * centrality) {
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

    /** Burns a district down to open ground, so a rich field has room to be fought over. */
    private void plaza(int cx, int cy) {
        for (int y = cy - 10; y <= cy + 10; y++) {
            for (int x = cx - 10; x <= cx + 10; x++) {
                if (in(x, y) && tiles[y][x] == '#') {
                    tiles[y][x] = ':';
                }
            }
        }
    }

    private void oreField(int cx, int cy, int radius) {
        blob(cx, cy, radius, '*', 0.85f);
    }

    private void eastWestRoad(int y) {
        for (int x = 0; x < SIZE; x++) {
            int wobble = (int) Math.round(2 * Math.sin(x / 23.0));
            set(x, y + wobble, '=');
            set(x, y + wobble + 1, '=');
        }
    }

    private void northSouthRoad(int x) {
        for (int y = 0; y < SIZE; y++) {
            int wobble = (int) Math.round(2 * Math.sin(y / 27.0));
            set(x + wobble, y, '=');
            set(x + wobble + 1, y, '=');
        }
    }

    /** A straight bridge stub: no wobble, because a bridge that wobbles misses its canal. */
    private void eastWestSegment(int y, int x0, int x1) {
        for (int x = x0; x <= x1; x++) {
            set(x, y, '=');
            set(x, y + 1, '=');
        }
    }

    private void northSouthSegment(int x, int y0, int y1) {
        for (int y = y0; y <= y1; y++) {
            set(x, y, '=');
            set(x + 1, y, '=');
        }
    }

    private void clearForBase(int cx, int cy) {
        for (int y = cy - 12; y <= cy + 12; y++) {
            for (int x = cx - 12; x <= cx + 12; x++) {
                if (!in(x, y)) {
                    continue;
                }
                char t = tiles[y][x];
                if (t == '#' || t == '~' || t == ':') {
                    tiles[y][x] = '.';
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
        int[][] spawns = {{16, 16}, {112, 112}};
        for (int[] spawn : spawns) {
            for (int y = spawn[1] - 4; y <= spawn[1] + 4; y++) {
                for (int x = spawn[0] - 4; x <= spawn[0] + 4; x++) {
                    char t = tiles[y][x];
                    if (t == '#' || t == '~') {
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
                if (in(x, y) && !seen[y][x] && tiles[y][x] != '~' && tiles[y][x] != '#') {
                    seen[y][x] = true;
                    queue.add(new int[] {x, y});
                }
            }
        }
        if (!seen[spawns[1][1]][spawns[1][0]]) {
            throw new IllegalStateException(
                    "The southeast spawn is unreachable: no bridge survived");
        }
    }

    void write(PrintStream out) {
        out.println("# Schwarzbruck - 128x128 1v1 city map for C&C: Wolfenstein.");
        out.println("# Generated by SchwarzbruckGenerator (seed " + SEED + "), then reviewed");
        out.println("# and checked in. Regenerate with:");
        out.println("#   java -cp core/build/classes/java/main:core/build/classes/java/test \\");
        out.println("#     com.ccwolf.core.map.SchwarzbruckGenerator > "
                + "core/src/main/resources/maps/schwarzbruck.map");
        out.println("# glyphs: . grass  = road  : rubble  * uranium  ~ water  # ruins");
        out.println("name Schwarzbruck");
        out.println("size " + SIZE + " " + SIZE);
        out.println("spawn 16 16");
        out.println("spawn 112 112");
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
