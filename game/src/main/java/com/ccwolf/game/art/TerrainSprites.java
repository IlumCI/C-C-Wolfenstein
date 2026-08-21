package com.ccwolf.game.art;

import com.ccwolf.core.map.Terrain;

/**
 * Ground tiles. Every terrain gets several seeded variants, picked by tile coordinate, so a
 * field of grass is not a field of one repeated pixel pattern.
 *
 * <p>Uranium seams are drawn as occult-green crystal clusters that thin out as they are mined,
 * which makes the state of the economy readable straight off the battlefield.
 */
public final class TerrainSprites {

    public static final int TILE = UnitSprites.TILE;
    public static final int VARIANTS = 4;

    /** How many richness steps an ore tile is drawn in. */
    public static final int ORE_LEVELS = 3;

    private TerrainSprites() {
    }

    public static PixelCanvas render(Terrain terrain, int variant) {
        PixelCanvas c = new PixelCanvas(TILE, TILE);
        int seed = variant * 977 + terrain.ordinal() * 131;

        switch (terrain) {
            case ROAD:
                road(c, seed);
                break;
            case RUBBLE:
                rubble(c, seed);
                break;
            case WATER:
                water(c, seed, variant);
                break;
            case WALL:
                wall(c, seed);
                break;
            case ORE:
            case GRASS:
            default:
                grass(c, seed);
                break;
        }
        return c;
    }

    /** How many depths of dug ground are drawn, matching TileMap.MAX_COVER. */
    public static final int TRENCH_LEVELS = 4;

    /**
     * Digs a tile out, in place.
     *
     * <p>An overlay rather than its own tile set, because entrenchment can happen on any ground
     * a man can stand on and there is no sense authoring a trench-in-grass, a trench-in-rubble
     * and a trench-in-a-uranium-seam separately.
     *
     * <p>The four steps are meant to be readable at a glance from a long way out, because
     * knowing which stretch of a line is properly dug and which is still a scrape is the whole
     * tactical use of looking at it: a scratch in the soil, a hollow with spoil beside it, a
     * proper trough with a parapet, and finally a revetted trench with timber in the walls. The
     * spoil always goes on the north lip, so a whole line reads as facing one way.
     *
     * @param level dug depth above the ground's own, 1 to {@link #TRENCH_LEVELS}
     */
    public static void entrench(PixelCanvas c, int level, int variant) {
        if (level <= 0) {
            return;
        }
        int seed = variant * 331 + level * 17;
        int[] dirt = WolfPalette.DIRT;
        int depth = Math.min(TRENCH_LEVELS, level);

        // Always cut clean across the tile, so that neighbouring dug tiles join into one
        // trench rather than a row of separate pits. The inset narrows as the work goes on,
        // which is what makes a scrape read as a scratch and a finished trench as a gap.
        int left = Math.max(0, 4 - depth);
        int right = TILE - left - 1;
        int height = 3 + depth * 2;
        int top = TILE / 2 - height / 2;
        int bottom = top + height - 1;

        // Spoil thrown up on the north lip. Bright, because this is the part that catches the
        // light and it is what makes a trench visible from a long way out.
        c.hLine(left, right, top - 1, WolfPalette.shade(dirt, 0));
        c.speckle(left, top - 2, right - left + 1, 2,
                WolfPalette.shade(dirt, 1), seed, 2);

        // The cut. Walls one shade darker than the floor is the wrong way round physically and
        // the right way round to look at: a dark rim against bright spoil is what reads as a
        // hole, and the floor has to stay light enough for a man standing in it to be seen.
        c.rect(left, top, right - left + 1, height, WolfPalette.shade(dirt, 3));
        c.hLine(left, right, top, WolfPalette.shade(dirt, 4));
        c.hLine(left, right, bottom, WolfPalette.shade(dirt, 4));
        c.speckle(left + 1, top + 1, right - left - 1, height - 2,
                WolfPalette.shade(dirt, 2), seed + 5, 5);

        if (depth >= 3) {
            // A parapet on the south lip: the thing an attacker has to come over.
            c.hLine(left, right, bottom + 1, WolfPalette.shade(dirt, 1));
            c.speckle(left, bottom + 1, right - left + 1, 2,
                    WolfPalette.shade(dirt, 0), seed + 11, 3);
        }
        if (depth >= 4) {
            // Revetment and sandbags — the marks of a position meant to be kept rather than
            // occupied. Both run along the trench rather than across it: anything drawn across
            // the cut at this size reads as a fence standing in it.
            int[] timber = WolfPalette.LEATHER;
            c.hLine(left + 1, right - 1, top + 1, WolfPalette.shade(timber, 2));
            c.hLine(left + 1, right - 1, bottom - 1, WolfPalette.shade(timber, 3));
            for (int x = left; x + 4 < right; x += 6) {
                c.rect(x + 1, bottom + 1, 4, 2, WolfPalette.shade(WolfPalette.BONE, 3));
                c.px(x + 1, bottom + 1, WolfPalette.shade(WolfPalette.BONE, 2));
            }
        }
    }

    /** Ore is drawn as an overlay on grass so a mined-out seam fades back into the field. */
    public static PixelCanvas renderOre(int variant, int level) {
        PixelCanvas c = render(Terrain.GRASS, variant);
        int seed = variant * 613 + level * 71;
        int clusters = level >= 2 ? 6 : (level == 1 ? 4 : 2);

        // A faint glow in the grass under the seam, laid down before the shards.
        if (level >= 1) {
            c.speckle(0, 0, TILE, TILE, WolfPalette.shade(WolfPalette.OCCULT, 3),
                    seed + 3, level >= 2 ? 7 : 14);
        }

        for (int i = 0; i < clusters; i++) {
            int x = 4 + ((seed + i * 47) % (TILE - 8));
            int y = 6 + ((seed + i * 83) % (TILE - 9));
            int height = 4 + ((seed + i * 31) % 3) + (level >= 2 ? 1 : 0);
            shard(c, x, y + height, height);
            // Clusters, not lone spikes: a smaller shard leaning against the main one.
            if (level >= 1) {
                shard(c, x + 3, y + height, Math.max(2, height - 2));
            }
        }
        return c;
    }

    /**
     * One uranium shard: a lit left facet, a shaded right one and a dark footing, so it reads
     * as a crystal with volume rather than as a green mark on the grass.
     */
    private static void shard(PixelCanvas c, int x, int baseY, int height) {
        int[] ore = WolfPalette.OCCULT;

        for (int i = 0; i < height; i++) {
            int halfWidth = Math.max(0, (height - i) / 2);
            int shade = i < height / 2 ? 2 : 1;
            c.hLine(x - halfWidth, x + halfWidth, baseY - i, WolfPalette.shade(ore, shade));
        }
        // Lit facet down the left, shadow down the right.
        c.vLine(x - 1, baseY - height + 2, baseY - 1, WolfPalette.shade(ore, 0));
        c.vLine(x + 1, baseY - height + 2, baseY, WolfPalette.shade(ore, 3));
        c.px(x, baseY - height, WolfPalette.shade(ore, 0));
        // Footing in the soil.
        c.hLine(x - 2, x + 2, baseY + 1, WolfPalette.shade(ore, 4));
    }

    private static void grass(PixelCanvas c, int seed) {
        c.fill(WolfPalette.shade(WolfPalette.GRASS, 2));
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(WolfPalette.GRASS, 1), seed, 5);
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(WolfPalette.GRASS, 3), seed + 7, 6);

        // A few upright blades so the ground has a direction to it.
        for (int i = 0; i < 6; i++) {
            int x = (seed + i * 53) % TILE;
            int y = (seed + i * 29) % (TILE - 3);
            c.vLine(x, y, y + 2, WolfPalette.shade(WolfPalette.GRASS, 0));
        }
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(WolfPalette.DIRT, 3), seed + 19, 41);
    }

    private static void road(PixelCanvas c, int seed) {
        c.fill(WolfPalette.shade(WolfPalette.DIRT, 3));

        // Cobbles in staggered courses, a few of them missing.
        int stone = 0;
        for (int row = 0; row * 4 < TILE; row++) {
            int offset = (row % 2) * 2;
            for (int col = -1; col * 6 < TILE; col++) {
                int x = col * 6 + offset;
                int y = row * 4;
                stone++;
                if ((seed + stone * 31) % 13 == 0) {
                    continue; // A missing cobble, showing the dirt bed.
                }
                int shade = 1 + ((seed + stone * 17) % 3);
                c.rect(x, y, 5, 3, WolfPalette.shade(WolfPalette.COBBLE, shade));
                c.hLine(x, x + 4, y, WolfPalette.shade(WolfPalette.COBBLE, shade - 1));
            }
        }
    }

    private static void rubble(PixelCanvas c, int seed) {
        c.fill(WolfPalette.shade(WolfPalette.DIRT, 3));
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(WolfPalette.DIRT, 2), seed, 4);

        // Broken masonry lying where it fell.
        for (int i = 0; i < 9; i++) {
            int x = (seed + i * 43) % (TILE - 4);
            int y = (seed + i * 61) % (TILE - 3);
            int w = 2 + ((seed + i) % 3);
            c.rect(x, y, w, 2, WolfPalette.shade(WolfPalette.STONE, 2));
            c.hLine(x, x + w - 1, y, WolfPalette.shade(WolfPalette.STONE, 1));
            c.hLine(x, x + w - 1, y + 1, WolfPalette.shade(WolfPalette.STONE, 3));
        }
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(WolfPalette.SMOKE, 4), seed + 11, 17);
    }

    private static void water(PixelCanvas c, int seed, int variant) {
        c.rampVertical(0, 0, TILE, TILE, WolfPalette.WATER, 1, 2);

        // Wave dashes, offset per variant so a river looks like it is moving.
        for (int i = 0; i < 7; i++) {
            int y = (i * 4 + variant * 2) % TILE;
            int x = (seed + i * 37) % (TILE - 6);
            c.hLine(x, x + 4, y, WolfPalette.shade(WolfPalette.WATER, 0));
            c.hLine(x + 1, x + 3, y + 1, WolfPalette.shade(WolfPalette.WATER, 3));
        }
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(WolfPalette.WATER, 4), seed + 5, 23);
    }

    private static void wall(PixelCanvas c, int seed) {
        c.fill(WolfPalette.shade(WolfPalette.STONE, 4));

        // Ruined masonry: heavy blocks, deep mortar, a dark top that reads as height.
        for (int row = 0; row * 6 < TILE; row++) {
            int offset = (row % 2) * 3;
            for (int col = -1; col * 8 < TILE; col++) {
                int x = col * 8 + offset;
                int y = row * 6;
                int shade = 1 + ((seed + row * 7 + col * 13) % 3);
                c.rect(x, y, 7, 5, WolfPalette.shade(WolfPalette.STONE, shade));
                c.hLine(x, x + 6, y, WolfPalette.shade(WolfPalette.STONE, shade - 1));
                c.vLine(x + 6, y, y + 4, WolfPalette.shade(WolfPalette.STONE, 4));
            }
        }
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(WolfPalette.SMOKE, 4), seed, 19);
    }
}
