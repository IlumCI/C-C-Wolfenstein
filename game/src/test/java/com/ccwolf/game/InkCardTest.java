package com.ccwolf.game;

import com.ccwolf.game.art.Ink;
import com.ccwolf.game.art.PixelCanvas;
import com.ccwolf.game.art.WolfPalette;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The drawn pipeline's shape card.
 *
 * <p>Same job the sculpt card does for {@link com.ccwolf.game.art.Sculptor}: every primitive,
 * once, large enough to see what it actually does, before anything is built on top of it. The
 * sculpted side learned this the expensive way — an inverted groove profile and a set of bolts
 * that read as pearls both survived a green suite because nothing ever drew them on their own.
 *
 * <p>Two things beyond the primitives, because they are the two claims the drawn pipeline rests
 * on. The contour holds a figure against the ground at the size the game shows it; and the ramps
 * are used through {@code shade} rather than {@code albedo}, because a drawn sprite is unlit by
 * construction.
 */
public class InkCardTest {

    static {
        Frame.useAwtBackend();
    }

    private static final int CELL = 150;
    private static final int PAD = 8;

    /** Two grounds, because a contour that only works on one of them is not a contour. */
    private static final int PALE = 0xFFB9B5A6;
    private static final int DARK = 0xFF23251E;

    @Test
    public void everyPrimitiveOnce() throws IOException {
        int cols = 5;
        int rows = 2;
        PixelCanvas sheet = new PixelCanvas(PAD + cols * (CELL + PAD), PAD + rows * (CELL + PAD));
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                sheet.rect(PAD + col * (CELL + PAD), PAD + row * (CELL + PAD), CELL, CELL,
                        row == 0 ? PALE : DARK);
            }
        }

        for (int row = 0; row < rows; row++) {
            int tone = WolfPalette.shade(WolfPalette.OLIVE, row == 0 ? 1 : 0);
            for (int col = 0; col < cols; col++) {
                Ink ink = new Ink(CELL, CELL);
                primitive(ink, col, tone);
                ink.contour(2f, 0.55f);
                sheet.blit(ink.finish(), PAD + col * (CELL + PAD), PAD + row * (CELL + PAD));
            }
        }
        save(sheet, "ink-shapes.png");
        assertTrue(true);
    }

    private void primitive(Ink ink, int which, int tone) {
        float c = CELL / 2f;
        switch (which) {
            case 0:
                ink.ellipse(c, c, 52f, 30f, 0.6f, tone);
                break;
            case 1:
                ink.capsule(c - 38f, c + 34f, c + 38f, c - 34f, 20f, tone);
                break;
            case 2:
                ink.taper(c - 36f, c + 40f, 28f, c + 34f, c - 42f, 6f, tone);
                break;
            case 3:
                ink.box(c - 46f, c - 34f, 92f, 68f, 12f, tone);
                break;
            default:
                ink.polygon(new float[] {c, c - 54f, c + 50f, c - 6f, c + 30f, c + 52f,
                        c - 30f, c + 52f, c - 50f, c - 6f}, tone);
                break;
        }
    }

    /**
     * The contour at the sizes the game actually renders, on both grounds, at two widths.
     *
     * <p>The claim written into {@code Ink.contour} is that the outline is a width in
     * <em>output</em> pixels and does not scale with the sprite, because a contour that shrinks
     * with the art stops holding the figure at exactly the distance it is needed. That claim is
     * right and this sheet also shows its cost, which is why every size gets both widths: two
     * pixels of outline on a forty-pixel man is a tenth of his width, and it closes the gap
     * between his legs and welds his arms to his coat. One pixel there holds the silhouette
     * without eating it.
     *
     * <p>So the finding for the figures that come next: <b>two pixels above sixty, one below</b>,
     * and a drawn body must be authored with gaps no narrower than three pixels at the smallest
     * size it is shown, or the contour will close them whatever its width.
     */
    @Test
    public void theContourHoldsAtEverySize() throws IOException {
        int[] sizes = {160, 64, 40};
        int band = 190;
        int wide = PAD;
        for (int size : sizes) {
            wide += 3 * (size + PAD) + PAD;
        }
        PixelCanvas sheet = new PixelCanvas(wide, PAD + 2 * (band + PAD));
        sheet.fill(PALE);
        sheet.rect(0, PAD + band + PAD / 2, wide, band + PAD, DARK);

        for (int row = 0; row < 2; row++) {
            int x = PAD;
            for (int size : sizes) {
                for (float thickness : new float[] {0f, 1f, 2f}) {
                    Ink ink = new Ink(size, size);
                    figurine(ink, size, row == 0);
                    if (thickness > 0f) {
                        ink.contour(thickness, 0.6f);
                    }
                    sheet.blit(ink.finish(), x, PAD + row * (band + PAD) + (band - size) / 2);
                    x += size + PAD;
                }
                x += PAD;
            }
        }
        save(sheet, "ink-contour.png");
        assertTrue(true);
    }

    /**
     * A stand-in figure: a silhouette with the proportions of a man, in flat tones.
     *
     * <p>Not the Partisan — that is the next commit's job. This exists so the contour is judged
     * against something with thin limbs and a small head, which is where an outline either holds
     * a shape together or eats it.
     */
    private void figurine(Ink ink, int size, boolean lightKit) {
        float u = size / 100f;
        int coat = WolfPalette.shade(WolfPalette.OLIVE, lightKit ? 1 : 0);
        int dark = WolfPalette.shade(WolfPalette.LEATHER, 2);
        int skin = WolfPalette.shade(WolfPalette.FLESH, 1);
        float mid = size / 2f;

        ink.capsule(mid - 9 * u, 62 * u, mid - 11 * u, 92 * u, 6 * u, dark);
        ink.capsule(mid + 9 * u, 62 * u, mid + 11 * u, 92 * u, 6 * u, dark);
        ink.box(mid - 17 * u, 30 * u, 34 * u, 36 * u, 8 * u, coat);
        ink.capsule(mid - 16 * u, 36 * u, mid - 21 * u, 58 * u, 5 * u, coat);
        ink.capsule(mid + 16 * u, 36 * u, mid + 21 * u, 58 * u, 5 * u, coat);
        ink.capsule(mid - 24 * u, 50 * u, mid + 24 * u, 44 * u, 3 * u, dark);
        ink.ellipse(mid, 21 * u, 9 * u, 10 * u, 0f, skin);
        ink.ellipse(mid, 16 * u, 12 * u, 7 * u, 0f, coat);
    }

    /**
     * One ramp, all its tones, drawn as authored.
     *
     * <p>The sculpted path calls {@code albedo} and lets the light generate the rest; calling
     * {@code shade} there produced a black tank with grey scratches, because the ramps are
     * pre-shaded and lighting them twice crushes them. This is the other half of that rule, and
     * the sheet is what makes it checkable: these swatches should walk evenly from highlight to
     * shadow with no step collapsing into its neighbour.
     */
    @Test
    public void theRampIsUsedAsAuthored() throws IOException {
        int[][] ramps = {WolfPalette.OLIVE, WolfPalette.LEATHER, WolfPalette.FLESH,
                WolfPalette.NIGHT, WolfPalette.BONE};
        int steps = WolfPalette.rampLength();
        assertEquals(steps, ramps[0].length);
        int swatch = 64;
        PixelCanvas sheet = new PixelCanvas(PAD + steps * (swatch + PAD),
                PAD + ramps.length * (swatch + PAD));
        sheet.fill(DARK);
        for (int r = 0; r < ramps.length; r++) {
            for (int i = 0; i < steps; i++) {
                Ink ink = new Ink(swatch, swatch);
                ink.box(4f, 4f, swatch - 8f, swatch - 8f, 6f, WolfPalette.shade(ramps[r], i));
                ink.contour(2f, 0.5f);
                sheet.blit(ink.finish(), PAD + i * (swatch + PAD), PAD + r * (swatch + PAD));
            }
        }
        save(sheet, "ink-tones.png");
        assertTrue(true);
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
