package com.ccwolf.game;

import com.ccwolf.game.art.Ink;
import com.ccwolf.game.art.PixelCanvas;
import com.ccwolf.game.art.WolfPalette;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The drawn pipeline's shape card.
 *
 * <p>Same job the sculpt card does for the other pipeline: every primitive, once, before anything
 * is built on it. What is different here is the sizes it is looked at. A pixel-art sheet has to be
 * reviewed at <b>one screen pixel per art pixel</b>, where the pixels themselves are inspectable
 * and a stray one is obvious, <em>and</em> at the size the game shows it, where the question is
 * whether the shape reads. Neither on its own is enough: the sculpted side lost most of a session
 * to sheets drawn fifteen times larger than anything the game renders.
 */
public class InkCardTest {

    static {
        Frame.useAwtBackend();
    }

    /** The grid a footsoldier is authored on. */
    private static final int GRID = 64;

    /** Screen pixels per art pixel, as the game shows a man on a hundred-and-twenty-eight tile. */
    private static final int SHOWN = 2;

    private static final int PALE = 0xFFB9B5A6;
    private static final int DARK = 0xFF23251E;

    private static final int[] TONES = Ink.tones(WolfPalette.OLIVE, WolfPalette.LEATHER,
            WolfPalette.LEATHER, WolfPalette.FLESH, WolfPalette.shade(WolfPalette.BLOOD, 1));

    @Test
    public void everyPrimitiveAtBothSizes() throws IOException {
        int cols = 5;
        int cell = GRID * SHOWN;
        int pad = 8;
        PixelCanvas sheet = new PixelCanvas(pad + cols * (cell + pad),
                pad + cell + pad + GRID + pad);
        sheet.fill(DARK);
        sheet.rect(0, 0, sheet.width(), pad + cell + pad / 2, PALE);

        for (int col = 0; col < cols; col++) {
            Ink ink = new Ink(GRID, GRID);
            primitive(ink, col);
            ink.outline();
            int x = pad + col * (cell + pad);
            sheet.blit(ink.toCanvas(TONES, SHOWN).fine(), x, pad);
            // The same sprite at one screen pixel per art pixel, directly under it. This is the
            // row where a pixel in the wrong place is a pixel in the wrong place rather than a
            // slight softness in a shape.
            sheet.blit(ink.toCanvas(TONES, 1), x, pad + cell + pad);
        }
        save(sheet, "ink-shapes.png");
        assertTrue(true);
    }

    private void primitive(Ink ink, int which) {
        float c = GRID / 2f;
        switch (which) {
            case 0:
                ink.ellipse(c, c, 22f, 13f, 0.6f, Ink.COAT);
                break;
            case 1:
                ink.capsule(c - 16f, c + 15f, c + 16f, c - 15f, 8f, Ink.COAT);
                break;
            case 2:
                ink.taper(c - 15f, c + 17f, 12f, c + 15f, c - 18f, 2f, Ink.COAT);
                break;
            case 3:
                ink.box(c - 20f, c - 14f, 40f, 28f, 5f, Ink.COAT);
                break;
            default:
                ink.polygon(new float[] {c, c - 23f, c + 21f, c - 3f, c + 13f, c + 22f,
                        c - 13f, c + 22f, c - 21f, c - 3f}, Ink.COAT);
                break;
        }
    }

    /**
     * The three operations that are pixel art rather than geometry.
     *
     * <p>{@code dither} is how two tones make a third without growing the palette, {@code shade}
     * is how a shape gets a lit and a turned-away side from a rule rather than from a lighting
     * model, and {@code outline} is the one-art-pixel edge that holds a figure against the
     * ground. Each is shown before and after, at both sizes.
     */
    @Test
    public void ditherShadeAndOutline() throws IOException {
        int cell = GRID * SHOWN;
        int pad = 8;
        PixelCanvas sheet = new PixelCanvas(pad + 6 * (cell + pad),
                pad + cell + pad + GRID + pad);
        sheet.fill(DARK);
        sheet.rect(0, 0, sheet.width(), pad + cell + pad / 2, PALE);

        for (int col = 0; col < 6; col++) {
            Ink ink = new Ink(GRID, GRID);
            ink.box(14f, 14f, 36f, 36f, 6f, Ink.COAT);
            switch (col) {
                case 1:
                    ink.dither(14, 32, 36, 18, Ink.COAT, Ink.COAT_DARK);
                    break;
                case 2:
                    ink.dither(14, 32, 36, 9, Ink.COAT, Ink.COAT_DARK);
                    ink.rect(14, 41, 36, 9, Ink.COAT_DARK);
                    break;
                case 3:
                    ink.shade(Ink.COAT, Ink.COAT_DARK, 1, 1, 4);
                    break;
                case 4:
                    ink.outline();
                    break;
                case 5:
                    ink.shade(Ink.COAT, Ink.COAT_DARK, 1, 1, 4);
                    ink.dither(14, 30, 36, 8, Ink.COAT, Ink.COAT_DARK);
                    ink.outline();
                    break;
                default:
                    break;
            }
            int x = pad + col * (cell + pad);
            sheet.blit(ink.toCanvas(TONES, SHOWN).fine(), x, pad);
            sheet.blit(ink.toCanvas(TONES, 1), x, pad + cell + pad);
        }
        save(sheet, "ink-operations.png");
        assertTrue(true);
    }

    /**
     * Every tone slot, and the same grid wearing two different sets of them.
     *
     * <p>The second half is the claim that makes slots worth having instead of colours: one grid,
     * two factions. If a Resistance partisan and a Regime soldier can share a body and differ by
     * a row of tones, then the drawing and the palette are genuinely separable and a change of
     * uniform costs nothing.
     */
    @Test
    public void slotsAreColouredLast() throws IOException {
        int[] regime = Ink.tones(WolfPalette.NIGHT, WolfPalette.NIGHT, WolfPalette.NIGHT,
                WolfPalette.FLESH, WolfPalette.shade(WolfPalette.BLOOD, 1));
        int swatch = 20;
        int pad = 6;
        int cell = GRID * SHOWN;
        PixelCanvas sheet = new PixelCanvas(
                Math.max(pad + Ink.SLOTS * (swatch + pad), pad + 2 * (cell + pad)),
                pad + swatch + pad + cell + pad);
        sheet.fill(DARK);

        for (byte tone = 1; tone < Ink.SLOTS; tone++) {
            Ink chip = new Ink(swatch, swatch);
            chip.rect(0, 0, swatch, swatch, tone);
            sheet.blit(chip.toCanvas(TONES, 1), pad + tone * (swatch + pad), pad);
        }

        Ink man = new Ink(GRID, GRID);
        blockedInFigure(man);
        man.outline();
        sheet.blit(man.toCanvas(TONES, SHOWN).fine(), pad, pad + swatch + pad);
        sheet.blit(man.toCanvas(regime, SHOWN).fine(), pad + cell + pad, pad + swatch + pad);
        save(sheet, "ink-tones.png");

        // Colour is attached at the very end and changes nothing about the drawing.
        assertArrayEquals(man.toGrid(), Ink.fromGrid(man.toGrid()).toGrid());
        assertTrue(true);
    }

    /**
     * A grid survives being written out as text and read back.
     *
     * <p>The load-bearing property of the whole pipeline, because the hand-fixing step is exactly
     * a round trip through text: the recipe blocks a figure in, it is dumped, the pixels that read
     * badly are corrected by hand, and the corrected grid is what ships. If the round trip were
     * lossy the hand corrections would be the thing it lost.
     */
    @Test
    public void aGridSurvivesTheRoundTripThroughText() {
        Ink ink = new Ink(GRID, GRID);
        blockedInFigure(ink);
        ink.outline();
        String[] dumped = ink.toGrid();
        assertEquals(GRID, dumped.length);
        assertEquals(GRID, dumped[0].length());

        Ink reloaded = Ink.fromGrid(dumped);
        assertEquals(GRID, reloaded.width());
        assertEquals(GRID, reloaded.height());
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                assertEquals("pixel " + x + "," + y, ink.at(x, y), reloaded.at(x, y));
            }
        }
    }

    /**
     * A figure blocked in from primitives — scaffolding, not art.
     *
     * <p>Deliberately crude. It exists so the card is judged against something with a small head
     * and thin limbs, which is where an outline either holds a shape together or closes the gaps
     * in it, and it is the shape the next commit's hand-fixing starts from.
     */
    private void blockedInFigure(Ink ink) {
        ink.capsule(26f, 40f, 25f, 58f, 4f, Ink.BOOT);
        ink.capsule(38f, 40f, 39f, 58f, 4f, Ink.BOOT);
        ink.box(22f, 20f, 20f, 22f, 5f, Ink.COAT);
        ink.capsule(23f, 24f, 20f, 38f, 3f, Ink.COAT);
        ink.capsule(41f, 24f, 44f, 38f, 3f, Ink.COAT);
        ink.capsule(18f, 34f, 46f, 30f, 2f, Ink.METAL);
        ink.ellipse(32f, 13f, 6f, 7f, 0f, Ink.SKIN);
        ink.ellipse(32f, 9f, 8f, 4f, 0f, Ink.COAT_LIGHT);
        ink.pixel(29, 13, Ink.EYE);
        ink.pixel(35, 13, Ink.EYE);
        ink.shade(Ink.COAT, Ink.COAT_DARK, 1, 1, 4);
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
