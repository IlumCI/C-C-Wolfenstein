package com.ccwolf.game.art;

import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.map.Terrain;

/**
 * Ground tiles. Every terrain gets several seeded variants, picked by tile coordinate, so a
 * field of grass is not a field of one repeated pixel pattern.
 *
 * <p>Uranium seams are drawn as occult-green crystal clusters that thin out as they are mined,
 * which makes the state of the economy readable straight off the battlefield.
 */
public final class TerrainSprites {

    /**
     * How many pixels one tile of ground is authored at.
     *
     * <p>Chosen from the camera rather than from taste. {@code Camera.MAX_TILE_PX} is 96, so at
     * full zoom a tile occupies ninety-six screen pixels; anything authored below that is being
     * upscaled, and the old thirty-two was being upscaled three times over. A hundred and
     * twenty-eight is the first power of two above ninety-six, so the ground is never stretched
     * and there is a little headroom left.
     *
     * <p>Deliberately no longer tied to {@code UnitSprites.TILE}. The two answer different
     * questions — how finely the ground is drawn, and how big a man is — and tying them meant
     * neither could move without the other.
     *
     * <p>Cost is quadratic, which is why {@code TerrainCache} does not bake blocks at this
     * resolution when the camera is far away: sixteen times the pixels of the old size, and the
     * whole point of the block cache was that resident memory tracks what is visible.
     */
    public static final int TILE = 128;
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
    public static final int TRENCH_LEVELS = 5;

    /**
     * Digs a tile out, in place.
     *
     * <p>An overlay rather than its own tile set, because entrenchment can happen on any ground
     * a man can stand on and there is no sense authoring a trench-in-grass, a trench-in-rubble
     * and a trench-in-a-uranium-seam separately.
     *
     * <p>The five steps are meant to be readable at a glance from a long way out, because
     * knowing which stretch of a line is properly dug and which is still a scrape is the whole
     * tactical use of looking at it: a scratch in the soil, a hollow with spoil beside it, a
     * proper trough with a parapet, a revetted trench with timber in the walls, and finally a
     * roofed dugout. The spoil always goes on the north lip, so a whole line reads as facing
     * one way.
     *
     * <p>The fifth step has to read as <em>covered</em> rather than as merely deeper, because
     * that is exactly what it is worth in the rules: a roof stops what comes down and nothing
     * that comes flat. So it is drawn as timber laid across the cut with earth banked over it
     * and one dark mouth left open — the only step whose floor you cannot see into, which is
     * the whole point of it.
     *
     * @param level dug depth above the ground's own, 1 to {@link #TRENCH_LEVELS}
     */
    public static void entrench(PixelCanvas c, int level, int variant) {
        entrench(c, level, variant, null);
    }

    /**
     * Digs a tile out, in place, in the idiom of whoever cut it.
     *
     * <p>The two sides do not build the same thing and should not look as though they do. The
     * Kreisau Circle digs: earth, timber, sandbags, a shovel and whatever the farm had. The
     * Regime pours: shuttered concrete, steel plate, and at the deepest level a bunker that was
     * never going to be moved. Both are cut clean across the tile so that neighbouring dug tiles
     * join into one work rather than a row of separate pits.
     *
     * <p>Both idioms are lit from the north-west and built out of the same four cues, in this
     * order, because that order is what makes a flat grid of pixels read as a hole: an ambient
     * shadow gathering in the corners of the cut, a hard dark rim on the near lip, a bright
     * catch on the far lip where the light lands, and a cast shadow thrown from the parapet onto
     * the floor. Depth is drawn, not implied.
     *
     * @param level dug depth above the ground's own, 1 to {@link #TRENCH_LEVELS}
     * @param builder which side cut it, or null for ground of no particular allegiance
     */
    public static void entrench(PixelCanvas c, int level, int variant, Faction builder) {
        if (level <= 0) {
            return;
        }
        int depth = Math.min(TRENCH_LEVELS, level);
        int seed = variant * 331 + depth * 17;

        // The cut, widening and deepening together so a scrape reads as a scratch and a
        // finished work reads as a gap in the ground.
        int inset = Math.max(0, 16 - depth * 4);
        int left = inset;
        int right = TILE - inset - 1;
        int height = 20 + depth * 12;
        int top = TILE / 2 - height / 2;
        int bottom = top + height - 1;

        if (builder == Faction.REGIME) {
            concreteWorks(c, depth, seed, left, right, top, bottom);
        } else {
            earthWorks(c, depth, seed, left, right, top, bottom);
        }
    }

    /**
     * How far a cast shadow reaches onto the floor of a cut this deep, in pixels.
     *
     * <p>The single most effective depth cue available: a band of shade under the near wall,
     * growing with the wall. Without it the floor is a flat colour and the whole thing reads as
     * a painted stripe however dark the rim is drawn.
     */
    private static int shadowReach(int depth) {
        return 2 + depth * 3;
    }

    /** Earth, timber and sandbags. What men with shovels make. */
    private static void earthWorks(PixelCanvas c, int depth, int seed,
                                   int left, int right, int top, int bottom) {
        int[] dirt = WolfPalette.DIRT;
        int[] timber = WolfPalette.LEATHER;
        int width = right - left + 1;
        int height = bottom - top + 1;

        // Spoil thrown up on the north lip, and thrown properly: a mound with its own lit crest
        // and its own shadow, not a line. This is the part visible from furthest away.
        int spoil = 3 + depth * 2;
        c.rect(left, top - spoil, width, spoil, WolfPalette.shade(dirt, 2));
        c.hLine(left, right, top - spoil, WolfPalette.shade(dirt, 0));
        c.hLine(left, right, top - spoil + 1, WolfPalette.shade(dirt, 1));
        c.speckle(left, top - spoil, width, spoil, WolfPalette.shade(dirt, 1), seed + 2, 3);
        c.speckle(left, top - spoil, width, spoil, WolfPalette.shade(dirt, 3), seed + 9, 4);

        // The floor, darkest at the walls and lightest down the middle, so the eye reads a
        // trough rather than a slot.
        c.rect(left, top, width, height, WolfPalette.shade(dirt, 2));
        c.speckle(left, top, width, height, WolfPalette.shade(dirt, 3), seed + 5, 5);
        c.speckle(left + 2, top + height / 3, width - 4, height / 3,
                WolfPalette.shade(dirt, 1), seed + 15, 6);

        // Near lip: hard and dark, the edge nearest the viewer.
        c.hLine(left, right, top, WolfPalette.shade(dirt, 4));
        c.hLine(left, right, top + 1, WolfPalette.shade(dirt, 4));
        // Cast shadow from that lip onto the floor.
        for (int i = 0; i < shadowReach(depth); i++) {
            c.hLine(left, right, top + 2 + i, WolfPalette.shade(dirt, i < 3 ? 4 : 3));
        }
        // Far lip: where the light actually lands.
        c.hLine(left, right, bottom, WolfPalette.shade(dirt, 4));
        c.hLine(left, right, bottom - 1, WolfPalette.shade(dirt, 1));
        c.hLine(left, right, bottom - 2, WolfPalette.shade(dirt, 0));

        // Ambient occlusion gathering in the ends of the cut.
        for (int i = 0; i < 6; i++) {
            c.vLine(left + i, top, bottom, WolfPalette.shade(dirt, 4));
            c.vLine(right - i, top, bottom, WolfPalette.shade(dirt, 4));
            if (i > 2) {
                break;
            }
        }

        if (depth >= 3) {
            // A parapet on the south lip: the thing an attacker has to come over, with its own
            // lit crest so it stands proud of the ground behind it.
            int parapet = 2 + depth;
            c.rect(left, bottom + 1, width, parapet, WolfPalette.shade(dirt, 2));
            c.hLine(left, right, bottom + 1, WolfPalette.shade(dirt, 1));
            c.hLine(left, right, bottom + parapet, WolfPalette.shade(dirt, 4));
            c.speckle(left, bottom + 1, width, parapet,
                    WolfPalette.shade(dirt, 0), seed + 11, 5);
        }

        if (depth >= 4) {
            // Revetment: posts driven into both walls with rails behind them. Along the trench
            // rather than across it - anything drawn across the cut reads as a fence in it.
            for (int x = left + 4; x < right - 3; x += 11) {
                c.vLine(x, top + 2, top + 7, WolfPalette.shade(timber, 3));
                c.px(x, top + 2, WolfPalette.shade(timber, 1));
                c.vLine(x, bottom - 7, bottom - 2, WolfPalette.shade(timber, 3));
                c.px(x, bottom - 7, WolfPalette.shade(timber, 1));
            }
            c.hLine(left + 2, right - 2, top + 4, WolfPalette.shade(timber, 2));
            c.hLine(left + 2, right - 2, bottom - 5, WolfPalette.shade(timber, 2));

            // Duckboards down the floor, which is what stops it reading as bare mud.
            int boardTop = top + height / 2 - 4;
            for (int x = left + 3; x < right - 6; x += 9) {
                c.rect(x, boardTop, 7, 8, WolfPalette.shade(timber, 3));
                c.hLine(x, x + 6, boardTop, WolfPalette.shade(timber, 2));
                c.hLine(x, x + 6, boardTop + 7, WolfPalette.shade(timber, 4));
            }

            // Sandbags along the parapet: two courses, staggered, each one lit on top.
            int bagY = bottom + 2;
            for (int course = 0; course < 2; course++) {
                int offset = (course & 1) * 7;
                for (int x = left + offset; x + 12 < right; x += 14) {
                    int y = bagY + course * 5;
                    c.ellipse(x + 6, y + 2, 7, 3, WolfPalette.shade(WolfPalette.BONE, 3));
                    c.ellipse(x + 6, y + 1, 6, 2, WolfPalette.shade(WolfPalette.BONE, 2));
                    c.hLine(x + 2, x + 10, y - 1, WolfPalette.shade(WolfPalette.BONE, 1));
                }
            }
        }

        if (depth >= 5) {
            roof(c, left, right, top, bottom, seed);
        }
    }

    /**
     * Lays a roof over a finished trench, turning it into a dugout.
     *
     * <p>The Resistance's fifth level, and the counterpart to the Regime's bunker: the same
     * problem answered by digging deeper and covering it rather than by pouring something new.
     * It must read as a lid over a hole - a raised bank with baulk ends showing under the lip
     * and one mouth left open - where the bunker reads as a block standing on the ground.
     *
     * <p>An earlier attempt drew the baulks full height across the cut and read as a picket
     * fence standing in the trench, which is the opposite of the point. They belong at the lip,
     * where the ends of the timbers would actually show.
     */
    private static void roof(PixelCanvas c, int left, int right, int top, int bottom, int seed) {
        int[] timber = WolfPalette.LEATHER;
        int[] dirt = WolfPalette.DIRT;
        int width = right - left + 1;
        int height = bottom - top + 1;

        // Earth heaped over the top, lit from the north-west, with a hard shadow along the
        // south edge so it stands proud of the ground rather than lying flush with it.
        c.rect(left, top, width, height, WolfPalette.shade(dirt, 1));
        c.speckle(left, top, width, height, WolfPalette.shade(dirt, 2), seed + 23, 5);
        c.speckle(left, top, width, height, WolfPalette.shade(dirt, 0), seed + 37, 11);
        c.hLine(left, right, top, WolfPalette.shade(dirt, 0));
        c.hLine(left, right, top + 1, WolfPalette.shade(dirt, 0));
        for (int i = 0; i < 5; i++) {
            c.hLine(left, right, bottom - i, WolfPalette.shade(dirt, i < 2 ? 4 : 3));
        }

        // Beam ends under the north lip: short, and only at the lip.
        for (int x = left + 3; x <= right - 3; x += 9) {
            c.rect(x, top + 2, 4, 5, WolfPalette.shade(timber, 3));
            c.hLine(x, x + 3, top + 2, WolfPalette.shade(timber, 1));
            c.hLine(x, x + 3, top + 6, WolfPalette.shade(timber, 4));
        }

        // The mouth: inset from the tile edge, because flush it merges with the neighbouring
        // tile and reads as that tile's shadow instead of as a way in. Recessed with a lit
        // lintel, a black interior and a shadow thrown back onto the earth beside it.
        int mouthW = Math.max(10, width / 5);
        int mouthX = left + width / 6;
        int mouthTop = top + 9;
        int mouthBottom = bottom - 7;
        if (mouthBottom > mouthTop + 4) {
            c.rect(mouthX - 3, mouthTop - 3, mouthW + 6, mouthBottom - mouthTop + 6,
                    WolfPalette.shade(dirt, 4));
            c.rect(mouthX, mouthTop, mouthW, mouthBottom - mouthTop + 1,
                    WolfPalette.shade(WolfPalette.NIGHT, 0));
            c.hLine(mouthX - 3, mouthX + mouthW + 2, mouthTop - 3,
                    WolfPalette.shade(timber, 1));
            c.hLine(mouthX - 3, mouthX + mouthW + 2, mouthTop - 2,
                    WolfPalette.shade(timber, 2));
            c.vLine(mouthX - 2, mouthTop - 2, mouthBottom + 2, WolfPalette.shade(timber, 2));
            c.vLine(mouthX + mouthW + 1, mouthTop - 2, mouthBottom + 2,
                    WolfPalette.shade(timber, 4));
            // Steps down into it, catching the light on each nose.
            for (int i = 0; i < 3; i++) {
                c.hLine(mouthX + 1, mouthX + mouthW - 2, mouthBottom - i * 3,
                        WolfPalette.shade(dirt, 2));
            }
        }
    }

    /**
     * Shuttered concrete and steel plate. What a state that intends to stay pours.
     *
     * <p>The contrast with the earth line is the whole point and has to survive being seen
     * small: straight edges instead of ragged ones, a cold grey instead of a warm brown, board
     * marks from the shuttering instead of spoil, and at the deepest level a bunker rather than
     * a hole - something that was never going to be dug out again.
     */
    private static void concreteWorks(PixelCanvas c, int depth, int seed,
                                      int left, int right, int top, int bottom) {
        int[] concrete = WolfPalette.CONCRETE;
        int[] steel = WolfPalette.GUNMETAL;
        int[] dirt = WolfPalette.DIRT;
        int width = right - left + 1;
        int height = bottom - top + 1;

        // Spoil, but sparse and pushed into a bank: a machine did this, not a man with a spade.
        int bank = 2 + depth;
        c.rect(left, top - bank, width, bank, WolfPalette.shade(dirt, 3));
        c.hLine(left, right, top - bank, WolfPalette.shade(dirt, 2));

        // The channel itself.
        c.rect(left, top, width, height, WolfPalette.shade(concrete, 2));
        c.speckle(left, top, width, height, WolfPalette.shade(concrete, 3), seed + 3, 9);
        c.speckle(left, top, width, height, WolfPalette.shade(concrete, 1), seed + 8, 14);

        // Board marks from the shuttering, running across the pour. Regular, because that is
        // exactly what separates poured work from dug work at a glance.
        for (int x = left + 5; x < right - 2; x += 10) {
            c.vLine(x, top + 1, bottom - 1, WolfPalette.shade(concrete, 3));
            c.vLine(x + 1, top + 1, bottom - 1, WolfPalette.shade(concrete, 1));
        }

        // Near lip and its cast shadow, as before - the depth cue does not change with the
        // material, only what the material looks like.
        c.hLine(left, right, top, WolfPalette.shade(concrete, 0));
        for (int i = 0; i < shadowReach(depth); i++) {
            c.hLine(left, right, top + 1 + i, WolfPalette.shade(concrete, i < 2 ? 4 : 3));
        }
        c.hLine(left, right, bottom, WolfPalette.shade(concrete, 4));
        c.hLine(left, right, bottom - 1, WolfPalette.shade(concrete, 1));
        for (int i = 0; i < 3; i++) {
            c.vLine(left + i, top, bottom, WolfPalette.shade(concrete, 4));
            c.vLine(right - i, top, bottom, WolfPalette.shade(concrete, 4));
        }

        if (depth >= 3) {
            // A poured parapet with a steel coping along the top.
            int parapet = 3 + depth;
            c.rect(left, bottom + 1, width, parapet, WolfPalette.shade(concrete, 2));
            c.hLine(left, right, bottom + 1, WolfPalette.shade(concrete, 0));
            c.hLine(left, right, bottom + parapet, WolfPalette.shade(concrete, 4));
            c.hLine(left, right, bottom + 2, WolfPalette.shade(steel, 2));
        }

        if (depth >= 4) {
            // Steel plate bolted to the fighting wall, with firing slits cut in it.
            int plateTop = top + 3;
            c.rect(left + 2, plateTop, width - 4, 9, WolfPalette.shade(steel, 3));
            c.hLine(left + 2, right - 2, plateTop, WolfPalette.shade(steel, 1));
            c.hLine(left + 2, right - 2, plateTop + 8, WolfPalette.shade(steel, 4));
            for (int x = left + 8; x < right - 10; x += 16) {
                c.rect(x, plateTop + 3, 9, 3, WolfPalette.shade(WolfPalette.NIGHT, 0));
                c.hLine(x, x + 8, plateTop + 2, WolfPalette.shade(steel, 0));
            }
            // Bolt heads, which is what makes it read as plate rather than as paint.
            for (int x = left + 4; x < right - 3; x += 8) {
                c.px(x, plateTop + 1, WolfPalette.shade(steel, 0));
                c.px(x, plateTop + 7, WolfPalette.shade(steel, 0));
            }
            // A grating over the floor instead of duckboards.
            for (int x = left + 4; x < right - 3; x += 6) {
                c.vLine(x, top + height / 2 - 5, top + height / 2 + 4,
                        WolfPalette.shade(steel, 4));
            }
        }

        if (depth >= 5) {
            bunker(c, left, right, top, bottom, seed);
        }
    }

    /**
     * The Regime's fifth level: not a roof over a trench but a bunker sitting in one.
     *
     * <p>It has to read as a solid object standing above the ground rather than as a lid laid
     * over a hole, because that is the difference between the two sides at this depth - one dug
     * deeper and covered it, the other stopped digging and built. So it gets a lit top face, two
     * shaded side faces and a hard cast shadow on the ground to its south-east, which is as
     * close to three dimensions as a single tile can honestly get.
     */
    private static void bunker(PixelCanvas c, int left, int right, int top, int bottom,
                               int seed) {
        int[] concrete = WolfPalette.CONCRETE;
        int[] steel = WolfPalette.GUNMETAL;

        int height = bottom - top + 1;
        int bx = left + 2;
        int bw = (right - left + 1) - 4;
        int by = top - 6;
        int bh = height + 10;

        // Cast shadow first, offset south-east, so the block that follows sits on top of it.
        c.rect(bx + 5, by + 5, bw, bh, WolfPalette.shade(WolfPalette.NIGHT, 1));

        // The mass.
        c.rect(bx, by, bw, bh, WolfPalette.shade(concrete, 1));
        c.speckle(bx, by, bw, bh, WolfPalette.shade(concrete, 2), seed + 31, 11);
        c.speckle(bx, by, bw, bh, WolfPalette.shade(concrete, 0), seed + 43, 17);

        // Top face, lit; south and east faces, shaded. Three tones is what says "block".
        c.rect(bx, by, bw, 7, WolfPalette.shade(concrete, 0));
        c.hLine(bx, bx + bw - 1, by, WolfPalette.shade(concrete, 0));
        c.rect(bx, by + bh - 6, bw, 6, WolfPalette.shade(concrete, 3));
        c.rect(bx + bw - 5, by, 5, bh, WolfPalette.shade(concrete, 3));
        c.vLine(bx, by, by + bh - 1, WolfPalette.shade(concrete, 1));

        // Chamfered corners: poured forms have them, and they catch the light.
        for (int i = 0; i < 5; i++) {
            c.hLine(bx, bx + 4 - i, by + i, WolfPalette.shade(concrete, 0));
        }

        // The embrasure. Deeply recessed - a bright sill, a black slot, a shadow above it - and
        // the one thing on the tile that tells you which way it is meant to shoot.
        int ex = bx + bw / 4;
        int ew = bw / 2;
        int ey = by + bh / 2 - 3;
        c.rect(ex - 2, ey - 3, ew + 4, 12, WolfPalette.shade(concrete, 4));
        c.rect(ex, ey, ew, 7, WolfPalette.shade(WolfPalette.NIGHT, 0));
        c.hLine(ex, ex + ew - 1, ey + 7, WolfPalette.shade(concrete, 0));
        c.hLine(ex, ex + ew - 1, ey - 1, WolfPalette.shade(concrete, 4));
        // A barrel in the slot, because an empty loophole reads as a window.
        c.rect(ex + ew / 2 - 1, ey + 2, ew / 3, 3, WolfPalette.shade(steel, 2));
        c.hLine(ex + ew / 2 - 1, ex + ew / 2 - 1 + ew / 3, ey + 2,
                WolfPalette.shade(steel, 1));
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

    /**
     * A deterministic pseudo-random in [0, bound), from a seed and a counter.
     *
     * <p>Every recipe below needs a great many small decisions — where each blade of grass
     * leans, which cobble is missing — and at this resolution there are hundreds of them per
     * tile rather than a handful. The old recipes reached for expressions like
     * {@code (seed + i * 53) % TILE}, which is fine for six blades and visibly striped for six
     * hundred: a linear sequence modulo a power of two lands on a lattice.
     */
    private static int rand(int seed, int n, int bound) {
        int h = seed * 0x9E3779B1 + n * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0xC2B2AE35;
        h ^= h >>> 13;
        return (h & 0x7FFFFFFF) % bound;
    }

    /**
     * Grass, which in this valley means mud with grass still growing on some of it.
     *
     * <p>The standing art direction: nothing here has been maintained since the occupation, it
     * is never a clear day, and the ground is churned rather than green. So the base is laid
     * down as bare earth and the grass is put back on top of it in patches, which is the
     * opposite of how a summer field would be built and the right way round for this one.
     */
    private static void grass(PixelCanvas c, int seed) {
        int[] grass = WolfPalette.GRASS;
        int[] dirt = WolfPalette.DIRT;

        // Earth first. What is underneath is mud, and the green is what has survived on it.
        c.fill(WolfPalette.shade(dirt, 2));
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(dirt, 3), seed + 3, 3);

        // Broad patches of surviving turf, as soft blobs rather than a wash, so the boundary
        // between grass and mud is ragged at the scale the eye reads first.
        for (int i = 0; i < 22; i++) {
            int cx = rand(seed, i * 4, TILE);
            int cy = rand(seed, i * 4 + 1, TILE);
            int rx = TILE / 14 + rand(seed, i * 4 + 2, TILE / 6);
            int ry = TILE / 16 + rand(seed, i * 4 + 3, TILE / 7);
            c.ellipse(cx, cy, rx, ry, WolfPalette.shade(grass, 2));
            c.ellipse(cx - rx / 3, cy - ry / 3, rx / 2, ry / 2, WolfPalette.shade(grass, 1));
        }
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(grass, 3), seed + 7, 6);
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(dirt, 1), seed + 11, 9);

        // Blades, in quantity. At this size the ground needs a direction and a texture, and one
        // pixel per blade at four to nine tall is what gives it both without reading as noise.
        for (int i = 0; i < 260; i++) {
            int x = rand(seed, 900 + i * 3, TILE);
            int y = rand(seed, 901 + i * 3, TILE - 10);
            int height = 4 + rand(seed, 902 + i * 3, 6);
            int shade = (i & 3) == 0 ? 0 : 1;
            c.vLine(x, y, y + height, WolfPalette.shade(grass, shade));
            // A lean, so the field does not read as a bed of nails.
            if ((i & 1) == 0) {
                c.px(x + 1, y, WolfPalette.shade(grass, shade));
            }
        }

        // Dead tufts and trodden ground.
        for (int i = 0; i < 40; i++) {
            int x = rand(seed, 5000 + i * 2, TILE - 6);
            int y = rand(seed, 5001 + i * 2, TILE - 6);
            c.vLine(x, y, y + 3, WolfPalette.shade(dirt, 1));
            c.px(x + 1, y + 1, WolfPalette.shade(dirt, 0));
        }
        // Standing water in the ruts. Drawn as wet earth rather than as water: a puddle in a
        // churned field is the colour of what it is sitting in, and painting it from the river
        // ramp put bright blue coins all over the grass - which the contact sheet said at once.
        for (int i = 0; i < 7; i++) {
            int cx = rand(seed, 6000 + i * 3, TILE);
            int cy = rand(seed, 6001 + i * 3, TILE);
            int rx = 4 + rand(seed, 6002 + i * 3, 9);
            int ry = Math.max(2, rx / 3);
            c.ellipse(cx, cy, rx, ry, WolfPalette.shade(dirt, 4));
            c.ellipse(cx, cy, rx - 1, Math.max(1, ry - 1), WolfPalette.shade(dirt, 3));
            // One highlight along the north rim, which is the only thing that says it is wet.
            c.hLine(cx - rx / 2, cx + rx / 2, cy - ry, WolfPalette.shade(dirt, 1));
        }
    }

    /**
     * Cobbles, laid before the war and not repaired since.
     *
     * <p>Each stone is drawn as a stone — lit face, shadowed edge, a chipped corner or two —
     * rather than as a filled rectangle, because at twenty pixels across a rectangle reads as a
     * tile pattern and a stone reads as a road.
     */
    private static void road(PixelCanvas c, int seed) {
        int[] cobble = WolfPalette.COBBLE;
        int[] dirt = WolfPalette.DIRT;

        // The bed the stones are set into, which shows through wherever one is missing.
        c.fill(WolfPalette.shade(dirt, 3));
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(dirt, 4), seed + 2, 4);

        int stoneW = 20;
        int stoneH = 13;
        int stone = 0;
        for (int row = -1; row * stoneH < TILE + stoneH; row++) {
            int offset = (row & 1) * (stoneW / 2);
            for (int col = -1; col * stoneW < TILE + stoneW; col++) {
                stone++;
                int x = col * stoneW + offset;
                int y = row * stoneH;
                if (rand(seed, stone, 7) == 0) {
                    continue; // Gone: a hole with the bed showing through.
                }
                int shade = rand(seed, stone + 77, 4);
                int w = stoneW - 3 - rand(seed, stone + 31, 3);
                int h = stoneH - 3 - rand(seed, stone + 53, 2);

                c.rect(x, y, w, h, WolfPalette.shade(cobble, shade));
                // Lit from the north-west, like everything else in this game.
                c.hLine(x, x + w - 1, y, WolfPalette.shade(cobble, Math.max(0, shade - 1)));
                c.vLine(x, y, y + h - 1, WolfPalette.shade(cobble, Math.max(0, shade - 1)));
                c.hLine(x, x + w - 1, y + h - 1, WolfPalette.shade(cobble, Math.min(4, shade + 2)));
                // Chips out of the corners, which is most of what stops a course of stones
                // reading as brickwork.
                int chip = rand(seed, stone + 101, 4);
                if (chip == 0) {
                    c.rect(x + w - 3, y, 3, 2, WolfPalette.shade(dirt, 3));
                } else if (chip == 1) {
                    c.rect(x, y + h - 3, 2, 3, WolfPalette.shade(dirt, 3));
                }
                c.speckle(x + 1, y + 1, w - 2, h - 2,
                        WolfPalette.shade(cobble, Math.min(4, shade + 1)), seed + stone, 7);
            }
        }
        // Mud worked up into the joints, heaviest along one wheel rut.
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(dirt, 2), seed + 13, 23);
        int rut = TILE / 3 + rand(seed, 91, TILE / 3);
        c.speckle(0, rut, TILE, 12, WolfPalette.shade(dirt, 1), seed + 17, 5);
    }

    /**
     * What a building looks like after it has stopped being one.
     *
     * <p>Chunks of masonry with reinforcing bar bent out of them, ash over everything. The
     * rebar is the detail that says this was concrete rather than a quarry, and at this
     * resolution there is finally room to draw it.
     */
    private static void rubble(PixelCanvas c, int seed) {
        int[] stone = WolfPalette.STONE;
        int[] dirt = WolfPalette.DIRT;

        c.fill(WolfPalette.shade(dirt, 3));
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(dirt, 2), seed, 4);
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(dirt, 4), seed + 6, 6);

        for (int i = 0; i < 46; i++) {
            int w = 12 + rand(seed, i * 5, 26);
            int h = 8 + rand(seed, i * 5 + 1, 16);
            int x = rand(seed, i * 5 + 2, TILE - w);
            int y = rand(seed, i * 5 + 3, TILE - h);
            int shade = 1 + rand(seed, i * 5 + 4, 3);

            c.rect(x, y, w, h, WolfPalette.shade(stone, shade));
            c.hLine(x, x + w - 1, y, WolfPalette.shade(stone, Math.max(0, shade - 1)));
            c.vLine(x, y, y + h - 1, WolfPalette.shade(stone, Math.max(0, shade - 1)));
            c.hLine(x, x + w - 1, y + h - 1, WolfPalette.shade(stone, Math.min(4, shade + 1)));
            // A crack across the face of the bigger pieces.
            if (w > 20) {
                c.line(x + 2, y + h / 2, x + w - 3, y + h / 2 + 1,
                        WolfPalette.shade(stone, Math.min(4, shade + 2)));
            }
            // Reinforcing bar, bent where the slab broke.
            if (rand(seed, i * 5 + 9, 3) == 0) {
                int bx = x + w;
                int by = y + h / 2;
                c.line(bx, by, bx + 5, by - 4, WolfPalette.shade(WolfPalette.GUNMETAL, 2));
                c.line(bx + 5, by - 4, bx + 9, by - 2, WolfPalette.shade(WolfPalette.GUNMETAL, 1));
            }
        }
        // Ash, which sits on everything.
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(WolfPalette.SMOKE, 4), seed + 11, 13);
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(WolfPalette.SMOKE, 3), seed + 23, 37);
    }

    /**
     * Water, which in this valley is not something anyone would drink.
     *
     * <p>Dirty and polluted, per the standing direction — an oily sheen on top, scum gathering
     * at the edges, and whatever the war has put in it floating past. The wave dashes are
     * offset per variant so a river reads as moving rather than as a tiled texture.
     */
    private static void water(PixelCanvas c, int seed, int variant) {
        int[] water = WolfPalette.WATER;

        c.rampVertical(0, 0, TILE, TILE, water, 1, 3);
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(water, 4), seed + 5, 9);

        // The sheen: broad, slow bands lying across the current, in a colour that has no
        // business being in water, which is the point of it.
        for (int i = 0; i < 4; i++) {
            int y = (i * 31 + variant * 11) % TILE;
            int x = rand(seed, 300 + i, TILE);
            int w = TILE / 3 + rand(seed, 310 + i, TILE / 3);
            c.rect(x, y, Math.min(w, TILE - x), 3, WolfPalette.shade(WolfPalette.OCCULT, 3));
            c.hLine(x, Math.min(x + w, TILE - 1), y, WolfPalette.shade(WolfPalette.OCCULT, 2));
        }

        // Ripples.
        for (int i = 0; i < 26; i++) {
            int y = (i * 5 + variant * 3) % TILE;
            int x = rand(seed, 100 + i * 2, TILE - 24);
            int w = 8 + rand(seed, 101 + i * 2, 16);
            c.hLine(x, x + w, y, WolfPalette.shade(water, 0));
            c.hLine(x + 2, x + w - 2, y + 1, WolfPalette.shade(water, 3));
        }

        // Scum and floating debris.
        for (int i = 0; i < 14; i++) {
            int x = rand(seed, 400 + i * 3, TILE - 8);
            int y = rand(seed, 401 + i * 3, TILE - 4);
            c.rect(x, y, 3 + rand(seed, 402 + i * 3, 5), 2,
                    WolfPalette.shade(WolfPalette.DIRT, 2));
        }
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(WolfPalette.SMOKE, 3), seed + 29, 51);
    }

    /**
     * Standing masonry: heavy courses, deep mortar, and a dark top that reads as height.
     */
    private static void wall(PixelCanvas c, int seed) {
        int[] stone = WolfPalette.STONE;

        c.fill(WolfPalette.shade(stone, 4));

        int blockW = 30;
        int blockH = 21;
        for (int row = -1; row * blockH < TILE + blockH; row++) {
            int offset = (row & 1) * (blockW / 2);
            for (int col = -1; col * blockW < TILE + blockW; col++) {
                int x = col * blockW + offset;
                int y = row * blockH;
                int n = row * 31 + col * 17;
                int shade = 1 + rand(seed, n, 3);
                int w = blockW - 4;
                int h = blockH - 4;

                c.rect(x, y, w, h, WolfPalette.shade(stone, shade));
                c.hLine(x, x + w - 1, y, WolfPalette.shade(stone, Math.max(0, shade - 1)));
                c.vLine(x, y, y + h - 1, WolfPalette.shade(stone, Math.max(0, shade - 1)));
                c.hLine(x, x + w - 1, y + h - 1, WolfPalette.shade(stone, 4));
                c.vLine(x + w - 1, y, y + h - 1, WolfPalette.shade(stone, 4));

                // Weathering, and a chunk knocked out of one block in five.
                c.speckle(x + 2, y + 2, w - 4, h - 4,
                        WolfPalette.shade(stone, Math.min(4, shade + 1)), seed + n, 9);
                if (rand(seed, n + 7, 5) == 0) {
                    int cw = 5 + rand(seed, n + 8, 7);
                    c.rect(x + w - cw, y + h - 5, cw, 5, WolfPalette.shade(stone, 4));
                }
            }
        }
        c.speckle(0, 0, TILE, TILE, WolfPalette.shade(WolfPalette.SMOKE, 4), seed + 41, 29);
    }
}
