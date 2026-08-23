package com.ccwolf.core.map;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Random;

/**
 * Generates {@code frontline.map}: the 256-by-256 front map.
 *
 * <p>Sixty-five thousand glyphs is not a thing a person authors by hand, but the shipped map is
 * still the text file, not this class: the generator is run deliberately, its output is looked
 * at, tuned, and checked in, and from then on the file is the artifact — reviewable in a diff
 * like any other map. The seed is a constant, so re-running the tool reproduces the shipped
 * file byte for byte.
 *
 * <h2>The shape of the front</h2>
 *
 * <p>Sixteen times the ground of Kreisau Valley, laid out east against west. A meandering river
 * splits the map down the middle with exactly three crossings, and a ruined city straddles the
 * river band — so the middle third is where the game happens: every march east goes through a
 * bridge or through the rubble, both of which are exactly the ground the trench-and-front
 * machinery was built for. Each side has two home ore fields it can hold cheaply and there are
 * two rich contested fields on the map's spine, north and south of the city, so the economy
 * eventually forces both sides out of their corners.
 *
 * <p>The generator ends by proving the map is a map: both spawns stand on clear ground, and a
 * breadth-first search must walk from one to the other. A map that fails validation is not
 * written at all.
 */
public final class FrontlineGenerator {

    public static final int SIZE = 256;

    /** The shipped file was generated with this seed. Change it and the map is a new map. */
    private static final long SEED = 12L;

    private final char[][] tiles = new char[SIZE][SIZE];
    private final Random random = new Random(SEED);

    public static void main(String[] args) throws IOException {
        FrontlineGenerator generator = new FrontlineGenerator();
        generator.build();
        generator.validate();
        generator.write(System.out);
    }

    void build() {
        // 1. Open ground everywhere, with a light scatter of shell-churned rubble for texture.
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                tiles[y][x] = '.';
            }
        }
        for (int i = 0; i < 900; i++) {
            blob(random.nextInt(SIZE), random.nextInt(SIZE), 1 + random.nextInt(2), ':', 0.5f);
        }

        // 2. The ruined city, straddling what will be the river band.
        //
        // The first pass scattered small wall blocks and the result read as debris, not as a
        // place: a city is streets and the blocks between them, even after the bombs. So the
        // band gets a street grid, and each cell of the grid rolls for what is left standing -
        // a shell of walls around a bombed-out rubble core, a block collapsed entirely, or a
        // lot the fire cleared. Density falls off away from the map's waist, so the districts
        // thin into outskirts instead of ending at a line.
        city(112, 176);

        // 3. Ore. Home fields first: two per side, close enough to hold from the base.
        oreField(30, 108, 6);
        oreField(32, 148, 5);
        oreField(224, 108, 6);
        oreField(222, 148, 5);
        // The contested fields, richer than anything at home. Off the river's line - the
        // first roll put the north field under the water that was drawn after it - and on
        // opposite banks, so each side has one it can reach without a crossing and one it
        // cannot.
        oreField(118, 20, 9);
        oreField(166, 236, 9);

        // 4. The river, meandering north to south. Drawn late so it cuts the city's edge
        // raggedly, which is what a fought-over waterfront looks like.
        for (int y = 0; y < SIZE; y++) {
            double centre = 142 + 16 * Math.sin(y / 31.0) + 7 * Math.sin(y / 9.7);
            int width = 5 + (y % 37 == 0 ? 1 : 0);
            for (int dx = -width / 2; dx <= width / 2; dx++) {
                set((int) Math.round(centre) + dx, y, '~');
            }
        }

        // 5. Roads, last, because a road overwrites whatever it is laid over - including the
        // river, and those three overwrites ARE the bridges. Everything that wants to cross
        // dry-shod funnels to one of three places, which is the whole operational question the
        // map asks.
        eastWestRoad(46);
        eastWestRoad(128);
        eastWestRoad(210);
        northSouthRoad(52);
        northSouthRoad(204);

        // 6. Clear the ground the bases stand on: no wall, water or rubble within the base
        // footprint, but ore stays - a home field inside the clearing is the point of it.
        clearForBase(20, 128);
        clearForBase(236, 128);
    }

    private void city(int left, int right) {
        int streetEvery = 9;
        for (int by = 8; by < SIZE - 8; by += streetEvery) {
            for (int bx = left; bx < right; bx += streetEvery) {
                float centrality = 1f - Math.abs(by - SIZE / 2f) / (SIZE / 2f);
                if (random.nextFloat() > 0.25f + 0.75f * centrality * centrality) {
                    continue;
                }
                int w = 5 + random.nextInt(3);
                int h = 5 + random.nextInt(3);
                int x = bx + 1 + random.nextInt(2);
                int y = by + 1 + random.nextInt(2);
                float fate = random.nextFloat();
                if (fate < 0.55f) {
                    // A shell: standing walls round a bombed-out core.
                    rect(x, y, w, h, '#');
                    rect(x + 1, y + 1, w - 2, h - 2, ':');
                    if (random.nextFloat() < 0.5f) {
                        // One wall breached, so infantry can fight through the block.
                        set(x + w / 2, y + h - 1, ':');
                        set(x + w / 2, y, ':');
                    }
                } else if (fate < 0.85f) {
                    // Collapsed flat.
                    blob(x + w / 2, y + h / 2, Math.max(w, h) / 2 + 1, ':', 0.8f);
                } else {
                    // Burnt out to the foundations: a clear lot inside the district.
                    rect(x, y, w, h, '.');
                    blob(x + w / 2, y + h / 2, 2, ':', 0.6f);
                }
            }
        }
        // The city's own north-south streets, one down each bank, so both waterfronts have a
        // spine to fight along and neither side's half of the city is the poor relation.
        for (int y = 12; y < SIZE - 12; y++) {
            int wobble = (int) Math.round(2 * Math.sin(y / 41.0));
            set(left + 4 + wobble, y, '=');
            set(left + 5 + wobble, y, '=');
            set(right - 6 - wobble, y, '=');
            set(right - 5 - wobble, y, '=');
        }
    }

    private void oreField(int cx, int cy, int radius) {
        blob(cx, cy, radius, '*', 0.85f);
    }

    private void eastWestRoad(int y) {
        for (int x = 0; x < SIZE; x++) {
            // A gentle waver so the road reads as laid by people rather than by a for-loop.
            int wobble = (int) Math.round(2 * Math.sin(x / 23.0));
            set(x, y + wobble, '=');
            set(x, y + wobble + 1, '=');
        }
    }

    private void northSouthRoad(int x) {
        for (int y = 44; y <= 212; y++) {
            int wobble = (int) Math.round(2 * Math.sin(y / 27.0));
            set(x + wobble, y, '=');
            set(x + wobble + 1, y, '=');
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

    /** The two claims a map must be able to make before it is worth writing to disk. */
    void validate() {
        int[][] spawns = {{20, 128}, {236, 128}};
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

        // One army must be able to walk to the other. Breadth-first over passable ground.
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
                    "The east spawn is unreachable from the west spawn: no crossing survived");
        }
    }

    void write(PrintStream out) {
        out.println("# Frontline - 256x256 1v1 front map for C&C: Wolfenstein.");
        out.println("# Generated by FrontlineGenerator (seed " + SEED + "), then reviewed and");
        out.println("# checked in. Regenerate with:");
        out.println("#   java -cp core/build/classes/java/main:core/build/classes/java/test \\");
        out.println("#     com.ccwolf.core.map.FrontlineGenerator > "
                + "core/src/main/resources/maps/frontline.map");
        out.println("# glyphs: . grass  = road  : rubble  * uranium  ~ water  # ruins");
        out.println("name Frontline");
        out.println("size " + SIZE + " " + SIZE);
        out.println("spawn 20 128");
        out.println("spawn 236 128");
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
