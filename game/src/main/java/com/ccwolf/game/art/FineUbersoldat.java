package com.ccwolf.game.art;

/**
 * The Ubersoldat on the sixty-four grid.
 *
 * <p>The Regime's showpiece, and after the infantry moved to the fine grid it was the one figure
 * left with quarter-inch pixels — the flagship at the lowest resolution on the field. Same port as
 * the riflemen: the coarse recipe's composition survives at doubled coordinates, and the new
 * pixels are spent on the things the design always wanted and never had room for. A skull-mask
 * with optic slits instead of a face plate with two red rows. Pistons in the waist. A cannon with
 * a bore, a muzzle and a painted band, not a grey stick.
 */
final class FineUbersoldat {

    private FineUbersoldat() {
    }

    private static final int CX = 32;

    /**
     * The Ausmerzer: the Extermination doctrine's machine, drawn as the Ubersoldat's bigger,
     * worse sibling.
     *
     * <p>Same skeleton at the same grid, and every difference is doctrine. Broader across the
     * shoulders, because it is built to fill the trench it steps into. Both arms end in
     * projector nozzles - it carries nothing that works past arm's length. And the optics burn
     * whichever way it faces: an Ubersoldat is a soldier and shows you its back, an Ausmerzer
     * is a furnace and the glow leaks round the housing.
     */
    static PixelCanvas drawAusmerzer(int facing, int frame, int scale, int outline) {
        PixelCanvas c = new PixelCanvas(FineInfantry.SIZE, FineInfantry.SIZE, scale);
        int[] plate = WolfPalette.NIGHT;
        int[] shade = WolfPalette.GUNMETAL;
        float angle = facing * (float) (Math.PI / 4.0);
        float dx = (float) Math.cos(angle);
        float dy = (float) Math.sin(angle);
        float perpX = -dy;
        float perpY = dx;
        int step = frame == 1 ? 2 : 0;

        c.groundShadow(CX, 60, 25, 7);

        leg(c, 20, 34, -step, plate, shade);
        leg(c, 44, 34, step, plate, shade);

        c.rect(22, 30, 21, 8, WolfPalette.shade(shade, 3));
        for (int x = 22; x < 43; x += 4) {
            c.vLine(x, 30, 37, WolfPalette.shade(shade, 2));
            c.px(x, 31, WolfPalette.shade(WolfPalette.STEEL, 2));
        }

        // The torso: wider than the Ubersoldat's and squarer - a boiler, not a chest.
        int[] rowLeft  = {19, 17, 15, 15, 15, 16, 17, 19, 21};
        int[] rowRight = {45, 47, 49, 49, 49, 48, 47, 45, 43};
        for (int i = 0; i < rowLeft.length; i++) {
            int y = 12 + i * 2;
            int fill = i < 3 ? 1 : (i < 6 ? 2 : 3);
            c.rect(rowLeft[i], y, rowRight[i] - rowLeft[i] + 1, 2, WolfPalette.shade(plate, fill));
            c.vLine(rowLeft[i], y, y + 1, WolfPalette.shade(plate, 0));
            c.vLine(rowRight[i], y, y + 1, WolfPalette.shade(plate, 4));
        }
        // Furnace grille where a sternum would be, lit from inside.
        c.rect(29, 18, 7, 8, WolfPalette.shade(shade, 4));
        for (int y = 19; y < 25; y += 2) {
            c.hLine(30, 34, y, WolfPalette.shade(WolfPalette.FIRE, 2));
        }
        c.hLine(31, 33, 21, WolfPalette.shade(WolfPalette.FIRE, 0));
        c.hLine(19, 45, 28, WolfPalette.shade(shade, 2));

        pauldron(c, 10, 22, plate, shade, true);
        pauldron(c, 54, 22, plate, shade, true);

        // Head: lower and wider than the Ubersoldat's, and the optics show at every facing.
        c.rect(25, 14, 15, 5, WolfPalette.shade(shade, 3));
        c.rect(23, 4, 19, 13, WolfPalette.shade(plate, 3));
        c.rect(25, 6, 15, 9, WolfPalette.shade(plate, 2));
        c.hLine(23, 41, 4, WolfPalette.shade(plate, 1));
        c.vLine(23, 4, 16, WolfPalette.shade(plate, 2));
        c.vLine(41, 4, 16, WolfPalette.shade(plate, 4));
        c.hLine(26, 30, 9, WolfPalette.shade(WolfPalette.BLOOD, 1));
        c.hLine(34, 38, 9, WolfPalette.shade(WolfPalette.BLOOD, 1));
        c.px(26, 9, WolfPalette.shade(WolfPalette.BLOOD, 0));
        c.px(34, 9, WolfPalette.shade(WolfPalette.BLOOD, 0));
        c.rect(27, 12, 11, 3, WolfPalette.shade(shade, 4));

        // Both arms end in projectors. The near one aims with the facing; the off one hangs.
        float k = 1f - 0.3f * Math.abs(dy);
        projectorArm(c, CX - perpX * 16f, 24 - perpY * 16f, dx, dy, k, plate, shade, true);
        projectorArm(c, CX + perpX * 16f, 24 + perpY * 16f, dx * 0.3f, dy * 0.3f + 0.6f, 1f,
                plate, shade, false);

        c.outline(outline);
        return c;
    }

    /** An arm that is a fuel line and a nozzle. Lit at the tip when it is the aiming one. */
    private static void projectorArm(PixelCanvas c, float sx, float sy, float dx, float dy,
                                     float k, int[] plate, int[] shade, boolean lit) {
        int shoulderX = Math.round(sx);
        int shoulderY = Math.round(sy);
        int tipX = Math.round(sx + dx * 18f * k);
        int tipY = Math.round(sy + dy * 18f * k);
        c.thickLine(shoulderX, shoulderY, tipX, tipY, 3, WolfPalette.shade(plate, 2));
        c.line(shoulderX, shoulderY, tipX, tipY, WolfPalette.shade(plate, 1));
        c.ellipse(tipX, tipY, 3, 3, WolfPalette.shade(shade, 2));
        c.ellipse(tipX, tipY, 1, 1, WolfPalette.shade(shade, 4));
        if (lit) {
            c.px(tipX + (dx > 0 ? 2 : -2), tipY, WolfPalette.shade(WolfPalette.FIRE, 1));
        }
    }

    static PixelCanvas draw(int facing, int frame, int scale, int outline) {
        PixelCanvas c = new PixelCanvas(FineInfantry.SIZE, FineInfantry.SIZE, scale);
        int[] plate = WolfPalette.STEEL;
        int[] shade = WolfPalette.NIGHT;
        float angle = facing * (float) (Math.PI / 4.0);
        float dx = (float) Math.cos(angle);
        float dy = (float) Math.sin(angle);
        float perpX = -dy;
        float perpY = dx;
        boolean toViewer = dy > 0.3f;
        int step = frame == 1 ? 2 : 0;

        c.groundShadow(CX, 60, 22, 6);

        leg(c, 22, 34, -step, plate, shade);
        leg(c, 42, 34, step, plate, shade);

        // Waist: a band of exposed machinery, piston rods catching what light there is.
        c.rect(24, 30, 17, 8, WolfPalette.shade(shade, 2));
        for (int x = 24; x < 41; x += 4) {
            c.vLine(x, 30, 37, WolfPalette.shade(shade, 1));
            c.px(x, 31, WolfPalette.shade(WolfPalette.STEEL, 1));
        }

        // Torso: a barrel with a chest and a waist, row pairs so the taper stays smooth.
        int[] rowLeft  = {22, 20, 18, 18, 18, 20, 20, 22, 24};
        int[] rowRight = {42, 44, 46, 46, 46, 44, 44, 42, 40};
        for (int i = 0; i < rowLeft.length; i++) {
            int y = 12 + i * 2;
            int fill = i < 3 ? 1 : (i < 6 ? 2 : 3);
            c.rect(rowLeft[i], y, rowRight[i] - rowLeft[i] + 1, 2, WolfPalette.shade(plate, fill));
            c.vLine(rowLeft[i], y, y + 1, WolfPalette.shade(plate, 0));
            c.vLine(rowRight[i], y, y + 1, WolfPalette.shade(plate, 4));
        }
        // Sternum ridge.
        c.vLine(32, 14, 27, WolfPalette.shade(plate, 0));
        c.vLine(33, 14, 27, WolfPalette.shade(plate, 3));
        // Plate seams and bolts at the breastplate corners.
        c.hLine(22, 42, 22, WolfPalette.shade(shade, 2));
        c.hLine(20, 44, 28, WolfPalette.shade(shade, 2));
        for (int[] bolt : new int[][] {{24, 16}, {40, 16}, {24, 26}, {40, 26}}) {
            c.px(bolt[0], bolt[1], WolfPalette.shade(plate, 0));
            c.px(bolt[0] + 1, bolt[1] + 1, WolfPalette.shade(plate, 4));
        }
        c.hLine(20, 44, 30, WolfPalette.shade(shade, 1));

        pauldron(c, 12, 22, plate, shade, false);
        pauldron(c, 52, 22, plate, shade, true);

        // Head: a small armoured skull sunk between the pauldrons.
        c.rect(26, 14, 13, 5, WolfPalette.shade(shade, 3));
        c.rect(24, 2, 17, 15, WolfPalette.shade(plate, 3));
        c.rect(26, 4, 13, 11, WolfPalette.shade(plate, 2));
        c.hLine(24, 40, 2, WolfPalette.shade(plate, 1));
        c.hLine(25, 39, 3, WolfPalette.shade(plate, 1));
        c.vLine(24, 2, 16, WolfPalette.shade(plate, 2));
        c.vLine(40, 2, 16, WolfPalette.shade(plate, 4));
        for (int[] bolt : new int[][] {{26, 4}, {38, 4}, {26, 14}, {38, 14}}) {
            c.px(bolt[0], bolt[1], WolfPalette.shade(plate, 0));
        }
        c.vLine(32, 6, 14, WolfPalette.shade(shade, 2));

        if (toViewer) {
            // Optic slits, a brow shadow over them, and a jaw grille: a skull, not a face.
            c.hLine(26, 38, 6, WolfPalette.shade(shade, 3));
            c.hLine(27, 30, 8, WolfPalette.shade(WolfPalette.BLOOD, 1));
            c.hLine(34, 37, 8, WolfPalette.shade(WolfPalette.BLOOD, 1));
            c.px(27, 8, WolfPalette.shade(WolfPalette.BLOOD, 0));
            c.px(34, 8, WolfPalette.shade(WolfPalette.BLOOD, 0));
            c.rect(28, 11, 9, 4, WolfPalette.shade(shade, 4));
            for (int x = 29; x <= 35; x += 2) {
                c.vLine(x, 11, 14, WolfPalette.shade(shade, 1));
            }
        } else {
            // The back of the skull cap and its cable loom running down into the collar.
            c.hLine(28, 36, 10, WolfPalette.shade(plate, 3));
            c.vLine(30, 12, 16, WolfPalette.shade(shade, 1));
            c.vLine(34, 12, 16, WolfPalette.shade(shade, 1));
        }

        // Arms: a fist on one side, the cannon on the other, both aimed by the facing. The
        // cannon foreshortens toward the camera the same way a rifleman's weapon does.
        float k = 1f - 0.3f * Math.abs(dy);
        int shoulderX = Math.round(CX + perpX * 14f);
        int shoulderY = Math.round(24 + perpY * 14f);
        int fistX = Math.round(shoulderX + dx * 10f * k);
        int fistY = Math.round(shoulderY + dy * 10f * k);
        c.thickLine(shoulderX, shoulderY, fistX, fistY, 4, WolfPalette.shade(plate, 2));
        c.thickLine(shoulderX, shoulderY, fistX, fistY, 2, WolfPalette.shade(plate, 1));
        c.ellipse(fistX, fistY, 5, 5, WolfPalette.shade(plate, 2));
        c.ellipse(fistX, fistY, 3, 3, WolfPalette.shade(shade, 1));
        c.px(fistX - 1, fistY - 1, WolfPalette.shade(plate, 0));

        cannon(c, dx, dy, perpX, perpY, k, plate, shade);

        c.outline(outline);
        return c;
    }

    /** Dark hip joint, thick thigh, armoured shin, splayed foot — at twice the resolution. */
    private static void leg(PixelCanvas c, int x, int y, int step, int[] plate, int[] shade) {
        c.rect(x - 3, y + step, 6, 5, WolfPalette.shade(shade, 2));
        c.rect(x - 4, y + 4 + step, 8, 9, WolfPalette.shade(plate, 2));
        c.vLine(x - 4, y + 4 + step, y + 12 + step, WolfPalette.shade(plate, 1));
        c.vLine(x + 3, y + 4 + step, y + 12 + step, WolfPalette.shade(plate, 3));
        c.rect(x - 4, y + 13 + step, 8, 8, WolfPalette.shade(plate, 3));
        c.vLine(x - 4, y + 13 + step, y + 20 + step, WolfPalette.shade(plate, 2));
        // Shin plate ridge.
        c.vLine(x, y + 13 + step, y + 20 + step, WolfPalette.shade(plate, 1));
        // Splayed foot, wider than the leg, with a lit toe.
        c.rect(x - 6, y + 21 + step, 12, 5, WolfPalette.shade(plate, 3));
        c.hLine(x - 6, x + 5, y + 21 + step, WolfPalette.shade(plate, 1));
        c.hLine(x - 6, x + 5, y + 25 + step, WolfPalette.shade(WolfPalette.NIGHT, 4));
    }

    /** A pauldron: the widest, tallest thing on the machine, with a recess beneath it. */
    private static void pauldron(PixelCanvas c, int cx, int cy, int[] plate, int[] shade,
                                 boolean red) {
        c.ellipse(cx, cy + 6, 11, 7, WolfPalette.shade(shade, 2));
        c.ellipse(cx, cy, 11, 9, WolfPalette.shade(plate, 3));
        c.ellipse(cx, cy - 2, 11, 7, WolfPalette.shade(plate, 2));
        c.ellipse(cx - 2, cy - 4, 7, 4, WolfPalette.shade(plate, 1));
        c.hLine(cx - 8, cx + 6, cy - 9, WolfPalette.shade(plate, 0));
        // Ribs across the plate.
        c.line(cx - 10, cy, cx + 10, cy, WolfPalette.shade(shade, 1));
        c.line(cx - 8, cy + 5, cx + 8, cy + 5, WolfPalette.shade(shade, 1));
        c.px(cx - 6, cy - 4, WolfPalette.shade(plate, 0));
        c.px(cx + 6, cy - 4, WolfPalette.shade(plate, 0));
        if (red) {
            // The painted band: the only colour on the machine.
            c.hLine(cx - 8, cx + 4, cy - 7, WolfPalette.shade(WolfPalette.BLOOD, 1));
            c.hLine(cx - 7, cx + 3, cy - 6, WolfPalette.shade(WolfPalette.BLOOD, 2));
            c.px(cx - 8, cy - 7, WolfPalette.shade(WolfPalette.BLOOD, 0));
        }
    }

    /** The arm cannon: a bored barrel with a painted band, not a grey stick. */
    private static void cannon(PixelCanvas c, float dx, float dy, float perpX, float perpY,
                               float k, int[] plate, int[] shade) {
        int shoulderX = Math.round(CX - perpX * 14f);
        int shoulderY = Math.round(24 - perpY * 14f);
        int tipX = Math.round(shoulderX + dx * 22f * k);
        int tipY = Math.round(shoulderY + dy * 22f * k);

        // Housing at the shoulder.
        c.thickLine(shoulderX, shoulderY, Math.round(shoulderX + dx * 6f),
                Math.round(shoulderY + dy * 6f), 4, WolfPalette.shade(plate, 2));
        // Barrel, two-tone along its length.
        c.thickLine(shoulderX, shoulderY, tipX, tipY, 2, WolfPalette.shade(shade, 2));
        c.line(shoulderX, shoulderY, tipX, tipY, WolfPalette.shade(plate, 1));
        c.line(Math.round(shoulderX + perpX), Math.round(shoulderY + perpY),
                Math.round(tipX + perpX), Math.round(tipY + perpY), WolfPalette.shade(shade, 3));
        // Painted band mid-barrel.
        int bandX = Math.round(shoulderX + dx * 14f * k);
        int bandY = Math.round(shoulderY + dy * 14f * k);
        c.thickLine(bandX, bandY, Math.round(bandX + dx), Math.round(bandY + dy), 2,
                WolfPalette.shade(WolfPalette.BLOOD, 2));
        // Muzzle: a dark bore in a lit ring.
        c.ellipse(tipX, tipY, 2, 2, WolfPalette.shade(plate, 1));
        c.px(tipX, tipY, WolfPalette.shade(WolfPalette.NIGHT, 4));
    }
}
