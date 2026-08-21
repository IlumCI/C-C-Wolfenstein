package com.ccwolf.android.art;

import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;

/**
 * Pixel-art recipes for structures.
 *
 * <p>Everything is drawn as a roof seen from above with the south wall face showing beneath
 * it, plus a sliver of the east face. That one convention is what makes a structure read as a
 * building with height rather than a coloured rectangle lying on the grass — the first pass
 * here drew flat roof slabs and they looked like lawns.
 *
 * <p>Both sides build the same things out of what they have: the Regime pours concrete and
 * hangs banners, the Resistance nails together timber, corrugated iron and sandbags.
 */
public final class BuildingSprites {

    public static final int TILE = UnitSprites.TILE;

    /** 0 intact, 1 scarred, 2 burning. */
    public static final int DAMAGE_STATES = 3;

    private static final int SHADOW = 0x55000000;
    private static final int OUTLINE = 0xFF101109;

    /** How much of the sprite's height is given over to the south wall face. */
    private static final int WALL_FACE = 7;

    private BuildingSprites() {
    }

    public static PixelCanvas render(BuildingType type, Faction faction, int damageState) {
        int w = type.tilesWide() * TILE;
        int h = type.tilesHigh() * TILE;
        PixelCanvas c = new PixelCanvas(w, h);
        boolean regime = faction == Faction.REGIME;

        // Ground shadow, thrown south-east, before anything is built on top of it.
        c.ellipse(w / 2 + 2, h - 4, w / 2 - 2, 5, SHADOW);

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
     * Regime construction is poured concrete under black roofing — brutalist mass with one
     * red accent. Resistance construction is timber and scavenged iron.
     */
    private static int[] walls(boolean regime) {
        return regime ? WolfPalette.CONCRETE : WolfPalette.LEATHER;
    }

    private static int[] roofs(boolean regime) {
        return regime ? WolfPalette.NIGHT : WolfPalette.OLIVE;
    }

    /**
     * A red band along a roof edge. Every Regime structure gets one: at a glance across the
     * valley, red-on-black is the enemy and olive-on-brown is you.
     */
    private static void redStripe(PixelCanvas c, int x, int y, int w) {
        c.hLine(x, x + w - 1, y, WolfPalette.shade(WolfPalette.BLOOD, 1));
        c.hLine(x, x + w - 1, y + 1, WolfPalette.shade(WolfPalette.BLOOD, 2));
    }

    /**
     * The core shape: a roof with a wall face under it.
     *
     * @param x left edge
     * @param y top edge
     * @param w width
     * @param h total height including the wall face
     * @return the y coordinate where the wall face starts, for placing doors and windows
     */
    private static int block(PixelCanvas c, int x, int y, int w, int h, boolean regime,
                             boolean pitched) {
        int[] wall = walls(regime);
        int[] roof = roofs(regime);
        int roofH = h - WALL_FACE;
        int wallTop = y + roofH;

        // Roof.
        if (pitched) {
            // Two planes meeting at a ridge, the north one catching the light.
            int half = roofH / 2;
            c.rect(x, y, w, half, WolfPalette.shade(roof, 1));
            c.rect(x, y + half, w, roofH - half, WolfPalette.shade(roof, 3));
            c.hLine(x, x + w - 1, y + half, WolfPalette.shade(roof, 0));
            c.hLine(x, x + w - 1, y + half - 1, WolfPalette.shade(roof, 0));
        } else {
            c.rampVertical(x, y, w, roofH, roof, 1, 3);
        }
        // Roof edging: light along the north and west, dark where it meets the wall.
        c.hLine(x, x + w - 1, y, WolfPalette.shade(roof, 0));
        c.vLine(x, y, wallTop - 1, WolfPalette.shade(roof, 0));
        c.vLine(x + w - 1, y, wallTop - 1, WolfPalette.shade(roof, 4));
        c.hLine(x, x + w - 1, wallTop - 1, WolfPalette.shade(roof, 4));

        // South wall face, in the building material.
        c.rampVertical(x, wallTop, w, WALL_FACE, wall, 1, 2);
        c.hLine(x, x + w - 1, wallTop, WolfPalette.shade(wall, 0));
        c.hLine(x, x + w - 1, y + h - 1, WolfPalette.shade(wall, 4));

        if (regime) {
            // Block courses across the wall face.
            for (int bx = x + 1; bx < x + w - 1; bx += 7) {
                c.vLine(bx, wallTop + 1, y + h - 2, WolfPalette.shade(wall, 3));
            }
            c.hLine(x, x + w - 1, wallTop + 3, WolfPalette.shade(wall, 3));
        } else {
            // Plank ends and a nailed batten.
            for (int bx = x + 2; bx < x + w - 1; bx += 3) {
                c.vLine(bx, wallTop + 1, y + h - 2, WolfPalette.shade(wall, 3));
            }
            c.hLine(x + 1, x + w - 2, wallTop + 2, WolfPalette.shade(wall, 1));
        }

        // East face sliver, so the building has a second visible side.
        c.rect(x + w - 3, y + 2, 3, roofH - 2, WolfPalette.shade(wall, 3));
        c.vLine(x + w - 3, y + 2, wallTop - 1, WolfPalette.shade(wall, 2));

        return wallTop;
    }

    /** A door punched into a wall face, with a step and a lintel. */
    private static void door(PixelCanvas c, int cx, int wallTop, int height, boolean regime) {
        int w = 7;
        int x = cx - w / 2;
        c.rect(x, wallTop + 1, w, height, 0xFF14140F);
        c.hLine(x - 1, x + w, wallTop, WolfPalette.shade(walls(regime), 0));
        c.vLine(x - 1, wallTop + 1, wallTop + height, WolfPalette.shade(walls(regime), 4));
        c.vLine(x + w, wallTop + 1, wallTop + height, WolfPalette.shade(walls(regime), 4));
        c.hLine(x - 1, x + w, wallTop + height + 1, WolfPalette.shade(WolfPalette.DIRT, 2));
    }

    /** Lit window slits along a wall face. */
    private static void windows(PixelCanvas c, int x, int wallTop, int count, int spacing) {
        for (int i = 0; i < count; i++) {
            int wx = x + i * spacing;
            c.rect(wx, wallTop + 2, 3, 2, 0xFF14140F);
            c.px(wx + 1, wallTop + 2, WolfPalette.shade(WolfPalette.FIRE, 2));
        }
    }

    private static void sandbags(PixelCanvas c, int x, int y, int w) {
        int[] bag = WolfPalette.BONE;
        for (int bx = x; bx < x + w - 3; bx += 5) {
            c.ellipse(bx + 2, y + 2, 3, 2, WolfPalette.shade(bag, 3));
            c.hLine(bx, bx + 4, y, WolfPalette.shade(bag, 2));
            c.hLine(bx, bx + 4, y + 3, WolfPalette.shade(bag, 4));
        }
    }

    /** Regime banner: blood red, brass-topped pole, original skull mark. No Nazi insignia. */
    private static void banner(PixelCanvas c, int x, int y, int height) {
        c.vLine(x, y - 3, y + height, WolfPalette.shade(WolfPalette.BRASS, 2));
        c.px(x, y - 4, WolfPalette.shade(WolfPalette.BRASS, 0));
        c.rect(x + 1, y, 7, height, WolfPalette.shade(WolfPalette.BLOOD, 1));
        c.vLine(x + 1, y, y + height, WolfPalette.shade(WolfPalette.BLOOD, 0));
        c.vLine(x + 7, y, y + height, WolfPalette.shade(WolfPalette.BLOOD, 3));
        c.hLine(x + 1, x + 7, y + height, WolfPalette.shade(WolfPalette.BLOOD, 3));

        int cx = x + 4;
        int cy = y + height / 2;
        c.ellipse(cx, cy - 1, 2, 2, WolfPalette.shade(WolfPalette.BONE, 0));
        c.px(cx - 1, cy - 1, WolfPalette.shade(WolfPalette.BLOOD, 4));
        c.px(cx + 1, cy - 1, WolfPalette.shade(WolfPalette.BLOOD, 4));
        c.hLine(cx - 1, cx + 1, cy + 2, WolfPalette.shade(WolfPalette.BONE, 1));
    }

    // --- the six structures ---------------------------------------------------------------

    private static void commandPost(PixelCanvas c, boolean regime, int w, int h) {
        int[] roof = roofs(regime);

        // Main hall across the full footprint, with a squat watch tower standing on the roof
        // at the north-west corner rather than cutting into the roofline.
        int wallTop = block(c, 3, 12, w - 6, h - 15, regime, false);

        int towerX = 8;
        int towerY = 3;
        int towerW = 20;
        int towerH = 15;
        c.rect(towerX + 2, towerY + 3, towerW, towerH, 0x44000000);
        c.rampVertical(towerX, towerY, towerW, towerH - 5, roof, 1, 2);
        c.rampVertical(towerX, towerY + towerH - 5, towerW, 5, walls(regime), 1, 2);
        c.bevel(towerX, towerY, towerW, towerH, WolfPalette.shade(roof, 0),
                WolfPalette.shade(walls(regime), 4));
        // Observation slits around the tower.
        for (int i = 0; i < 3; i++) {
            c.rect(towerX + 3 + i * 6, towerY + towerH - 4, 3, 2, 0xFF14140F);
        }

        // Rooftop hatch and a vent block on the main roof.
        c.rect(w - 20, 18, 8, 6, WolfPalette.shade(roof, 3));
        c.hLine(w - 20, w - 13, 18, WolfPalette.shade(roof, 0));
        c.rect(w - 34, 20, 5, 4, WolfPalette.shade(WolfPalette.GUNMETAL, 2));

        door(c, w / 2, wallTop, 5, regime);
        windows(c, 8, wallTop, 2, 9);
        windows(c, w - 20, wallTop, 2, 9);

        if (regime) {
            redStripe(c, 3, 12, w - 6);
            banner(c, 33, 6, 15);
            banner(c, w - 13, 6, 15);
        } else {
            sandbags(c, 4, h - 5, w - 8);
            // Radio mast lashed to the tower.
            c.vLine(w - 26, 6, 20, WolfPalette.shade(WolfPalette.GUNMETAL, 1));
            c.hLine(w - 29, w - 23, 7, WolfPalette.shade(WolfPalette.GUNMETAL, 2));
            c.hLine(w - 28, w - 24, 10, WolfPalette.shade(WolfPalette.GUNMETAL, 3));
        }
    }

    private static void generator(PixelCanvas c, boolean regime, int w, int h) {
        int wallTop = block(c, 4, 12, w - 8, h - 15, regime, false);

        // Two stacks rising above the roofline, sooty at the lips.
        for (int i = 0; i < 2; i++) {
            int sx = 8 + i * 14;
            c.rect(sx, 2, 7, 14, WolfPalette.shade(WolfPalette.GUNMETAL, 2));
            c.bevel(sx, 2, 7, 14, WolfPalette.shade(WolfPalette.GUNMETAL, 1),
                    WolfPalette.shade(WolfPalette.GUNMETAL, 4));
            c.rect(sx, 2, 7, 2, WolfPalette.shade(WolfPalette.SMOKE, 3));
            c.px(sx + 3, 1, WolfPalette.shade(WolfPalette.SMOKE, 1));
        }

        // The coil on the roof: where the engineering gives way to the occult.
        int cx = w / 2 + 4;
        int cy = 22;
        c.ellipse(cx, cy, 7, 6, WolfPalette.shade(WolfPalette.GUNMETAL, 3));
        c.ellipse(cx, cy, 5, 4, WolfPalette.shade(WolfPalette.OCCULT, 3));
        c.ellipse(cx, cy - 1, 3, 2, WolfPalette.shade(WolfPalette.OCCULT, 1));
        c.px(cx, cy - 1, WolfPalette.shade(WolfPalette.OCCULT, 0));
        c.line(cx - 9, cy + 5, cx - 5, cy + 2, WolfPalette.shade(WolfPalette.BRASS, 2));
        c.line(cx + 9, cy + 5, cx + 5, cy + 2, WolfPalette.shade(WolfPalette.BRASS, 2));

        door(c, w / 2, wallTop, 4, regime);
        windows(c, 6, wallTop, 1, 6);
        if (regime) {
            redStripe(c, 4, 12, w - 8);
        }
    }

    private static void refinery(PixelCanvas c, boolean regime, int w, int h) {
        // Shed on the right half.
        int shedX = w / 2 - 6;
        int wallTop = block(c, shedX, 8, w - shedX - 3, h - 11, regime, false);

        // Silo on the left: a banded drum with the load glowing out of the top hatch.
        int siloX = 17;
        int siloY = h / 2 - 1;
        c.ellipse(siloX, siloY + 2, 14, 13, WolfPalette.shade(WolfPalette.GUNMETAL, 4));
        c.ellipse(siloX, siloY, 14, 13, WolfPalette.shade(WolfPalette.GUNMETAL, 3));
        c.ellipse(siloX - 1, siloY - 1, 12, 11, WolfPalette.shade(WolfPalette.GUNMETAL, 2));
        c.ellipse(siloX - 2, siloY - 2, 8, 7, WolfPalette.shade(WolfPalette.GUNMETAL, 1));
        c.ellipse(siloX, siloY - 1, 6, 5, WolfPalette.shade(WolfPalette.OCCULT, 3));
        c.ellipse(siloX, siloY - 2, 4, 3, WolfPalette.shade(WolfPalette.OCCULT, 1));
        c.ellipse(siloX, siloY - 2, 2, 1, WolfPalette.shade(WolfPalette.OCCULT, 0));
        c.speckle(siloX - 7, siloY - 7, 14, 12, WolfPalette.shade(WolfPalette.OCCULT, 2), 5, 6);
        // Hoops.
        c.hLine(siloX - 13, siloX + 13, siloY - 8, WolfPalette.shade(WolfPalette.GUNMETAL, 4));
        c.hLine(siloX - 13, siloX + 13, siloY + 8, WolfPalette.shade(WolfPalette.GUNMETAL, 4));

        // Pipework running from the silo into the shed.
        c.rect(siloX + 12, siloY - 3, 12, 5, WolfPalette.shade(WolfPalette.BRASS, 2));
        c.hLine(siloX + 12, siloX + 23, siloY - 3, WolfPalette.shade(WolfPalette.BRASS, 0));
        c.hLine(siloX + 12, siloX + 23, siloY + 1, WolfPalette.shade(WolfPalette.BRASS, 3));

        // Hazard-striped unloading apron along the bottom of the shed.
        for (int x = shedX + 2; x < w - 5; x += 8) {
            c.rect(x, h - 4, 4, 3, WolfPalette.shade(WolfPalette.BRASS, 1));
            c.rect(x + 4, h - 4, 4, 3, WolfPalette.shade(WolfPalette.GUNMETAL, 3));
        }

        door(c, shedX + 12, wallTop, 4, regime);
        if (regime) {
            redStripe(c, shedX, 8, w - shedX - 3);
        }
        if (!regime) {
            sandbags(c, shedX + 20, 4, w - shedX - 24);
        }
    }

    private static void barracks(PixelCanvas c, boolean regime, int w, int h) {
        int wallTop = block(c, 3, 6, w - 6, h - 9, regime, true);
        int[] roof = roofs(regime);

        // Roof ribs, stopping short of the ridge so the ridge still reads.
        for (int x = 6; x < w - 5; x += 5) {
            c.vLine(x, 7, wallTop - 6, WolfPalette.shade(roof, 4));
            c.vLine(x + 1, 7, wallTop - 6, WolfPalette.shade(roof, 2));
        }

        // Chimney at the north end.
        c.rect(w - 14, 2, 6, 8, WolfPalette.shade(walls(regime), 2));
        c.rect(w - 14, 2, 6, 2, WolfPalette.shade(WolfPalette.SMOKE, 3));

        door(c, w / 2 - 6, wallTop, 5, regime);
        windows(c, 6, wallTop, 2, 8);
        windows(c, w - 16, wallTop, 1, 8);

        // Ammunition crates stacked against the wall.
        c.rect(w - 13, h - 7, 9, 6, WolfPalette.shade(WolfPalette.LEATHER, 2));
        c.bevel(w - 13, h - 7, 9, 6, WolfPalette.shade(WolfPalette.LEATHER, 1),
                WolfPalette.shade(WolfPalette.LEATHER, 4));
        c.hLine(w - 12, w - 6, h - 4, WolfPalette.shade(WolfPalette.LEATHER, 3));

        if (regime) {
            redStripe(c, 3, 6, w - 6);
            banner(c, 6, 8, 14);
        } else {
            sandbags(c, 4, h - 5, 16);
        }
    }

    private static void warWorks(PixelCanvas c, boolean regime, int w, int h) {
        int wallTop = block(c, 3, 8, w - 6, h - 11, regime, false);
        int[] roof = roofs(regime);

        // Corrugated roof and a row of skylights down the middle.
        for (int x = 5; x < w - 4; x += 3) {
            c.vLine(x, 9, wallTop - 2, WolfPalette.shade(roof, 3));
        }
        for (int x = 10; x < w - 12; x += 12) {
            c.rect(x, 16, 8, 5, WolfPalette.shade(WolfPalette.STEEL, 1));
            c.rectOutline(x, 16, 8, 5, WolfPalette.shade(roof, 4));
        }

        // Roller shutter, wide enough to drive a tank through.
        int doorW = w / 3;
        int doorX = w / 2 - doorW / 2;
        c.rect(doorX, wallTop, doorW, WALL_FACE - 1, WolfPalette.shade(WolfPalette.GUNMETAL, 3));
        for (int y = wallTop + 1; y < wallTop + WALL_FACE - 1; y += 2) {
            c.hLine(doorX + 1, doorX + doorW - 2, y, WolfPalette.shade(WolfPalette.GUNMETAL, 1));
        }
        c.rectOutline(doorX, wallTop, doorW, WALL_FACE - 1, WolfPalette.shade(walls(regime), 0));

        // Oil-stained apron in front of the doors.
        c.speckle(doorX - 3, h - 3, doorW + 6, 3, WolfPalette.shade(WolfPalette.SMOKE, 4), 11, 2);

        if (regime) {
            redStripe(c, 3, 8, w - 6);
        }

        // Gantry crane along the roofline.
        c.rect(6, 3, w - 12, 4, WolfPalette.shade(WolfPalette.GUNMETAL, 2));
        c.hLine(6, w - 7, 3, WolfPalette.shade(WolfPalette.GUNMETAL, 0));
        c.hLine(6, w - 7, 6, WolfPalette.shade(WolfPalette.GUNMETAL, 4));
        c.rect(w / 2 - 3, 7, 6, 4, WolfPalette.shade(WolfPalette.BRASS, 2));
        c.vLine(w / 2, 11, 15, WolfPalette.shade(WolfPalette.GUNMETAL, 3));
    }

    private static void flakTurret(PixelCanvas c, boolean regime, int w, int h) {
        int cx = w / 2;
        int cy = h / 2;

        if (regime) {
            // Poured concrete emplacement.
            c.ellipse(cx, cy + 2, 11, 10, WolfPalette.shade(WolfPalette.STONE, 4));
            c.ellipse(cx, cy, 11, 10, WolfPalette.shade(WolfPalette.STONE, 2));
            c.ellipse(cx - 1, cy - 1, 9, 8, WolfPalette.shade(WolfPalette.STONE, 1));
            c.ellipse(cx, cy, 7, 6, WolfPalette.shade(WolfPalette.STONE, 3));
        } else {
            // A ring of sandbags, each one drawn.
            c.ellipse(cx, cy + 2, 11, 10, 0x44000000);
            for (int a = 0; a < 9; a++) {
                double t = a * Math.PI * 2 / 9;
                int bx = (int) (cx + Math.cos(t) * 9);
                int by = (int) (cy + Math.sin(t) * 8);
                c.ellipse(bx, by, 3, 3, WolfPalette.shade(WolfPalette.BONE, 3));
                c.ellipse(bx, by - 1, 2, 1, WolfPalette.shade(WolfPalette.BONE, 2));
                c.hLine(bx - 2, bx + 2, by + 2, WolfPalette.shade(WolfPalette.BONE, 4));
            }
        }

        // Gun mount: the barrel itself is drawn separately so it can traverse.
        c.ellipse(cx, cy, 6, 5, WolfPalette.shade(WolfPalette.GUNMETAL, 3));
        c.ellipse(cx - 1, cy - 1, 5, 4, WolfPalette.shade(WolfPalette.GUNMETAL, 2));
        c.ellipse(cx - 1, cy - 1, 3, 2, WolfPalette.shade(WolfPalette.GUNMETAL, 1));
    }

    /** The traversing barrel, drawn on top of the emplacement at run time. */
    public static PixelCanvas flakBarrel(int facing) {
        int size = TILE;
        PixelCanvas c = new PixelCanvas(size, size);
        int cx = size / 2;
        int cy = size / 2;
        int[] metal = WolfPalette.GUNMETAL;

        c.rect(cx, cy - 2, 12, 2, WolfPalette.shade(metal, 1));
        c.rect(cx, cy, 12, 2, WolfPalette.shade(metal, 3));
        c.rect(cx + 9, cy - 3, 3, 5, WolfPalette.shade(metal, 2));
        c.ellipse(cx - 1, cy, 5, 4, WolfPalette.shade(metal, 2));
        c.ellipse(cx - 2, cy - 1, 3, 3, WolfPalette.shade(metal, 1));
        c.outline(OUTLINE);

        return facing == 0 ? c : c.rotatedSmooth((float) (facing * Math.PI / 4.0), 3);
    }

    /**
     * Battle damage, painted over a finished structure: soot and cracks at the first tier,
     * holes through to a dark interior and fire at the second.
     */
    private static void applyDamage(PixelCanvas c, int state, int seed) {
        if (state <= 0) {
            return;
        }
        int w = c.width();
        int h = c.height();

        c.speckle(2, 2, w - 4, h - 4, WolfPalette.shade(WolfPalette.SMOKE, 4), seed * 7 + 3,
                state == 1 ? 16 : 7);

        int cracks = state == 1 ? 3 : 5;
        for (int i = 0; i < cracks; i++) {
            int x = 5 + ((seed * 13 + i * 29) % Math.max(1, w - 10));
            int y = 4 + ((seed * 7 + i * 17) % Math.max(1, h / 2));
            // A crack forks: two short runs from the same origin reads as broken masonry,
            // one straight line reads as a scratch.
            c.line(x, y, x + 2, y + 5, WolfPalette.shade(WolfPalette.SMOKE, 4));
            c.line(x + 2, y + 5, x - 1 + (i % 3), y + 9, WolfPalette.shade(WolfPalette.SMOKE, 4));
        }

        if (state < 2) {
            return;
        }

        for (int i = 0; i < 3; i++) {
            int x = 7 + ((seed * 23 + i * 37) % Math.max(1, w - 14));
            int y = 7 + ((seed * 11 + i * 19) % Math.max(1, h - 16));
            c.ellipse(x, y, 4, 3, 0xFF0C0C0A);
            c.ellipse(x, y + 1, 2, 1, WolfPalette.shade(WolfPalette.FIRE, 3));
            c.px(x - 1, y - 3, WolfPalette.shade(WolfPalette.FIRE, 2));
            c.px(x + 1, y - 4, WolfPalette.shade(WolfPalette.FIRE, 1));
            c.px(x, y - 6, WolfPalette.shade(WolfPalette.SMOKE, 1));
        }
        c.tint(0xFF1E1810, 0.22f);
    }
}
