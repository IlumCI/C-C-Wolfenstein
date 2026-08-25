package com.ccwolf.core.map;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Random;

/**
 * Generates {@code aschefeld.map}: the 96-by-96 open ash plain.
 *
 * <p>The opposite question from the city maps. No river, no streets, almost no walls — just a
 * plain of ash drifts and shell craters with one rich uranium field dead centre and thin
 * pickings at home. There is nowhere to hide and nothing to funnel through: whoever wants the
 * money stands in the open to take it, which makes this the artillery map, the flanking map,
 * and the shortest matches in the rotation.
 *
 * <p>The crater belt around the middle is the map's only cover, and it is a ring — cover that
 * curls away from you as you follow it, never a line to sit behind. A few burnt farmsteads
 * scatter the outfield for the infantry to cling to.
 *
 * <p>Same contract as the other generated maps: the shipped file is the artifact, the seed is
 * a constant, and the generator proves the map is a map before writing a byte.
 */
public final class AschefeldGenerator {

    public static final int SIZE = 96;

    /** The shipped file was generated with this seed. Change it and the map is a new map. */
    private static final long SEED = 9L;

    private final char[][] tiles = new char[SIZE][SIZE];
    private final Random random = new Random(SEED);

    public static void main(String[] args) throws IOException {
        AschefeldGenerator generator = new AschefeldGenerator();
        generator.build();
        generator.validate();
        generator.write(System.out);
    }

    void build() {
        // 1. The plain, heavily textured with ash drifts: passable everywhere, pretty nowhere.
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                tiles[y][x] = '.';
            }
        }
        for (int i = 0; i < 420; i++) {
            blob(random.nextInt(SIZE), random.nextInt(SIZE), 1 + random.nextInt(3), ':', 0.4f);
        }

        // 2. Shell craters: rubble rings with churned centres, thickest in a belt around the
        // middle - the ground was fought over before this match ever started.
        for (int i = 0; i < 26; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 16 + random.nextDouble() * 12;
            crater(48 + (int) Math.round(Math.cos(angle) * dist),
                    48 + (int) Math.round(Math.sin(angle) * dist), 2 + random.nextInt(3));
        }
        for (int i = 0; i < 10; i++) {
            crater(random.nextInt(SIZE), random.nextInt(SIZE), 2 + random.nextInt(2));
        }

        // 3. Burnt farmsteads: the only walls on the map, small and lonely, for the infantry.
        for (int i = 0; i < 7; i++) {
            int x = 8 + random.nextInt(SIZE - 20);
            int y = 8 + random.nextInt(SIZE - 20);
            int w = 3 + random.nextInt(3);
            int h = 3 + random.nextInt(2);
            rect(x, y, w, h, '#');
            rect(x + 1, y + 1, w - 2, h - 2, ':');
            set(x + w / 2, y + h - 1, ':');
        }

        // 4. Ore: thin at home, everything in the middle. The centre field is the richest
        // single field in the game, and it is in the open on purpose - the map IS the field.
        oreField(12, 34, 3);
        oreField(12, 62, 3);
        oreField(84, 34, 3);
        oreField(84, 62, 3);
        oreField(48, 48, 10);
        oreField(48, 30, 4);
        oreField(48, 66, 4);

        // 5. One road, spawn to spawn through the middle: the axis everyone knows better
        // than to march straight down, and marches straight down anyway.
        for (int x = 0; x < SIZE; x++) {
            int wobble = (int) Math.round(2 * Math.sin(x / 21.0));
            set(x, 48 + wobble, '=');
            set(x, 49 + wobble, '=');
        }

        // 6. The ground the bases stand on.
        clearForBase(12, 48);
        clearForBase(84, 48);
    }

    /** A rubble ring with a churned centre: cover you can be shelled out of. */
    private void crater(int cx, int cy, int radius) {
        for (int y = cy - radius - 1; y <= cy + radius + 1; y++) {
            for (int x = cx - radius - 1; x <= cx + radius + 1; x++) {
                int dx = x - cx;
                int dy = y - cy;
                int d2 = dx * dx + dy * dy;
                if (d2 <= (radius + 1) * (radius + 1) && d2 >= (radius - 1) * (radius - 1)
                        && random.nextFloat() < 0.75f) {
                    set(x, y, ':');
                }
            }
        }
    }

    private void oreField(int cx, int cy, int radius) {
        blob(cx, cy, radius, '*', 0.85f);
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
        int[][] spawns = {{12, 48}, {84, 48}};
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
            throw new IllegalStateException("The east spawn is unreachable - on this map that"
                    + " would take real effort");
        }
    }

    void write(PrintStream out) {
        out.println("# Aschefeld - 96x96 1v1 open plain for C&C: Wolfenstein.");
        out.println("# Generated by AschefeldGenerator (seed " + SEED + "), then reviewed");
        out.println("# and checked in. Regenerate with:");
        out.println("#   java -cp core/build/classes/java/main:core/build/classes/java/test \\");
        out.println("#     com.ccwolf.core.map.AschefeldGenerator > "
                + "core/src/main/resources/maps/aschefeld.map");
        out.println("# glyphs: . grass  = road  : rubble  * uranium  ~ water  # ruins");
        out.println("name Aschefeld");
        out.println("size " + SIZE + " " + SIZE);
        out.println("spawn 12 48");
        out.println("spawn 84 48");
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
