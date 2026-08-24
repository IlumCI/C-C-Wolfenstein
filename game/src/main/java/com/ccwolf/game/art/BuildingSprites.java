package com.ccwolf.game.art;

import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;

/**
 * Pixel-art recipes for structures, authored at 32 pixels to the tile — so a Command Post is a
 * 96x96 sprite with room for stepped concrete mass, a guard tower, an entrance and a roof full
 * of vents and cable runs.
 *
 * <p>Each structure is built from its own architecture rather than a shared box: they share a
 * lighting convention (north-west highlight, south wall face, south-east shadow) and nothing
 * else. The two sides build the same things out of what they have — the Regime pours concrete
 * and hangs banners, the Resistance nails together timber, corrugated iron and sandbags — so a
 * base reads as theirs or yours from across the valley.
 */
public final class BuildingSprites {

    /**
     * The tile these recipes are composed in. Deliberately unchanged.
     *
     * <p>Everything below is written in whole pixels — a wall twelve high, a slit four by three
     * — and those numbers are the architecture. What changed is the canvas underneath: see
     * {@link #SCALE}.
     */
    public static final int TILE = 32;

    /**
     * How many real pixels each authored one becomes, so structures match the ground they stand
     * on. Terrain moved to 128 pixels a tile; a three-by-three Command Post is now a 384-pixel
     * sprite composed in the same 96-pixel space it always was.
     */
    private static final int SCALE = Math.max(1, TerrainSprites.TILE / TILE);

    /** 0 intact, 1 scarred, 2 burning. */
    public static final int DAMAGE_STATES = 3;

    private static final int OUTLINE = 0xFF0C0D09;

    private BuildingSprites() {
    }

    public static PixelCanvas render(BuildingType type, Faction faction, int damageState) {
        int w = type.tilesWide() * TILE;
        int h = type.tilesHigh() * TILE;
        PixelCanvas c = new PixelCanvas(w, h, SCALE);
        boolean regime = faction == Faction.REGIME;

        groundPad(c, w, h, regime);

        switch (type) {
            case COMMAND_POST:
                commandPost(c, regime, w, h);
                break;
            case GENERATOR:
                generator(c, regime, w, h);
                break;
            case REFINERY:
                refinery(c, regime, w, h);
                break;
            case BARRACKS:
                barracks(c, regime, w, h);
                break;
            case WAR_WORKS:
                warWorks(c, regime, w, h);
                break;
            case MG_NEST:
                mgNest(c, regime, w, h);
                break;
            case PAK_GUN:
                pakGun(c, regime, w, h);
                break;
            case HELIPAD:
                helipad(c, regime, w, h);
                break;
            case FLAK_TURRET:
            default:
                flakTurret(c, regime, w, h);
                break;
        }

        c.outline(OUTLINE);
        applyDamage(c, damageState, type.ordinal());
        return c;
    }

    /**
     * The helipad: a poured slab, a painted ring, and the fuel that makes it a target.
     *
     * <p>Deliberately flat - the one structure whose whole roof is its floor. The ring and the
     * marks are the faction's paint, and the bowser in the corner is what an attacking player
     * should learn to shoot first.
     */
    private static void helipad(PixelCanvas c, boolean regime, int w, int h) {
        int[] slab = regime ? WolfPalette.CONCRETE : WolfPalette.STONE;
        int cx = w / 2;
        int cy = h / 2;
        // The slab, proud of the ground pad by a lip.
        c.panel(3, 3, w - 6, h - 6, slab, 2);
        c.rectOutline(3, 3, w - 6, h - 6, WolfPalette.shade(slab, 0));
        c.rectOutline(4, 4, w - 8, h - 8, WolfPalette.shade(slab, 4));
        // Expansion joints.
        c.hLine(4, w - 5, cy, WolfPalette.shade(slab, 3));
        c.vLine(cx, 4, h - 5, WolfPalette.shade(slab, 3));
        // The painted ring and the H, in the faction's field colour.
        int paint = regime ? WolfPalette.shade(WolfPalette.BLOOD, 2)
                : WolfPalette.shade(WolfPalette.OLIVE, 1);
        c.ellipse(cx, cy, 18, 18, paint);
        c.ellipse(cx, cy, 15, 15, WolfPalette.shade(slab, 2));
        c.rect(cx - 8, cy - 8, 3, 17, paint);
        c.rect(cx + 6, cy - 8, 3, 17, paint);
        c.rect(cx - 5, cy - 1, 11, 3, paint);
        // Corner lamps.
        for (int[] corner : new int[][] {{6, 6}, {w - 8, 6}, {6, h - 8}, {w - 8, h - 8}}) {
            c.rect(corner[0], corner[1], 2, 2, WolfPalette.shade(WolfPalette.BRASS, 0));
        }
        // The fuel bowser, tucked on the east edge where fresh airframes roll past it.
        c.panel(w - 14, cy - 6, 10, 12, WolfPalette.GUNMETAL, 2);
        c.hLine(w - 14, w - 5, cy - 6, WolfPalette.shade(WolfPalette.BLOOD, 1));
        c.px(w - 12, cy, WolfPalette.shade(WolfPalette.BRASS, 1));
        c.line(w - 10, cy + 5, w - 6, cy + 8, WolfPalette.shade(WolfPalette.NIGHT, 1));
    }

    // --- shared materials and conventions --------------------------------------------------

    private static int[] walls(boolean regime) {
        return regime ? WolfPalette.CONCRETE : WolfPalette.LEATHER;
    }

    private static int[] roofs(boolean regime) {
        return regime ? WolfPalette.NIGHT : WolfPalette.OLIVE;
    }

    /** Hardstanding under the structure: gravel for the Regime, churned mud for the others. */
    private static void groundPad(PixelCanvas c, int w, int h, boolean regime) {
        c.groundShadow(w / 2 + 3, h - 5, w / 2 - 2, 6);
        int[] ground = regime ? WolfPalette.CONCRETE : WolfPalette.DIRT;
        c.ellipse(w / 2, h - 8, w / 2 - 3, h / 4, WolfPalette.shade(ground, 3));
        c.speckle(4, h / 2, w - 8, h / 2 - 2, WolfPalette.shade(ground, 2), 41, 7);
        c.speckle(4, h / 2, w - 8, h / 2 - 2, WolfPalette.shade(ground, 4), 17, 11);
        // Grit, at real resolution rather than in blocks, so the pad has a surface instead of
        // a pattern. This is the difference between a bigger picture and a better one.
        PixelCanvas f = c.fine();
        f.speckle(0, f.height() / 2, f.width(), f.height() / 2,
                WolfPalette.shade(ground, 1), 91, 23);
        f.speckle(0, f.height() / 2, f.width(), f.height() / 2,
                WolfPalette.shade(ground, 4), 53, 19);
    }

    /**
     * A wall face seen from the south: the material, its courses, and a shadow line where it
     * meets the ground. This is what gives every structure height.
     */
    private static void wallFace(PixelCanvas c, int x, int y, int w, int h, boolean regime) {
        int[] wall = walls(regime);
        c.rampVertical(x, y, w, h, wall, 1, 3);
        c.hLine(x, x + w - 1, y, WolfPalette.shade(wall, 0));
        c.hLine(x, x + w - 1, y + h - 1, WolfPalette.shade(wall, 4));

        if (regime) {
            // Shuttering marks: concrete poured in lifts, with tie holes.
            for (int by = y + 4; by < y + h - 2; by += 6) {
                c.hLine(x + 1, x + w - 2, by, WolfPalette.shade(wall, 3));
            }
            for (int bx = x + 5; bx < x + w - 3; bx += 11) {
                c.px(bx, y + 3, WolfPalette.shade(wall, 4));
                c.px(bx, y + 9, WolfPalette.shade(wall, 4));
            }
        } else {
            // Vertical planking with a nailed batten and the odd knot.
            for (int bx = x + 2; bx < x + w - 1; bx += 4) {
                c.vLine(bx, y + 1, y + h - 2, WolfPalette.shade(wall, 3));
                c.vLine(bx + 1, y + 1, y + h - 2, WolfPalette.shade(wall, 1));
            }
            c.hLine(x + 1, x + w - 2, y + 3, WolfPalette.shade(wall, 0));
            c.speckle(x + 1, y + 1, w - 2, h - 2, WolfPalette.shade(wall, 4), 23, 29);
        }
        weather(c, x, y, w, h, wall, regime);
    }

    /**
     * The fine pass over a wall: grain, joints, and the dirt that gathers where water runs.
     *
     * <p>Drawn one real pixel at a time over a face composed in the coarse grid. Everything here
     * is a line thinner than a composed mark can be, which is exactly why it belongs in this
     * pass and not in the one above.
     */
    private static void weather(PixelCanvas c, int x, int y, int w, int h, int[] wall,
                                boolean regime) {
        PixelCanvas f = c.fine();
        int s = c.scale();
        if (s == 1) {
            return;
        }
        int fx = x * s;
        int fy = y * s;
        int fw = w * s;
        int fh = h * s;

        // Grime gathering at the foot of the wall, heaviest in the corners.
        for (int i = 0; i < fh / 3; i++) {
            int alpha = fh / 3 - i;
            f.speckle(fx, fy + fh - 1 - i, fw, 1, WolfPalette.shade(wall, 4), 700 + i, 2 + alpha);
        }

        if (regime) {
            // Rain streaks below each lift joint, and hairline cracks running down from them.
            for (int i = 0; i < fw / 9; i++) {
                int sx = fx + (i * 37 + 11) % Math.max(1, fw - 2);
                int len = fh / 3 + (i * 13) % Math.max(1, fh / 2);
                f.vLine(sx, fy + fh - len, fy + fh - 2, WolfPalette.shade(WolfPalette.DIRT, 4));
                if ((i & 3) == 0) {
                    f.vLine(sx + 1, fy + fh - len, fy + fh - 2, WolfPalette.shade(wall, 4));
                }
            }
        } else {
            // Grain along each plank, and rust weeping from the nail heads.
            for (int i = 0; i < fw / 3; i++) {
                int sx = fx + (i * 17 + 5) % Math.max(1, fw - 1);
                int top = fy + (i * 29) % Math.max(1, fh / 2);
                f.vLine(sx, top, top + fh / 3, WolfPalette.shade(wall, 4));
            }
            for (int i = 0; i < fw / 12; i++) {
                int sx = fx + (i * 43 + 7) % Math.max(1, fw - 1);
                int top = fy + fh / 4;
                f.vLine(sx, top, top + 5, WolfPalette.shade(WolfPalette.BRASS, 3));
            }
        }
    }

    /** A flat roof with a parapet lip, lit from the north-west. */
    private static void flatRoof(PixelCanvas c, int x, int y, int w, int h, boolean regime) {
        int[] roof = roofs(regime);
        c.rampVertical(x, y, w, h, roof, 1, 2);
        c.hLine(x, x + w - 1, y, WolfPalette.shade(roof, 0));
        c.vLine(x, y, y + h - 1, WolfPalette.shade(roof, 0));
        c.hLine(x, x + w - 1, y + h - 1, WolfPalette.shade(roof, 4));
        c.vLine(x + w - 1, y, y + h - 1, WolfPalette.shade(roof, 4));
        c.speckle(x + 1, y + 1, w - 2, h - 2, WolfPalette.shade(roof, 3), 13, 9);

        // Felt seams running across the deck, and damp pooling along the low edge. One real
        // pixel wide, which is thinner than the composition grid can draw.
        PixelCanvas f = c.fine();
        int s = c.scale();
        if (s > 1) {
            for (int sy = (y + 3) * s; sy < (y + h - 1) * s; sy += 7 * s) {
                f.hLine(x * s + 1, (x + w) * s - 2, sy, WolfPalette.shade(roof, 4));
                f.hLine(x * s + 1, (x + w) * s - 2, sy + 1, WolfPalette.shade(roof, 1));
            }
            f.speckle(x * s, (y + h - 3) * s, w * s, 3 * s,
                    WolfPalette.shade(WolfPalette.DIRT, 4), 61, 5);
        }
    }

    /** A doorway with a lintel, a dark interior and a worn step. */
    private static void doorway(PixelCanvas c, int cx, int y, int width, int height,
                                boolean regime) {
        int x = cx - width / 2;
        c.rect(x, y, width, height, 0xFF101008);
        c.hLine(x - 1, x + width, y - 1, WolfPalette.shade(walls(regime), 0));
        c.vLine(x - 1, y, y + height - 1, WolfPalette.shade(walls(regime), 4));
        c.vLine(x + width, y, y + height - 1, WolfPalette.shade(walls(regime), 4));
        // A lamp over the door and a step below it.
        c.px(cx, y - 2, WolfPalette.shade(WolfPalette.FIRE, 1));
        c.hLine(x - 2, x + width + 1, y + height, WolfPalette.shade(WolfPalette.CONCRETE, 2));
        c.hLine(x - 2, x + width + 1, y + height + 1, WolfPalette.shade(WolfPalette.CONCRETE, 4));
    }

    /** Lit slit windows along a wall face. */
    private static void slits(PixelCanvas c, int x, int y, int count, int spacing) {
        for (int i = 0; i < count; i++) {
            int wx = x + i * spacing;
            c.rect(wx, y, 4, 3, 0xFF101008);
            c.hLine(wx, wx + 3, y - 1, WolfPalette.shade(WolfPalette.CONCRETE, 1));
            c.px(wx + 1, y + 1, WolfPalette.shade(WolfPalette.FIRE, 2));
        }
    }

    /** Sandbag revetment: each bag drawn, stacked in two courses. */
    private static void sandbags(PixelCanvas c, int x, int y, int w) {
        int[] bag = WolfPalette.BONE;
        for (int row = 0; row < 2; row++) {
            int offset = (row % 2) * 3;
            for (int bx = x + offset; bx < x + w - 5; bx += 7) {
                int by = y + row * 4;
                c.ellipse(bx + 3, by + 2, 4, 2, WolfPalette.shade(bag, 3));
                c.hLine(bx, bx + 6, by, WolfPalette.shade(bag, 2));
                c.hLine(bx, bx + 6, by + 3, WolfPalette.shade(bag, 4));
            }
        }
    }

    /** Regime banner: blood red, brass-topped pole, an original skull mark. */
    private static void banner(PixelCanvas c, int x, int y, int height) {
        c.vLine(x, y - 5, y + height, WolfPalette.shade(WolfPalette.BRASS, 2));
        c.px(x, y - 6, WolfPalette.shade(WolfPalette.BRASS, 0));
        c.px(x, y - 5, WolfPalette.shade(WolfPalette.BRASS, 0));

        int w = 10;
        c.rect(x + 1, y, w, height, WolfPalette.shade(WolfPalette.BLOOD, 1));
        c.vLine(x + 1, y, y + height, WolfPalette.shade(WolfPalette.BLOOD, 0));
        c.vLine(x + w, y, y + height, WolfPalette.shade(WolfPalette.BLOOD, 3));
        // Ragged hem.
        c.hLine(x + 1, x + w, y + height, WolfPalette.shade(WolfPalette.BLOOD, 3));
        c.px(x + 3, y + height + 1, WolfPalette.shade(WolfPalette.BLOOD, 2));
        c.px(x + 7, y + height + 1, WolfPalette.shade(WolfPalette.BLOOD, 2));

        // Skull mark: cranium, two sockets, a jaw.
        int cx = x + 5;
        int cy = y + height / 2 - 1;
        c.ellipse(cx, cy, 3, 3, WolfPalette.shade(WolfPalette.BONE, 0));
        c.px(cx - 1, cy, WolfPalette.shade(WolfPalette.BLOOD, 4));
        c.px(cx + 1, cy, WolfPalette.shade(WolfPalette.BLOOD, 4));
        c.hLine(cx - 2, cx + 2, cy + 3, WolfPalette.shade(WolfPalette.BONE, 1));
        c.px(cx, cy + 4, WolfPalette.shade(WolfPalette.BONE, 2));
    }

    /** A red band. Every Regime structure gets one; it is how you read a base at a glance. */
    private static void redBand(PixelCanvas c, int x, int y, int w) {
        c.hLine(x, x + w - 1, y, WolfPalette.shade(WolfPalette.BLOOD, 0));
        c.hLine(x, x + w - 1, y + 1, WolfPalette.shade(WolfPalette.BLOOD, 1));
        c.hLine(x, x + w - 1, y + 2, WolfPalette.shade(WolfPalette.BLOOD, 3));
    }

    /**
     * Dresses a bare roof so it does not read as a coloured field.
     *
     * <p>Regime roofs get expansion joints, ducting and cable trays; Resistance roofs get
     * camouflage netting, lashed tarpaulins and a water tank. Placement is seeded from the
     * rectangle, so a given structure always looks the same.
     */
    private static void roofDetail(PixelCanvas c, int x, int y, int w, int h, boolean regime,
                                   int seed) {
        int[] roof = roofs(regime);
        int[] metal = WolfPalette.GUNMETAL;

        if (regime) {
            // Expansion joints across the slab.
            for (int jy = y + 7; jy < y + h - 3; jy += 11) {
                c.hLine(x + 2, x + w - 3, jy, WolfPalette.shade(roof, 4));
                c.hLine(x + 2, x + w - 3, jy + 1, WolfPalette.shade(roof, 0));
            }
            for (int jx = x + 9; jx < x + w - 5; jx += 17) {
                c.vLine(jx, y + 2, y + h - 3, WolfPalette.shade(roof, 4));
            }
            // Ducting run with elbows.
            int dy = y + h / 2;
            c.rect(x + 4, dy, w - 12, 3, WolfPalette.shade(metal, 3));
            c.hLine(x + 4, x + w - 9, dy, WolfPalette.shade(metal, 1));
            c.rect(x + w - 10, dy - 4, 4, 8, WolfPalette.shade(metal, 2));
        } else {
            // Camouflage netting: irregular olive speckle in two tones over part of the roof.
            int netW = Math.max(8, w / 2);
            c.speckle(x + 3, y + 3, netW, h - 6, WolfPalette.shade(WolfPalette.OLIVE, 0),
                    seed, 3);
            c.speckle(x + 3, y + 3, netW, h - 6, WolfPalette.shade(WolfPalette.OLIVE, 4),
                    seed + 5, 4);
            // A lashed tarpaulin with a rope across it.
            c.panel(x + w - 22, y + 5, 16, 12, WolfPalette.LEATHER, 2);
            c.hLine(x + w - 22, x + w - 7, y + 10, WolfPalette.shade(WolfPalette.BONE, 3));
            c.vLine(x + w - 15, y + 5, y + 16, WolfPalette.shade(WolfPalette.BONE, 3));
            // Water tank on legs.
            c.ellipse(x + w - 12, y + h - 9, 6, 5, WolfPalette.shade(metal, 2));
            c.ellipse(x + w - 12, y + h - 10, 4, 3, WolfPalette.shade(metal, 1));
            c.px(x + w - 16, y + h - 5, WolfPalette.shade(metal, 4));
            c.px(x + w - 8, y + h - 5, WolfPalette.shade(metal, 4));
        }

        // Cable tray running to the edge, both sides build these.
        for (int cx2 = x + 4; cx2 < x + w - 4; cx2 += 3) {
            c.px(cx2, y + h - 5, WolfPalette.shade(metal, 1));
            c.px(cx2 + 1, y + h - 5, WolfPalette.shade(metal, 4));
        }
    }

    /** Roof furniture: a vent block with louvres. */
    private static void vent(PixelCanvas c, int x, int y, int w, int h, boolean regime) {
        int[] metal = WolfPalette.GUNMETAL;
        c.panel(x, y, w, h, metal, 2);
        for (int i = 1; i < h - 1; i += 2) {
            c.hLine(x + 1, x + w - 2, y + i, WolfPalette.shade(metal, 4));
        }
    }

    // --- Command Post ----------------------------------------------------------------------

    /**
     * A three-tile blockhouse: a stepped main mass, a guard tower with a searchlight at the
     * north-west, an entrance with steps and bollards, and a roof of vents and cable runs.
     */
    private static void commandPost(PixelCanvas c, boolean regime, int w, int h) {
        int[] roof = roofs(regime);
        int wallH = 12;
        int bodyTop = 18;
        int wallTop = h - wallH - 6;

        // Main mass.
        flatRoof(c, 6, bodyTop, w - 12, wallTop - bodyTop, regime);
        wallFace(c, 6, wallTop, w - 12, wallH, regime);

        // Stepped upper block, set back from the roof edge.
        flatRoof(c, 18, 8, w - 44, 22, regime);
        wallFace(c, 18, 30, w - 44, 6, regime);
        c.hLine(18, w - 27, 8, WolfPalette.shade(roof, 0));

        // Guard tower at the north-west corner, taller than everything else.
        int towerX = 4;
        int towerW = 20;
        c.rect(towerX + 3, 4, towerW, 30, 0x55000000);
        flatRoof(c, towerX, 2, towerW, 18, regime);
        wallFace(c, towerX, 20, towerW, 14, regime);
        slits(c, towerX + 3, 24, 2, 8);
        // Searchlight on the tower roof, pointing out over the map.
        c.ellipse(towerX + 10, 8, 5, 4, WolfPalette.shade(WolfPalette.GUNMETAL, 2));
        c.ellipse(towerX + 12, 8, 3, 3, WolfPalette.shade(WolfPalette.BONE, 1));
        c.px(towerX + 13, 8, WolfPalette.shade(WolfPalette.BONE, 0));

        // Dress the main roof, then add the heavier furniture on top of it.
        roofDetail(c, 6, 36, w - 12, wallTop - 38, regime, 11);
        vent(c, w - 30, 40, 12, 9, regime);
        vent(c, 14, wallTop - 16, 10, 8, regime);
        c.panel(w / 2 - 6, 40, 12, 9, WolfPalette.GUNMETAL, 3);
        c.hLine(w / 2 - 5, w / 2 + 4, 41, WolfPalette.shade(WolfPalette.GUNMETAL, 0));

        // Entrance: a recessed doorway with bollards either side.
        doorway(c, w / 2, wallTop + 3, 12, wallH - 4, regime);
        c.rect(w / 2 - 14, h - 10, 4, 6, WolfPalette.shade(WolfPalette.CONCRETE, 2));
        c.rect(w / 2 + 10, h - 10, 4, 6, WolfPalette.shade(WolfPalette.CONCRETE, 2));
        slits(c, 14, wallTop + 4, 2, 10);
        slits(c, w - 34, wallTop + 4, 2, 10);

        if (regime) {
            redBand(c, 6, wallTop - 4, w - 12);
            banner(c, 40, 36, 26);
            banner(c, w - 26, 36, 26);
        } else {
            sandbags(c, 6, h - 12, w - 12);
            // Radio mast lashed to the tower, guyed with wire.
            c.vLine(towerX + 17, 0, 18, WolfPalette.shade(WolfPalette.GUNMETAL, 1));
            c.hLine(towerX + 14, towerX + 20, 3, WolfPalette.shade(WolfPalette.GUNMETAL, 2));
            c.hLine(towerX + 15, towerX + 19, 7, WolfPalette.shade(WolfPalette.GUNMETAL, 3));
            c.line(towerX + 17, 4, towerX + 26, 20, WolfPalette.shade(WolfPalette.GUNMETAL, 4));
            // Camouflage netting over part of the roof.
            c.speckle(20, 12, 30, 16, WolfPalette.shade(WolfPalette.OLIVE, 0), 7, 4);
        }
    }

    // --- Generator ---------------------------------------------------------------------------

    /** A turbine hall: two stacks, a coil housing, transformers and cable spools. */
    private static void generator(PixelCanvas c, boolean regime, int w, int h) {
        int wallH = 12;
        int wallTop = h - wallH - 5;

        flatRoof(c, 5, 16, w - 10, wallTop - 16, regime);
        wallFace(c, 5, wallTop, w - 10, wallH, regime);

        // Two stacks rising past the roofline, sooty, with soot smears down their sides.
        int[] metal = WolfPalette.GUNMETAL;
        for (int i = 0; i < 2; i++) {
            int sx = 10 + i * 18;
            c.rect(sx + 2, 6, 9, 18, 0x55000000);
            c.panel(sx, 2, 9, 22, metal, 2);
            c.rect(sx, 2, 9, 3, WolfPalette.shade(WolfPalette.SMOKE, 3));
            c.hLine(sx, sx + 8, 2, WolfPalette.shade(WolfPalette.SMOKE, 1));
            c.speckle(sx, 5, 9, 18, WolfPalette.shade(WolfPalette.SMOKE, 4), 9 + i, 6);
            c.px(sx + 4, 0, WolfPalette.shade(WolfPalette.SMOKE, 1));
        }

        roofDetail(c, 5, 16, w - 10, wallTop - 16, regime, 23);

        // The coil: brass windings around a glowing core, the occult tech showing through.
        int coilX = w - 20;
        int coilY = 26;
        c.ellipse(coilX, coilY, 10, 9, WolfPalette.shade(metal, 3));
        c.ellipse(coilX, coilY, 8, 7, WolfPalette.shade(metal, 2));
        for (int r = 6; r > 1; r -= 2) {
            c.ellipse(coilX, coilY, r, r - 1, WolfPalette.shade(WolfPalette.BRASS, 2));
            c.ellipse(coilX, coilY, r - 1, r - 2, WolfPalette.shade(WolfPalette.OCCULT, 2));
        }
        c.ellipse(coilX, coilY - 1, 2, 2, WolfPalette.shade(WolfPalette.OCCULT, 0));
        // Arcs jumping to the terminals.
        c.line(coilX - 10, coilY + 8, coilX - 5, coilY + 3,
                WolfPalette.shade(WolfPalette.OCCULT, 1));
        c.line(coilX + 10, coilY + 8, coilX + 5, coilY + 3,
                WolfPalette.shade(WolfPalette.OCCULT, 1));

        // Transformer boxes and a cable spool on the hardstanding.
        c.panel(8, wallTop + 2, 10, 8, metal, 2);
        c.rivets(9, wallTop + 3, 8, 6, 3, WolfPalette.shade(metal, 0),
                WolfPalette.shade(metal, 4));
        c.ellipse(w - 12, h - 12, 6, 5, WolfPalette.shade(WolfPalette.LEATHER, 2));
        c.ellipse(w - 12, h - 12, 4, 3, WolfPalette.shade(WolfPalette.LEATHER, 4));
        c.ellipse(w - 12, h - 12, 2, 1, WolfPalette.shade(metal, 3));

        doorway(c, w / 2 + 4, wallTop + 3, 9, wallH - 4, regime);

        if (regime) {
            redBand(c, 5, 16, w - 10);
        } else {
            sandbags(c, 6, h - 10, 24);
        }
    }

    // --- Refinery ----------------------------------------------------------------------------

    /** A silo, a processing shed, pipework between them, and a striped unloading bay. */
    private static void refinery(PixelCanvas c, boolean regime, int w, int h) {
        int[] metal = WolfPalette.GUNMETAL;
        int wallH = 12;
        int wallTop = h - wallH - 6;
        int shedX = w / 2 - 4;

        flatRoof(c, shedX, 12, w - shedX - 6, wallTop - 12, regime);
        wallFace(c, shedX, wallTop, w - shedX - 6, wallH, regime);

        // Silo: a banded drum with a hatch that glows with what is inside.
        int siloX = 26;
        int siloY = h / 2 - 2;
        c.ellipse(siloX + 3, siloY + 4, 20, 18, 0x55000000);
        c.ellipse(siloX, siloY, 20, 18, WolfPalette.shade(metal, 4));
        c.ellipse(siloX, siloY, 18, 16, WolfPalette.shade(metal, 3));
        c.ellipse(siloX - 2, siloY - 2, 15, 13, WolfPalette.shade(metal, 2));
        c.ellipse(siloX - 3, siloY - 3, 10, 8, WolfPalette.shade(metal, 1));
        // Banding hoops and vertical seams.
        c.hLine(siloX - 19, siloX + 19, siloY - 9, WolfPalette.shade(metal, 4));
        c.hLine(siloX - 19, siloX + 19, siloY + 9, WolfPalette.shade(metal, 4));
        // Two seams only. Five evenly spaced ones made the drum read as a tyre.
        c.vLine(siloX - 9, siloY - 15, siloY + 15, WolfPalette.shade(metal, 4));
        c.vLine(siloX + 9, siloY - 15, siloY + 15, WolfPalette.shade(metal, 4));
        // Hatch, open, ore glowing out of it.
        c.ellipse(siloX, siloY - 2, 8, 7, WolfPalette.shade(metal, 4));
        c.ellipse(siloX, siloY - 2, 7, 6, WolfPalette.shade(WolfPalette.OCCULT, 3));
        c.ellipse(siloX, siloY - 3, 5, 4, WolfPalette.shade(WolfPalette.OCCULT, 2));
        c.ellipse(siloX, siloY - 3, 3, 2, WolfPalette.shade(WolfPalette.OCCULT, 0));
        c.speckle(siloX - 7, siloY - 8, 14, 12, WolfPalette.shade(WolfPalette.OCCULT, 1), 5, 5);
        // Access ladder up the near side.
        for (int y = siloY + 2; y < siloY + 16; y += 3) {
            c.hLine(siloX - 3, siloX + 3, y, WolfPalette.shade(metal, 1));
        }
        c.vLine(siloX - 3, siloY + 2, siloY + 16, WolfPalette.shade(metal, 2));
        c.vLine(siloX + 3, siloY + 2, siloY + 16, WolfPalette.shade(metal, 2));

        // Pipework from the silo into the shed, with flanges.
        c.panel(siloX + 17, siloY - 5, 16, 7, WolfPalette.BRASS, 2);
        c.rect(siloX + 20, siloY - 6, 3, 9, WolfPalette.shade(WolfPalette.BRASS, 1));
        c.rect(siloX + 27, siloY - 6, 3, 9, WolfPalette.shade(WolfPalette.BRASS, 1));

        roofDetail(c, shedX, 12, w - shedX - 6, wallTop - 12, regime, 31);

        // Roof furniture: a condenser and a vent.
        vent(c, w - 22, 18, 12, 9, regime);
        c.panel(shedX + 6, 20, 10, 8, WolfPalette.GUNMETAL, 3);

        // Unloading bay along the bottom of the shed: hazard stripes and tyre marks.
        c.hazard(shedX, h - 8, w - shedX - 6, 6, WolfPalette.shade(WolfPalette.BRASS, 1),
                WolfPalette.shade(WolfPalette.GUNMETAL, 4));
        c.speckle(shedX, h - 8, w - shedX - 6, 6, WolfPalette.shade(WolfPalette.SMOKE, 4),
                19, 4);

        doorway(c, shedX + 18, wallTop + 3, 12, wallH - 4, regime);

        if (regime) {
            redBand(c, shedX, 12, w - shedX - 6);
        } else {
            sandbags(c, shedX + 4, 6, 30);
        }
    }

    // --- Barracks ----------------------------------------------------------------------------

    /** A hut with a pitched, ribbed roof, a chimney, bunk windows and stacked crates. */
    private static void barracks(PixelCanvas c, boolean regime, int w, int h) {
        int[] roof = roofs(regime);
        int wallH = 12;
        int wallTop = h - wallH - 5;
        int roofTop = 8;
        int ridge = (roofTop + wallTop) / 2;

        // Pitched roof: north slope lit, south slope shaded, with a ridge cap.
        c.rect(4, roofTop, w - 8, ridge - roofTop, WolfPalette.shade(roof, 1));
        c.rect(4, ridge, w - 8, wallTop - ridge, WolfPalette.shade(roof, 3));
        c.hLine(4, w - 5, roofTop, WolfPalette.shade(roof, 0));
        c.hLine(3, w - 4, ridge - 1, WolfPalette.shade(roof, 0));
        c.hLine(3, w - 4, ridge, WolfPalette.shade(roof, 4));
        c.hLine(4, w - 5, wallTop - 1, WolfPalette.shade(roof, 4));

        // Corrugation: one shaded line per sheet, not a light-dark pair. Ribbing both sides
        // of every seam turned the whole roof into a palisade fence.
        for (int x = 7; x < w - 6; x += 5) {
            c.vLine(x, roofTop + 1, ridge - 1, WolfPalette.shade(roof, 2));
            c.vLine(x, ridge + 1, wallTop - 2, WolfPalette.shade(roof, 4));
        }
        // Ridge cap drawn last so the corrugation cannot cut through it.
        c.hLine(3, w - 4, ridge - 1, WolfPalette.shade(roof, 0));
        c.hLine(3, w - 4, ridge, WolfPalette.shade(roof, 4));
        if (!regime) {
            c.panel(w - 26, roofTop + 3, 14, 10, WolfPalette.GUNMETAL, 3);
            c.rivets(w - 25, roofTop + 4, 12, 8, 4, WolfPalette.shade(WolfPalette.GUNMETAL, 1),
                    WolfPalette.shade(WolfPalette.GUNMETAL, 4));
        }

        wallFace(c, 4, wallTop, w - 8, wallH, regime);

        // Chimney with a soot lip, at the north end.
        c.rect(w - 20, 2, 8, 12, WolfPalette.shade(walls(regime), 2));
        c.bevel(w - 20, 2, 8, 12, WolfPalette.shade(walls(regime), 0),
                WolfPalette.shade(walls(regime), 4));
        c.rect(w - 20, 2, 8, 2, WolfPalette.shade(WolfPalette.SMOKE, 2));
        c.px(w - 17, 0, WolfPalette.shade(WolfPalette.SMOKE, 1));

        doorway(c, w / 2 - 6, wallTop + 3, 10, wallH - 4, regime);
        slits(c, 8, wallTop + 4, 2, 9);
        slits(c, w - 22, wallTop + 4, 1, 9);

        // Crates and a barrel by the door.
        c.panel(w - 20, h - 12, 12, 9, WolfPalette.LEATHER, 2);
        c.hLine(w - 19, w - 10, h - 8, WolfPalette.shade(WolfPalette.LEATHER, 4));
        c.vLine(w - 14, h - 11, h - 4, WolfPalette.shade(WolfPalette.LEATHER, 4));
        c.ellipse(w - 26, h - 8, 4, 4, WolfPalette.shade(WolfPalette.GUNMETAL, 2));
        c.ellipse(w - 26, h - 9, 3, 2, WolfPalette.shade(WolfPalette.GUNMETAL, 1));

        if (regime) {
            redBand(c, 4, roofTop, w - 8);
            banner(c, 10, 12, 20);
        } else {
            sandbags(c, 5, h - 10, 26);
            // Washing line strung along the eaves.
            c.hLine(8, w - 24, wallTop - 3, WolfPalette.shade(WolfPalette.BONE, 4));
            c.rect(14, wallTop - 3, 4, 4, WolfPalette.shade(WolfPalette.BONE, 2));
            c.rect(24, wallTop - 3, 3, 5, WolfPalette.shade(WolfPalette.OLIVE, 1));
        }
    }

    // --- War Works ---------------------------------------------------------------------------

    /** A big shed: gantry crane, skylights, a roller shutter and an oil-stained apron. */
    private static void warWorks(PixelCanvas c, boolean regime, int w, int h) {
        int[] roof = roofs(regime);
        int[] metal = WolfPalette.GUNMETAL;
        int wallH = 13;
        int wallTop = h - wallH - 5;

        flatRoof(c, 4, 14, w - 8, wallTop - 14, regime);
        wallFace(c, 4, wallTop, w - 8, wallH, regime);

        // Corrugated roof sheeting.
        for (int x = 7; x < w - 6; x += 4) {
            c.vLine(x, 15, wallTop - 2, WolfPalette.shade(roof, 3));
        }
        // Skylights down the centre line, glazed and grubby.
        for (int x = 14; x < w - 22; x += 20) {
            c.panel(x, 26, 14, 9, WolfPalette.STEEL, 2);
            c.rect(x + 1, 27, 12, 7, WolfPalette.shade(WolfPalette.STEEL, 3));
            c.vLine(x + 7, 27, 33, WolfPalette.shade(metal, 3));
            c.speckle(x + 1, 27, 12, 7, WolfPalette.shade(WolfPalette.SMOKE, 3), x, 5);
        }

        // Gantry crane spanning the roof, with a hoist block hanging from it.
        c.panel(8, 4, w - 16, 6, metal, 2);
        for (int x = 12; x < w - 12; x += 8) {
            c.line(x, 5, x + 4, 9, WolfPalette.shade(metal, 4));
            c.line(x + 4, 5, x, 9, WolfPalette.shade(metal, 4));
        }
        c.panel(w / 2 - 5, 10, 10, 6, WolfPalette.BRASS, 2);
        c.vLine(w / 2, 16, 24, WolfPalette.shade(metal, 3));
        c.rect(w / 2 - 3, 24, 6, 4, WolfPalette.shade(metal, 2));

        // Roller shutter, wide enough to drive a tank through.
        int doorW = w / 3;
        int doorX = w / 2 - doorW / 2;
        c.rect(doorX, wallTop + 1, doorW, wallH - 2, WolfPalette.shade(metal, 3));
        for (int y = wallTop + 2; y < wallTop + wallH - 2; y += 2) {
            c.hLine(doorX + 1, doorX + doorW - 2, y, WolfPalette.shade(metal, 1));
        }
        c.rectOutline(doorX, wallTop + 1, doorW, wallH - 2, WolfPalette.shade(walls(regime), 0));
        c.hLine(doorX, doorX + doorW - 1, wallTop + 1, WolfPalette.shade(metal, 0));

        // Oil-stained apron, and a stack of track links beside the door.
        c.speckle(doorX - 4, h - 6, doorW + 8, 5, WolfPalette.shade(WolfPalette.SMOKE, 4), 11, 2);
        c.panel(8, h - 13, 14, 9, metal, 3);
        for (int i = 0; i < 3; i++) {
            c.hLine(9, 20, h - 12 + i * 3, WolfPalette.shade(metal, 1));
        }

        if (regime) {
            redBand(c, 4, 14, w - 8);
        } else {
            sandbags(c, w - 34, h - 11, 28);
        }
    }

    // --- Flak Turret --------------------------------------------------------------------------

    /** An emplacement: a ring, a gun mount, ammunition boxes and spent cases. */
    private static void flakTurret(PixelCanvas c, boolean regime, int w, int h) {
        int[] metal = WolfPalette.GUNMETAL;
        int cx = w / 2;
        int cy = h / 2 + 1;

        if (regime) {
            // Poured concrete ring with shuttering marks.
            c.ellipse(cx, cy + 2, 15, 13, WolfPalette.shade(WolfPalette.CONCRETE, 4));
            c.ellipse(cx, cy, 15, 13, WolfPalette.shade(WolfPalette.CONCRETE, 2));
            c.ellipse(cx - 1, cy - 1, 13, 11, WolfPalette.shade(WolfPalette.CONCRETE, 1));
            c.ellipse(cx, cy, 10, 9, WolfPalette.shade(WolfPalette.CONCRETE, 3));
            for (int a = 0; a < 8; a++) {
                double t = a * Math.PI / 4;
                c.px((int) (cx + Math.cos(t) * 13), (int) (cy + Math.sin(t) * 11),
                        WolfPalette.shade(WolfPalette.CONCRETE, 4));
            }
        } else {
            // A ring of individually stacked sandbags.
            c.ellipse(cx, cy + 2, 15, 13, 0x44000000);
            for (int a = 0; a < 11; a++) {
                double t = a * Math.PI * 2 / 11;
                int bx = (int) (cx + Math.cos(t) * 12);
                int by = (int) (cy + Math.sin(t) * 10);
                c.ellipse(bx, by, 4, 3, WolfPalette.shade(WolfPalette.BONE, 3));
                c.ellipse(bx, by - 1, 3, 2, WolfPalette.shade(WolfPalette.BONE, 2));
                c.hLine(bx - 3, bx + 3, by + 2, WolfPalette.shade(WolfPalette.BONE, 4));
            }
        }

        // Gun mount: a turntable with a shield ring. The barrel is drawn separately so it can
        // traverse onto whatever the turret is shooting at.
        c.ellipse(cx, cy, 8, 7, WolfPalette.shade(metal, 3));
        c.ellipse(cx - 1, cy - 1, 7, 6, WolfPalette.shade(metal, 2));
        c.ellipse(cx - 1, cy - 1, 4, 3, WolfPalette.shade(metal, 1));
        c.rivets(cx - 6, cy - 5, 12, 10, 5, WolfPalette.shade(metal, 0),
                WolfPalette.shade(metal, 4));

        // Ammunition boxes and spent cases on the parapet.
        c.panel(4, h - 12, 9, 7, WolfPalette.LEATHER, 2);
        c.hLine(5, 11, h - 9, WolfPalette.shade(WolfPalette.LEATHER, 4));
        c.speckle(cx - 6, h - 8, 12, 5, WolfPalette.shade(WolfPalette.BRASS, 1), 29, 6);

        if (regime) {
            redBand(c, cx - 8, 2, 16);
        }
    }

    /**
     * MG Nest: a low sandbagged or concreted position with a slit and a gun poking out of it.
     * Deliberately squat — it should read as the cheap thing next to the flak tower's mass.
     */
    private static void mgNest(PixelCanvas c, boolean regime, int w, int h) {
        int[] metal = WolfPalette.GUNMETAL;
        int cx = w / 2;
        int cy = h / 2 + 2;

        if (regime) {
            // A poured pillbox: hexagonal-ish slab with a firing slit.
            c.ellipse(cx, cy + 2, 13, 10, WolfPalette.shade(WolfPalette.CONCRETE, 4));
            c.ellipse(cx, cy, 13, 10, WolfPalette.shade(WolfPalette.CONCRETE, 2));
            c.ellipse(cx - 1, cy - 1, 11, 8, WolfPalette.shade(WolfPalette.CONCRETE, 1));
            c.rect(cx - 7, cy - 3, 14, 4, 0xFF0E0F0B);
            c.hLine(cx - 7, cx + 6, cy - 4, WolfPalette.shade(WolfPalette.CONCRETE, 0));
        } else {
            // Sandbags heaped into a horseshoe, open at the back.
            c.ellipse(cx, cy + 2, 13, 10, 0x44000000);
            for (int a = 0; a < 8; a++) {
                double t = Math.PI * 0.15 + a * Math.PI * 0.95 / 7;
                int bx = (int) (cx + Math.cos(t) * 11);
                int by = (int) (cy + Math.sin(t) * 8);
                c.ellipse(bx, by, 4, 3, WolfPalette.shade(WolfPalette.BONE, 3));
                c.ellipse(bx, by - 1, 3, 2, WolfPalette.shade(WolfPalette.BONE, 2));
                c.hLine(bx - 3, bx + 3, by + 2, WolfPalette.shade(WolfPalette.BONE, 4));
            }
            c.ellipse(cx, cy, 8, 6, WolfPalette.shade(WolfPalette.DIRT, 3));
        }

        // The gun itself: a machine gun on a low mount, plus an ammunition box.
        c.rect(cx - 2, cy - 2, 12, 3, WolfPalette.shade(metal, 2));
        c.hLine(cx - 2, cx + 9, cy - 2, WolfPalette.shade(metal, 1));
        c.rect(cx + 8, cy - 3, 3, 5, WolfPalette.shade(metal, 3));
        c.ellipse(cx - 3, cy, 4, 3, WolfPalette.shade(metal, 3));
        c.panel(cx - 10, cy + 4, 7, 5, WolfPalette.LEATHER, 2);
        c.speckle(cx, cy + 5, 10, 4, WolfPalette.shade(WolfPalette.BRASS, 1), 17, 4);

        if (regime) {
            redBand(c, cx - 6, 3, 12);
        }
    }

    /**
     * Pak Gun: a long anti-tank barrel behind a shield, in a revetment. All the visual weight
     * is in the gun rather than the position — the opposite of the nest.
     */
    private static void pakGun(PixelCanvas c, boolean regime, int w, int h) {
        int[] metal = WolfPalette.GUNMETAL;
        int cx = w / 2;
        int cy = h / 2 + 3;

        // Revetment: a low earth bank rather than a ring.
        c.ellipse(cx, cy + 3, 14, 7, WolfPalette.shade(WolfPalette.DIRT, 4));
        c.ellipse(cx, cy + 2, 13, 6, WolfPalette.shade(WolfPalette.DIRT, 2));
        c.speckle(cx - 13, cy - 4, 26, 10, WolfPalette.shade(WolfPalette.DIRT, 1), 23, 5);

        // Split trail legs braced into the ground.
        c.line(cx - 2, cy, cx - 11, cy - 6, WolfPalette.shade(metal, 3));
        c.line(cx - 2, cy + 1, cx - 11, cy + 7, WolfPalette.shade(metal, 3));
        c.px(cx - 11, cy - 6, WolfPalette.shade(metal, 1));
        c.px(cx - 11, cy + 7, WolfPalette.shade(metal, 1));

        // Wheels either side of the carriage.
        c.ellipse(cx - 3, cy - 7, 4, 4, WolfPalette.shade(metal, 4));
        c.ellipse(cx - 3, cy + 7, 4, 4, WolfPalette.shade(metal, 4));
        c.ellipse(cx - 3, cy - 7, 2, 2, WolfPalette.shade(metal, 2));
        c.ellipse(cx - 3, cy + 7, 2, 2, WolfPalette.shade(metal, 2));

        // Gun shield: an angled plate with a sight aperture.
        c.panel(cx - 1, cy - 8, 6, 17, regime ? WolfPalette.CONCRETE : WolfPalette.OLIVE, 2);
        c.rivets(cx, cy - 7, 4, 15, 5, WolfPalette.shade(metal, 1),
                WolfPalette.shade(metal, 4));
        c.rect(cx + 1, cy - 2, 3, 3, 0xFF0E0F0B);

        // The barrel: long, thin, with a muzzle brake.
        c.rect(cx + 4, cy - 1, 16, 3, WolfPalette.shade(metal, 2));
        c.hLine(cx + 4, cx + 19, cy - 1, WolfPalette.shade(metal, 1));
        c.hLine(cx + 4, cx + 19, cy + 1, WolfPalette.shade(metal, 4));
        c.rect(cx + 18, cy - 2, 4, 5, WolfPalette.shade(metal, 3));
        c.px(cx + 21, cy, WolfPalette.shade(metal, 0));

        // Shell cases stacked behind the gun.
        c.speckle(cx - 12, cy - 2, 8, 6, WolfPalette.shade(WolfPalette.BRASS, 1), 31, 3);

        if (regime) {
            redBand(c, cx - 5, 2, 10);
        }
    }

    /** The traversing barrel, drawn on top of the emplacement at run time. */
    public static PixelCanvas flakBarrel(int facing) {
        int size = TILE;
        PixelCanvas c = new PixelCanvas(size, size);
        int cx = size / 2;
        int cy = size / 2;
        int[] metal = WolfPalette.GUNMETAL;

        // Twin barrels with a shield behind them.
        c.rect(cx, cy - 4, 15, 2, WolfPalette.shade(metal, 1));
        c.rect(cx, cy - 2, 15, 2, WolfPalette.shade(metal, 3));
        c.rect(cx, cy + 1, 15, 2, WolfPalette.shade(metal, 1));
        c.rect(cx, cy + 3, 15, 2, WolfPalette.shade(metal, 3));
        c.rect(cx + 13, cy - 5, 3, 11, WolfPalette.shade(metal, 2));
        c.panel(cx - 5, cy - 6, 7, 13, metal, 2);
        c.rivets(cx - 4, cy - 5, 5, 11, 4, WolfPalette.shade(metal, 0),
                WolfPalette.shade(metal, 4));
        c.outline(OUTLINE);

        return facing == 0 ? c : c.rotatedSmooth((float) (facing * Math.PI / 4.0), 3);
    }

    /**
     * Battle damage painted over a finished structure: soot and forked cracks at the first
     * tier, holes through to a dark interior with fire and smoke at the second.
     */
    private static void applyDamage(PixelCanvas c, int state, int seed) {
        if (state <= 0) {
            return;
        }
        int w = c.width();
        int h = c.height();

        c.speckle(3, 3, w - 6, h - 6, WolfPalette.shade(WolfPalette.SMOKE, 4), seed * 7 + 3,
                state == 1 ? 18 : 8);

        int cracks = state == 1 ? 4 : 7;
        for (int i = 0; i < cracks; i++) {
            int x = 6 + ((seed * 13 + i * 29) % Math.max(1, w - 12));
            int y = 5 + ((seed * 7 + i * 17) % Math.max(1, h / 2));
            // A crack forks; a single straight line reads as a scratch.
            c.line(x, y, x + 3, y + 7, WolfPalette.shade(WolfPalette.SMOKE, 4));
            c.line(x + 3, y + 7, x - 1 + (i % 4), y + 13, WolfPalette.shade(WolfPalette.SMOKE, 4));
            c.px(x + 3, y + 7, WolfPalette.shade(WolfPalette.SMOKE, 3));
        }

        if (state < 2) {
            return;
        }

        for (int i = 0; i < 4; i++) {
            int x = 9 + ((seed * 23 + i * 37) % Math.max(1, w - 18));
            int y = 9 + ((seed * 11 + i * 19) % Math.max(1, h - 20));
            c.ellipse(x, y, 5, 4, 0xFF0A0A08);
            c.ellipse(x, y + 1, 3, 2, WolfPalette.shade(WolfPalette.FIRE, 3));
            c.ellipse(x, y + 1, 2, 1, WolfPalette.shade(WolfPalette.FIRE, 2));
            // Flame licking up out of the hole, and smoke above it.
            c.px(x - 1, y - 4, WolfPalette.shade(WolfPalette.FIRE, 1));
            c.px(x + 1, y - 5, WolfPalette.shade(WolfPalette.FIRE, 0));
            c.px(x, y - 8, WolfPalette.shade(WolfPalette.SMOKE, 1));
            c.px(x + 2, y - 10, WolfPalette.shade(WolfPalette.SMOKE, 0));
        }
        c.tint(0xFF1C1610, 0.24f);
    }
}
