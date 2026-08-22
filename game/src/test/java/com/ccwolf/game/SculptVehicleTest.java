package com.ccwolf.game;

import com.ccwolf.game.art.Form;
import com.ccwolf.game.art.Light;
import com.ccwolf.game.art.Machine;
import com.ccwolf.game.art.PixelCanvas;
import com.ccwolf.game.art.Sculptor;
import com.ccwolf.game.art.WolfPalette;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * One vehicle, drawn to the standard the rest of the roster has to reach.
 *
 * <p>The test card proved the lighting model on bare shapes. This proves the thing that actually
 * matters: whether part count pays. The old recipes topped out around fifty primitive calls,
 * because past that point more parts made a sprite muddier rather than richer — highlights placed
 * by different lines of code fight each other, and the fiftieth hand-placed shadow is arguing
 * with the tenth. With the light computed, the opposite holds, and this is the demonstration:
 * several hundred strokes, every one of them lit by the same sun, and the sprite gets clearer the
 * more of them there are.
 *
 * <p>Drawn once, then looked at large. A hull that only works at forty pixels across is a hull
 * that is hiding something.
 */
public class SculptVehicleTest {

    static {
        Frame.useAwtBackend();
    }

    /** Big enough that panel lines, bolts and track links have somewhere to live. */
    private static final int SIZE = 256;

    @Test
    public void aHullWithEverythingOnIt() throws IOException {
        PixelCanvas lit = sturmpanzer(0f).light(Light.overcast());
        PixelCanvas sheet = new PixelCanvas(SIZE * 2 + 30, SIZE + 20);
        sheet.fill(0xFF23251E);
        sheet.rect(SIZE + 20, 0, SIZE + 10, sheet.height(), 0xFFB9B5A6);
        sheet.blit(lit, 10, 10);
        sheet.blit(lit, SIZE + 30, 10);
        save(sheet, "sculpt-vehicle.png");

        assertTrue("the hull should not be a flat colour", distinct(lit) > 400);
    }

    /**
     * A Regime assault gun, east-facing, in the sculpting vocabulary.
     *
     * <p>Written as a description of a vehicle rather than as coordinates: a hull, a glacis, a
     * fighting compartment, an engine deck with louvres over it, running gear, and the fittings
     * a crew would actually have bolted on. The numbers are proportions of the canvas so the same
     * recipe holds at any resolution.
     */
    private Sculptor sturmpanzer(float treadPhase) {
        Sculptor s = new Sculptor(SIZE, SIZE);
        // Albedo, not a shade index. The ramps are pre-shaded, so picking a middle index and
        // then lighting it shades the surface twice - which is how the first pass of this hull
        // came out as a black silhouette with a few grey scratches on it.
        int hull = WolfPalette.albedo(WolfPalette.NIGHT);
        int hullDark = WolfPalette.shade(WolfPalette.NIGHT, 1);
        int steel = WolfPalette.albedo(WolfPalette.GUNMETAL);
        int steelDark = WolfPalette.shade(WolfPalette.GUNMETAL, 1);
        int bare = WolfPalette.albedo(WolfPalette.STEEL);
        int rubber = WolfPalette.shade(WolfPalette.NIGHT, 2);

        float cx = SIZE * 0.5f;
        float cy = SIZE * 0.5f;

        // --- running gear, underneath everything ---------------------------------------------
        for (int side = 0; side < 2; side++) {
            float ty = cy + (side == 0 ? -1f : 1f) * SIZE * 0.235f;
            Machine.trackRun(s, cx - SIZE * 0.36f, ty, cx + SIZE * 0.36f, ty, SIZE * 0.052f,
                    0f, steelDark, treadPhase);
            for (int i = 0; i < 6; i++) {
                Machine.roadWheel(s, cx - SIZE * 0.29f + i * SIZE * 0.115f, ty, SIZE * 0.042f,
                        SIZE * 0.012f, rubber, steel);
            }
            // Drive sprocket at the back, idler at the front: different, because they are.
            Machine.roadWheel(s, cx - SIZE * 0.355f, ty, SIZE * 0.036f, SIZE * 0.012f, steel,
                    steel);
            s.disc(cx + SIZE * 0.355f, ty, SIZE * 0.038f, SIZE * 0.014f, SIZE * 0.02f,
                    Form.BEVEL, steel, Sculptor.STEEL);
            Machine.boltRing(s, cx + SIZE * 0.355f, ty, SIZE * 0.024f, 10, SIZE * 0.005f,
                    SIZE * 0.034f, steelDark);
            // Track guards over the top run.
            Machine.plate(s, cx - SIZE * 0.36f, ty - SIZE * 0.072f, SIZE * 0.72f, SIZE * 0.03f,
                    2f, SIZE * 0.055f, SIZE * 0.012f, hullDark, Sculptor.PAINT, 31 + side);
        }

        // --- lower hull -----------------------------------------------------------------------
        Machine.plate(s, cx - SIZE * 0.33f, cy - SIZE * 0.2f, SIZE * 0.66f, SIZE * 0.4f, 5f,
                SIZE * 0.06f, SIZE * 0.05f, hull, Sculptor.PAINT, 7);

        // Glacis: a sloped plate at the bow, drawn as a polygon because that is what it is.
        s.polygon(new float[] {
                cx + SIZE * 0.20f, cy - SIZE * 0.19f,
                cx + SIZE * 0.35f, cy - SIZE * 0.10f,
                cx + SIZE * 0.35f, cy + SIZE * 0.10f,
                cx + SIZE * 0.20f, cy + SIZE * 0.19f},
                SIZE * 0.055f, SIZE * 0.045f, Form.BEVEL, hull, Sculptor.PAINT);
        Machine.weld(s, cx + SIZE * 0.20f, cy - SIZE * 0.19f, cx + SIZE * 0.20f, cy + SIZE * 0.19f,
                SIZE * 0.008f, SIZE * 0.115f, steelDark, 3);

        // Plate seams down the hull, which is what says it was welded up from sheets.
        Machine.seam(s, cx - SIZE * 0.33f, cy - SIZE * 0.06f, cx + SIZE * 0.20f,
                cy - SIZE * 0.06f, SIZE * 0.005f, SIZE * 0.012f);
        Machine.seam(s, cx - SIZE * 0.33f, cy + SIZE * 0.06f, cx + SIZE * 0.20f,
                cy + SIZE * 0.06f, SIZE * 0.005f, SIZE * 0.012f);
        Machine.seam(s, cx - SIZE * 0.10f, cy - SIZE * 0.2f, cx - SIZE * 0.10f, cy + SIZE * 0.2f,
                SIZE * 0.005f, SIZE * 0.012f);

        // --- engine deck at the rear, with louvres cut into it --------------------------------
        Machine.plate(s, cx - SIZE * 0.32f, cy - SIZE * 0.17f, SIZE * 0.19f, SIZE * 0.34f, 3f,
                SIZE * 0.115f, SIZE * 0.018f, hullDark, Sculptor.PAINT, 13);
        Machine.grille(s, cx - SIZE * 0.30f, cy - SIZE * 0.14f, SIZE * 0.15f, SIZE * 0.28f, 9,
                SIZE * 0.016f);
        Machine.boltLine(s, cx - SIZE * 0.32f, cy - SIZE * 0.17f, cx - SIZE * 0.13f,
                cy - SIZE * 0.17f, SIZE * 0.038f, SIZE * 0.0075f, SIZE * 0.133f, hullDark);
        Machine.boltLine(s, cx - SIZE * 0.32f, cy + SIZE * 0.17f, cx - SIZE * 0.13f,
                cy + SIZE * 0.17f, SIZE * 0.038f, SIZE * 0.0075f, SIZE * 0.133f, hullDark);

        // Exhausts, up the back.
        for (int i = 0; i < 2; i++) {
            float ey = cy + (i == 0 ? -1f : 1f) * SIZE * 0.10f;
            s.capsule(cx - SIZE * 0.37f, ey, cx - SIZE * 0.31f, ey, SIZE * 0.022f,
                    SIZE * 0.09f, SIZE * 0.022f, Form.ROUND, steelDark, Sculptor.LEATHER);
            s.carveCapsule(cx - SIZE * 0.375f, ey, cx - SIZE * 0.36f, ey, SIZE * 0.016f,
                    SIZE * 0.03f, Form.ROUND);
            s.stain(cx - SIZE * 0.39f, ey - SIZE * 0.03f, SIZE * 0.06f, SIZE * 0.06f,
                    WolfPalette.shade(WolfPalette.SMOKE, 4), 0.55f);
        }

        // --- fighting compartment -------------------------------------------------------------
        Machine.plate(s, cx - SIZE * 0.11f, cy - SIZE * 0.155f, SIZE * 0.30f, SIZE * 0.31f, 4f,
                SIZE * 0.115f, SIZE * 0.06f, hull, Sculptor.PAINT, 17);
        Machine.weld(s, cx - SIZE * 0.11f, cy - SIZE * 0.155f, cx - SIZE * 0.11f,
                cy + SIZE * 0.155f, SIZE * 0.007f, SIZE * 0.175f, steelDark, 9);
        Machine.boltLine(s, cx - SIZE * 0.09f, cy - SIZE * 0.14f, cx + SIZE * 0.17f,
                cy - SIZE * 0.14f, SIZE * 0.042f, SIZE * 0.008f, SIZE * 0.175f, hullDark);
        Machine.boltLine(s, cx - SIZE * 0.09f, cy + SIZE * 0.14f, cx + SIZE * 0.17f,
                cy + SIZE * 0.14f, SIZE * 0.042f, SIZE * 0.008f, SIZE * 0.175f, hullDark);

        // Commander's hatch and a loader's hatch beside it.
        Machine.hatch(s, cx - SIZE * 0.02f, cy - SIZE * 0.07f, SIZE * 0.055f, SIZE * 0.175f,
                hull);
        Machine.hatch(s, cx - SIZE * 0.02f, cy + SIZE * 0.075f, SIZE * 0.042f, SIZE * 0.175f,
                hull);

        // Vision blocks: recessed slots with a steel lip over each.
        for (int i = 0; i < 3; i++) {
            float vy = cy - SIZE * 0.10f + i * SIZE * 0.10f;
            s.carveBox(cx + SIZE * 0.15f, vy - SIZE * 0.016f, SIZE * 0.035f, SIZE * 0.032f,
                    2f, SIZE * 0.02f, Form.BEVEL);
            s.box(cx + SIZE * 0.148f, vy - SIZE * 0.024f, SIZE * 0.04f, SIZE * 0.01f, 1.5f,
                    SIZE * 0.175f, SIZE * 0.012f, Form.BEVEL, steel, Sculptor.STEEL);
        }

        // --- mantlet and gun ------------------------------------------------------------------
        s.disc(cx + SIZE * 0.185f, cy, SIZE * 0.075f, SIZE * 0.16f, SIZE * 0.065f, Form.DOME,
                hull, Sculptor.PAINT);
        Machine.boltRing(s, cx + SIZE * 0.185f, cy, SIZE * 0.058f, 12, SIZE * 0.006f,
                SIZE * 0.19f, hullDark);
        Machine.barrel(s, cx + SIZE * 0.23f, cy, cx + SIZE * 0.455f, cy, SIZE * 0.026f,
                SIZE * 0.2f, steel);

        // A coaxial machine gun beside the main armament, because a tank has one.
        Machine.barrel(s, cx + SIZE * 0.22f, cy - SIZE * 0.055f, cx + SIZE * 0.33f,
                cy - SIZE * 0.055f, SIZE * 0.009f, SIZE * 0.185f, steelDark);

        // --- stowage, the thing that makes a vehicle look crewed ------------------------------
        Machine.plate(s, cx - SIZE * 0.30f, cy - SIZE * 0.235f, SIZE * 0.10f, SIZE * 0.055f, 3f,
                SIZE * 0.135f, SIZE * 0.022f, WolfPalette.albedo(WolfPalette.LEATHER),
                Sculptor.LEATHER, 23);
        for (int i = 0; i < 3; i++) {
            s.capsule(cx + SIZE * 0.02f + i * SIZE * 0.035f, cy - SIZE * 0.225f,
                    cx + SIZE * 0.02f + i * SIZE * 0.035f, cy - SIZE * 0.185f, SIZE * 0.013f,
                    SIZE * 0.13f, SIZE * 0.013f, Form.ROUND,
                    WolfPalette.albedo(WolfPalette.OLIVE), Sculptor.LEATHER);
        }
        // Spare track links hung on the flank, which is where crews actually put them.
        for (int i = 0; i < 4; i++) {
            s.box(cx - SIZE * 0.06f + i * SIZE * 0.032f, cy + SIZE * 0.19f, SIZE * 0.028f,
                    SIZE * 0.042f, 2f, SIZE * 0.13f, SIZE * 0.014f, Form.BEVEL, steelDark,
                    Sculptor.STEEL);
        }
        // A towing cable coiled along the deck.
        s.capsule(cx - SIZE * 0.28f, cy + SIZE * 0.20f, cx - SIZE * 0.02f, cy + SIZE * 0.205f,
                SIZE * 0.009f, SIZE * 0.125f, SIZE * 0.009f, Form.ROUND, steelDark,
                Sculptor.STEEL);

        // --- markings and wear -----------------------------------------------------------------
        s.stain(cx - SIZE * 0.06f, cy - SIZE * 0.13f, SIZE * 0.05f, SIZE * 0.02f,
                WolfPalette.albedo(WolfPalette.BLOOD), 0.85f);
        Machine.chip(s, cx + SIZE * 0.20f, cy - SIZE * 0.19f, cx + SIZE * 0.35f, cy - SIZE * 0.10f,
                bare, 9, 5);
        Machine.chip(s, cx - SIZE * 0.33f, cy - SIZE * 0.2f, cx - SIZE * 0.33f, cy + SIZE * 0.2f,
                bare, 7, 11);
        s.stain(cx - SIZE * 0.36f, cy - SIZE * 0.26f, SIZE * 0.72f, SIZE * 0.06f,
                WolfPalette.albedo(WolfPalette.DIRT), 0.18f);
        s.stain(cx - SIZE * 0.36f, cy + SIZE * 0.20f, SIZE * 0.72f, SIZE * 0.06f,
                WolfPalette.albedo(WolfPalette.DIRT), 0.22f);

        return s;
    }

    private int distinct(PixelCanvas c) {
        java.util.HashSet<Integer> seen = new java.util.HashSet<Integer>();
        for (int y = 0; y < c.height(); y += 2) {
            for (int x = 0; x < c.width(); x += 2) {
                seen.add(Integer.valueOf(c.get(x, y)));
            }
        }
        return seen.size();
    }

    private void save(PixelCanvas canvas, String name) throws IOException {
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
                canvas.pixelWidth(), canvas.pixelHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, canvas.pixelWidth(), canvas.pixelHeight(), canvas.pixels(), 0,
                canvas.pixelWidth());
        Frame.write(out, name);
    }
}
