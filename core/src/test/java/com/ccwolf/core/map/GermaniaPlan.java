package com.ccwolf.core.map;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayDeque;

/**
 * Draws {@code germania.map}: the capital, by hand.
 *
 * <p>This class holds no dice. Its predecessor rolled the districts from a seed; the capital
 * deserved better, because it is not a battlefield that happens to have monuments in it — it
 * is one drawing, and a drawing is made of decisions. Every block, street, breach, ore seam
 * and omission below is placed by coordinate, on purpose, and will never change unless a
 * person changes it. The class is the pen, not the author: the shipped text file is the map,
 * this is the drawing that produces it, and the byte-pin test keeps the two honest.
 *
 * <p>The plan is the real one, at roughly twenty metres a tile. North to south: the river,
 * bent shallow across the Hall's basin; the Great Hall on its podium (its dome is drawn by
 * {@code MonumentSprites} — the M-field here is the footprint the simulation walks against);
 * the Great Plaza between the Palace and the Chancellery; the axis, six lanes of poured
 * highway, five kilometres of it; the park straddling the east-west axis; the Round Plaza in
 * its ring of ministries; the Arch with the avenue running beneath its roof; the South
 * Station and its yards. The monolith quarters flank it all on the state's fourteen-tile
 * module, with the blocks omitted where a district needed a square, a market, or a base's
 * ground — an omission is as deliberate as a block. And at the edges, old Berlin, block by
 * hand-ruined block.
 */
public final class GermaniaPlan {

    public static final int SIZE = 256;

    /** The axis' centreline. Everything on this map is placed relative to it. */
    private static final int AXIS = 128;

    private final char[][] tiles = new char[SIZE][SIZE];

    public static void main(String[] args) throws IOException {
        GermaniaPlan plan = new GermaniaPlan();
        plan.draw();
        plan.validate();
        plan.write(System.out);
    }

    void draw() {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                tiles[y][x] = '.';
            }
        }

        river();
        oldBerlin();
        park();
        monolithQuarters();
        greatHall();
        greatPlaza();
        roundPlaza();
        theAxis();
        triumphalArch();
        southStation();
        oreSeams();
        baseGround();
    }

    /** The Spree, drawn as five hand-set reaches, shallowest where it rounds the basin. */
    private void river() {
        reach(0, 44, 6);
        reach(44, 100, 5);
        reach(100, 156, 3);
        reach(156, 212, 5);
        reach(212, 256, 6);
        // Two bridges, and only two: the north bank is reachable, not convenient.
        for (int y = 0; y <= 16; y++) {
            set(58, y, '=');
            set(59, y, '=');
            set(198, y, '=');
            set(199, y, '=');
        }
    }

    private void reach(int x0, int x1, int centreY) {
        for (int x = x0; x < x1; x++) {
            for (int dy = -3; dy <= 3; dy++) {
                set(x, centreY + dy, '~');
            }
        }
    }

    /**
     * Old Berlin, at the western and eastern edges: each ruin placed by hand, in the street
     * pattern the old city actually had — perimeter blocks on irregular lots, not a grid.
     */
    private void oldBerlin() {
        int[][] shells = {
            // West bank of the old city, north to south.
            {8, 30, 7, 6}, {20, 32, 6, 7}, {34, 28, 8, 6}, {12, 44, 6, 6}, {28, 46, 7, 7},
            {42, 42, 6, 8}, {8, 60, 8, 7}, {22, 62, 6, 6}, {38, 58, 7, 6}, {14, 78, 7, 7},
            {30, 80, 8, 6}, {44, 76, 6, 7}, {10, 96, 6, 8}, {26, 98, 7, 6}, {40, 94, 6, 6},
            {8, 130, 8, 6}, {24, 128, 6, 7}, {38, 132, 7, 7}, {14, 148, 6, 6}, {30, 150, 8, 7},
            {44, 146, 6, 6}, {10, 168, 7, 7}, {26, 170, 6, 6}, {40, 166, 8, 7}, {12, 190, 6, 7},
            {28, 188, 7, 6}, {42, 192, 6, 6}, {16, 234, 7, 6}, {34, 238, 6, 7},
            // East bank, mirrored in spirit but not in fact - the old city was not symmetric.
            {206, 30, 6, 7}, {220, 28, 8, 6}, {236, 32, 6, 6}, {210, 46, 7, 7}, {226, 44, 6, 6},
            {240, 48, 7, 7}, {206, 64, 8, 6}, {222, 60, 6, 8}, {238, 64, 6, 6}, {212, 80, 6, 7},
            {228, 78, 7, 6}, {242, 82, 6, 7}, {208, 96, 7, 6}, {224, 96, 6, 7}, {240, 98, 7, 6},
            {206, 130, 6, 6}, {222, 128, 7, 7}, {238, 132, 6, 6}, {212, 148, 8, 6},
            {228, 150, 6, 7}, {242, 146, 6, 6}, {208, 168, 6, 7}, {224, 166, 7, 6},
            {240, 170, 6, 7}, {214, 190, 7, 7}, {230, 192, 6, 6}, {210, 236, 8, 6},
            {228, 240, 6, 6},
        };
        for (int[] s : shells) {
            ruinShell(s[0], s[1], s[2], s[3]);
        }
        // The rubble fields where whole streets came down: three in the west, two in the east.
        rubbleField(18, 112, 10, 6);
        rubbleField(36, 210, 8, 5);
        rubbleField(10, 214, 6, 5);
        rubbleField(218, 112, 9, 6);
        rubbleField(236, 212, 8, 5);
    }

    /** One ruined block: standing walls, a bombed-out core, and a breach on two faces. */
    private void ruinShell(int x, int y, int w, int h) {
        rect(x, y, w, h, '#');
        rect(x + 1, y + 1, w - 2, h - 2, ':');
        set(x + w / 2, y, ':');
        set(x + w / 2, y + h - 1, ':');
    }

    private void rubbleField(int x, int y, int w, int h) {
        rect(x, y, w, h, ':');
    }

    /** The park, straddling the east-west axis west of the Round Plaza. Paths, no buildings. */
    private void park() {
        for (int x = 60; x <= 116; x++) {
            set(x, 91, '=');
            set(x, 111, '=');
        }
        for (int y = 84; y <= 118; y++) {
            set(88, y, '=');
        }
    }

    /**
     * The monolith quarters, on the state's fourteen-tile module: a ten-by-ten superconcrete
     * block in each cell, four-tile alleys between, a two-tile service road through each
     * quarter's spine — and the cells left empty on purpose, listed here by name.
     */
    private void monolithQuarters() {
        // North-west quarter, between the old city and the plaza, above the park.
        quarter(60, 116, 52, 82, 88, 67, new int[][] {{74, 52}});
        // West quarter, south of the park down to the yards.
        quarter(60, 116, 122, 224, 88, 173, new int[][] {{74, 150}, {102, 192}});
        // East quarter, the government side, plaza to yards.
        quarter(140, 196, 52, 224, 168, 138,
                new int[][] {{168, 52}, {182, 52}, {154, 108}, {182, 164}});
        // The empty cells above are the quarter squares: the one market the state allowed
        // each district, and the ground the north-eastern base stands on.
    }

    private void quarter(int left, int right, int top, int bottom, int spineX, int spineY,
            int[][] omitted) {
        for (int by = top; by + 10 <= bottom; by += 14) {
            for (int bx = left; bx + 10 <= right; bx += 14) {
                boolean skip = false;
                for (int[] o : omitted) {
                    if (o[0] == bx && o[1] == by) {
                        skip = true;
                        break;
                    }
                }
                if (!skip) {
                    rect(bx, by, 10, 10, 'W');
                }
            }
        }
        for (int x = left; x <= right; x++) {
            setRoad(x, spineY);
            setRoad(x, spineY + 1);
        }
        for (int y = top; y <= bottom; y++) {
            setRoad(spineX, y);
            setRoad(spineX + 1, y);
        }
    }

    /** The Hall: its podium and the marble mass the dome sprite stands on. */
    private void greatHall() {
        rect(109, 10, 39, 46, '_');
        rect(112, 14, 32, 32, 'M');
    }

    /** The Great Plaza, and the two palaces that watch it. */
    private void greatPlaza() {
        rect(108, 56, 41, 22, '_');
        rect(86, 53, 16, 22, '_');
        rect(88, 55, 12, 18, 'M');
        rect(155, 53, 16, 22, '_');
        rect(157, 55, 12, 18, 'M');
    }

    /** The Round Plaza, in its ring of ministries. */
    private void roundPlaza() {
        disc(AXIS, 122, 11, '_');
        rect(110, 112, 6, 8, 'W');
        rect(141, 112, 6, 8, 'W');
        rect(110, 125, 6, 8, 'W');
        rect(141, 125, 6, 8, 'W');
    }

    /** The axis and the east-west axis: the two lines every other line defers to. */
    private void theAxis() {
        for (int y = 78; y <= 232; y++) {
            for (int x = AXIS - 3; x <= AXIS + 2; x++) {
                if (tiles[y][x] != 'M') {
                    tiles[y][x] = 'H';
                }
            }
        }
        for (int x = 0; x < SIZE; x++) {
            for (int dy = 0; dy < 3; dy++) {
                if (tiles[100 + dy][x] != 'M') {
                    tiles[100 + dy][x] = 'H';
                }
            }
        }
        // The ring avenues and the two cross streets, placed where the quarters need them.
        eastWestRoad(140);
        eastWestRoad(196);
        eastWestRoad(233);
        northSouthRoad(58);
        northSouthRoad(198);
    }

    /** The Arch: apron, legs, and the portal - the avenue keeps every lane beneath it. */
    private void triumphalArch() {
        rect(115, 170, 26, 14, '_');
        rect(119, 172, 5, 10, 'M');
        rect(132, 172, 5, 10, 'M');
        rect(124, 170, 8, 14, 'H');
    }

    /** The Station: forecourt, the terminal the sprite roofs, and the yards it feeds. */
    private void southStation() {
        rect(108, 226, 41, 9, '_');
        rect(111, 236, 35, 10, 'W');
        for (int y = 238; y <= 244; y += 3) {
            for (int x = 20; x <= 107; x++) {
                set(x, y, '=');
            }
            for (int x = 149; x <= 235; x++) {
                set(x, y, '=');
            }
        }
    }

    /** The uranium, where the drawing wants the fighting to go. */
    private void oreSeams() {
        disc(176, 44, 5, '*');
        disc(188, 54, 4, '*');
        disc(64, 210, 5, '*');
        disc(94, 214, 4, '*');
        disc(84, 88, 7, '*');
        disc(100, 115, 5, '*');
        disc(112, 134, 4, '*');
        disc(170, 240, 6, '*');
    }

    /** The ground the bases stand on, carved deliberately out of whatever was there. */
    private void baseGround() {
        clearForBase(176, 64);
        clearForBase(80, 220);
    }

    private void eastWestRoad(int y) {
        for (int x = 0; x < SIZE; x++) {
            setRoad(x, y);
            setRoad(x, y + 1);
        }
    }

    private void northSouthRoad(int x) {
        for (int y = 20; y < SIZE; y++) {
            setRoad(x, y);
            setRoad(x + 1, y);
        }
    }

    /** Roads defer to the state's stone and its avenue: they end at both, cut neither. */
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
        out.println("# HAND-DRAWN: this map holds no dice and will never be re-rolled. Every");
        out.println("# block, street, breach and seam was placed by coordinate in");
        out.println("# GermaniaPlan (test sources), which is the pen, not the author.");
        out.println("# The monuments' terrain here is their footprint; their faces are the");
        out.println("# MonumentSprites the renderer anchors over them.");
        out.println("# Redraw with:");
        out.println("#   java -cp core/build/classes/java/main:core/build/classes/java/test \\");
        out.println("#     com.ccwolf.core.map.GermaniaPlan > "
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
