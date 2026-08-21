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
