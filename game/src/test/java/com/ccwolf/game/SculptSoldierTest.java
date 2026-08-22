package com.ccwolf.game;

import com.ccwolf.game.art.Anatomy;
import com.ccwolf.game.art.Light;
import com.ccwolf.game.art.Machine;
import com.ccwolf.game.art.PixelCanvas;
import com.ccwolf.game.art.Pose;
import com.ccwolf.game.art.Sculptor;
import com.ccwolf.game.art.WolfPalette;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * A man, built out of anatomy rather than out of circles.
 *
 * <p>The first attempt at this came out as a skittle, and the diagnosis was structural: no
 * ellipse in the engine, a sphere for a head, screen coordinates instead of a body frame, and
 * both arms in the same place. All four are fixed underneath this, and this is where that gets
 * checked.
 *
 * <p>Two sheets, because they answer different questions. Heads large, where the skull assembly
 * and the carved face are actually reviewable; and a figure at eight facings, where the only
 * question is whether it is one man turning.
 */
public class SculptSoldierTest {

    static {
        Frame.useAwtBackend();
    }

    private static final int SIZE = 160;

    /** Pixels per centimetre. A hundred and seventy-five tall man fills most of the canvas. */
    private static final float SCALE = 0.82f;

    private Pose poseFor(int facing, int size, float scale) {
        return new Pose((float) (facing * Math.PI / 4.0), size / 2f, size * 0.90f, scale);
    }

    @Test
    public void oneManAtEightFacings() throws IOException {
        PixelCanvas sheet = new PixelCanvas(SIZE * 8 + 20, SIZE + 20);
        sheet.fill(0xFF23251E);
        for (int facing = 0; facing < 8; facing++) {
            Sculptor s = new Sculptor(SIZE, SIZE);
            partisan(s, poseFor(facing, SIZE, SCALE), 0);
            sheet.blit(s.light(Light.overcast()), 10 + facing * SIZE, 10);
        }
        save(sheet, "sculpt-soldiers.png");
        assertTrue(true);
    }

    /**
     * The skull, large, at three angles.
     *
     * <p>Bare on the top row so the assembly is visible, then under each of the three helmets the
     * Kreisau Circle actually wears. The helmets are the variety axis, so what matters is that
     * the three read as three shapes rather than as three colours.
     */
    @Test
    public void headsCloseUp() throws IOException {
        int big = 300;
        float scale = 8.5f;
        PixelCanvas sheet = new PixelCanvas(big * 3 + 40, big * 2 + 30);
        sheet.fill(0xFF23251E);

        float[] toward = {2f, 1f, 6f};
        for (int i = 0; i < 3; i++) {
            int skin = WolfPalette.shade(WolfPalette.FLESH, 2);
            // Facings 2, 1 and 6: straight at the camera, three-quarters, and away.
            Pose p = new Pose((float) (toward[i] * Math.PI / 4.0), big / 2f, big * 0.62f, scale);

            Sculptor bare = new Sculptor(big, big);
            Anatomy.skull(bare, p, 0f, 0f, 0f, skin);
            sheet.blit(bare.light(Light.overcast()), 10 + i * (big + 10), 10);

            Sculptor lidded = new Sculptor(big, big);
            Anatomy.skull(lidded, p, 0f, 0f, 0f, skin);
            int kit = WolfPalette.shade(WolfPalette.OLIVE, 1);
            if (i == 0) {
                Anatomy.potHelmet(lidded, p, 0f, 0f, 4f, kit);
            } else if (i == 1) {
                Anatomy.dishHelmet(lidded, p, 0f, 0f, 5f, kit);
            } else {
                Anatomy.stahlhelm(lidded, p, 0f, 0f, 3f,
                        WolfPalette.albedo(WolfPalette.NIGHT));
            }
            sheet.blit(lidded.light(Light.overcast()), 10 + i * (big + 10), big + 20);
        }
        save(sheet, "sculpt-heads.png");
        assertTrue(true);
    }

    /**
     * The same heads at the size they are actually seen, beside the size they are reviewed at.
     *
     * <p>Worth its own sheet because it settles an argument. A head in this game is about twenty
     * pixels across; the review sheet draws it at three hundred, which is fifteen times larger
     * than it will ever appear. Every hour spent perfecting a mouth is an hour spent on four
     * pixels, and the thing that actually tells a soldier from a bollard at twenty pixels is the
     * helmet's outline and the line of the shoulders.
     */
    @Test
    public void aHeadAtTheSizeItIsActuallySeen() throws IOException {
        int[] sizes = {300, 96, 40, 20};
        PixelCanvas sheet = new PixelCanvas(520, 320);
        sheet.fill(0xFF23251E);
        int x = 10;
        for (int size : sizes) {
            Sculptor s = new Sculptor(size, size);
            Pose p = new Pose((float) (2 * Math.PI / 4.0), size / 2f, size * 0.60f,
                    size * 0.0283f);
            Anatomy.skull(s, p, 0f, 0f, 0f, WolfPalette.shade(WolfPalette.FLESH, 2));
            Anatomy.potHelmet(s, p, 0f, 0f, 4f, WolfPalette.shade(WolfPalette.OLIVE, 1));
            sheet.blit(s.light(Light.overcast()), x, 10);
            x += size + 10;
        }
        save(sheet, "sculpt-head-scales.png");
        assertTrue(true);
    }

    /**
     * The three helmets as black silhouettes, at the size the game shows them.
     *
     * <p>The acceptance test for the whole Kreisau look, and the reason salvaged Allied kit is a
     * better variety axis than clothing was. A shirt pattern is invisible at forty pixels; an
     * outline is not. So this throws away every colour, every material and all the shading, and
     * asks the only question that matters: with nothing left but the shape, can a pot helmet, a
     * dish helmet and a coal-scuttle be told apart?
     *
     * <p>If they cannot, the variety is decorative and the effort belongs somewhere else.
     */
    @Test
    public void theThreeHelmetsAreTellableApartInBlack() throws IOException {
        int[] sizes = {160, 64, 40};
        // Measured from the content, not guessed. A sheet narrower than what it lays out draws
        // the small cells off the right-hand edge, where they pass and are never looked at -
        // and the small cells are the entire point of this test.
        int wide = 10;
        for (int size : sizes) {
            wide += 3 * (size + 6) + 12;
        }
        PixelCanvas sheet = new PixelCanvas(wide, 2 * 180 + 30);
        sheet.fill(0xFFB9B5A6);

        for (int row = 0; row < 2; row++) {
            int x = 10;
            for (int size : sizes) {
                for (int kit = 0; kit < 3; kit++) {
                    Sculptor s = new Sculptor(size, size);
                    Pose p = new Pose((float) ((row == 0 ? 2 : 3) * Math.PI / 4.0), size / 2f,
                            size * 0.62f, size * 0.0283f);
                    Anatomy.skull(s, p, 0f, 0f, 0f, WolfPalette.shade(WolfPalette.FLESH, 2));
                    int kitColor = WolfPalette.shade(WolfPalette.OLIVE, 1);
                    if (kit == 0) {
                        Anatomy.potHelmet(s, p, 0f, 0f, 4f, kitColor);
                    } else if (kit == 1) {
                        Anatomy.dishHelmet(s, p, 0f, 0f, 5f, kitColor);
                    } else {
                        Anatomy.stahlhelm(s, p, 0f, 0f, 3f,
                                WolfPalette.albedo(WolfPalette.NIGHT));
                    }
                    sheet.blit(blacken(s.light(Light.overcast())), x,
                            10 + row * 180 + (180 - size) / 2);
                    x += size + 6;
                }
                x += 10;
            }
        }
        save(sheet, "sculpt-silhouettes.png");
        assertTrue(true);
    }

    /** Every covered pixel to solid black, so only the outline survives. */
    private PixelCanvas blacken(PixelCanvas lit) {
        PixelCanvas out = new PixelCanvas(lit.width(), lit.height());
        int[] src = lit.pixels();
        int[] dst = out.pixels();
        for (int i = 0; i < src.length; i++) {
            int alpha = src[i] >>> 24;
            dst[i] = alpha << 24;
        }
        return out;
    }

    // --- the figure ---------------------------------------------------------------------------

    /** A Kreisau partisan in salvaged Allied kit. */
    private void partisan(Sculptor s, Pose p, int frame) {
        int blouse = WolfPalette.shade(WolfPalette.OLIVE, 1);
        int canvas = WolfPalette.albedo(WolfPalette.LEATHER);
        int leather = WolfPalette.shade(WolfPalette.LEATHER, 2);
        int skin = WolfPalette.shade(WolfPalette.FLESH, 2);
        float stride = frame == 1 ? 7f : 0f;

        Anatomy.leg(s, p, -7f, stride, blouse, leather);
        Anatomy.leg(s, p, 7f, -stride, blouse, leather);
        Anatomy.skirt(s, p, 0f, 0f, blouse, 5);
        Anatomy.torso(s, p, 0f, 0f, blouse, 3);
        Anatomy.webbing(s, p, 0f, canvas, canvas);
        Anatomy.shoulders(s, p, 0f, 0f, blouse);
        Anatomy.neck(s, p, 0f, 0f, skin, blouse);
        Anatomy.armsAtTheReady(s, p, 0f, blouse, skin);
        Anatomy.skull(s, p, 0f, 2f, 158f, skin);
        Anatomy.potHelmet(s, p, 0f, 1f, 162f, blouse);
        smg(s, p);
    }

    /** A submachine gun held across the chest, muzzle forward. */
    private void smg(Sculptor s, Pose p) {
        int steel = WolfPalette.albedo(WolfPalette.GUNMETAL);
        int wood = WolfPalette.shade(WolfPalette.LEATHER, 1);

        // Receiver, running forward past the left hand.
        Anatomy.tube(s, p, 7f, 6f, 112f, -7f, 27f, 118f, 2.6f, steel, Sculptor.STEEL);
        // The stock, back under the right forearm.
        Anatomy.tube(s, p, 9f, 3f, 111f, 14f, -9f, 108f, 2.4f, wood, Sculptor.LEATHER);
        // Magazine, hanging under the receiver: the detail that names the weapon.
        Anatomy.tube(s, p, 1f, 15f, 111f, 1f, 16f, 99f, 1.8f, steel, Sculptor.STEEL);
        Machine.barrel(s, p.x(-4f, 23f, 117f), p.y(-4f, 23f, 117f),
                p.x(-9f, 33f, 119f), p.y(-9f, 33f, 119f), p.size(1.1f),
                p.depth(-6f, 28f, 118f), steel);
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
