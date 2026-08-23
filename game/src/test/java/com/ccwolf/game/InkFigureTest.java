package com.ccwolf.game;

import com.ccwolf.game.art.Ink;
import com.ccwolf.game.art.InkBody;
import com.ccwolf.game.art.PixelCanvas;
import com.ccwolf.game.art.WolfPalette;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * A Kreisau partisan, blocked in from the body vocabulary, seen from straight above.
 *
 * <p>This is the scaffolding stage of the drawn pipeline, and it is worth being clear about what
 * it is for. The recipe will never be the art. It gets the proportions right and produces a grid
 * worth correcting — and then the corrections are what ship.
 *
 * <p>Overhead, the question the sheet has to answer is a different one from the question the
 * three-quarter version asked. It is no longer "is this one man turning", because the parts are
 * placed in his own frame and the turn is arithmetic. It is "can you tell which way he is
 * pointed", and the things that have to answer it are the lid, the shoulders and the weapon out
 * in front — because from directly above there is nothing else.
 */
public class InkFigureTest {

    static {
        Frame.useAwtBackend();
    }

    private static final int GRID = 64;
    private static final int SHOWN = 2;

    private static final int PALE = 0xFFB9B5A6;
    private static final int DARK = 0xFF23251E;

    // Olive blouse, grey wool trousers, canvas webbing. The trousers are a different ramp from
    // the boots on purpose: at this size two browns stacked read as one brown column, which is
    // what the first sheet showed from the knee down.
    // Olive blouse, grey wool trousers, canvas webbing, a khaki-painted salvaged helmet. The
    // trousers are a different ramp from the boots and the helmet from the blouse for the same
    // reason: at this size two neighbouring browns are one brown, and the helmet is the shape
    // that has to separate before anything else does.
    private static final int[] KREISAU = Ink.tones(WolfPalette.OLIVE, WolfPalette.STONE,
            WolfPalette.LEATHER, WolfPalette.FLESH, WolfPalette.STONE,
            WolfPalette.shade(WolfPalette.BLOOD, 1));

    @Test
    public void onePartisanAtEightFacings() throws IOException {
        int cell = GRID * SHOWN;
        int pad = 6;
        PixelCanvas sheet = new PixelCanvas(pad + 8 * (cell + pad),
                pad + 2 * (cell + pad) + GRID + pad);
        sheet.fill(PALE);
        sheet.rect(0, pad + cell + pad / 2, sheet.width(), cell + pad, DARK);
        sheet.rect(0, pad + 2 * (cell + pad) - pad / 2, sheet.width(), GRID + pad + pad,
                0xFF6E6455);

        for (int facing = 0; facing < InkBody.FACINGS; facing++) {
            Ink ink = partisan(facing, 0);
            int x = pad + facing * (cell + pad);
            sheet.blit(ink.toCanvas(KREISAU, SHOWN).fine(), x, pad);
            sheet.blit(ink.toCanvas(KREISAU, SHOWN).fine(), x, pad + cell + pad);
            // And the art pixels themselves, on a third ground, one screen pixel each.
            sheet.blit(ink.toCanvas(KREISAU, 1), x, pad + 2 * (cell + pad));
        }
        save(sheet, "ink-partisan.png");
        assertTrue(true);
    }

    /**
     * Two facings, very large, with the grid lines showing.
     *
     * <p>Reviewed at the size the game shows him, a wrong pixel is a slight lumpiness and nothing
     * more; the last three faults in this figure were all found by measuring rather than by
     * looking, which is a bad sign about the looking. This is the sheet where a shape can actually
     * be judged as a shape — the counterpart to the one-screen-pixel row, not a replacement for
     * it.
     */
    @Test
    public void twoFacingsCloseEnoughToJudge() throws IOException {
        int big = 8;
        int cell = GRID * big;
        int pad = 8;
        int[] facings = {1, 2};
        PixelCanvas sheet = new PixelCanvas(pad + facings.length * (cell + pad), pad + cell + pad);
        sheet.fill(PALE);
        for (int i = 0; i < facings.length; i++) {
            sheet.blit(partisan(facings[i], 0).toCanvas(KREISAU, big).fine(),
                    pad + i * (cell + pad), pad);
        }
        save(sheet, "ink-partisan-close.png");
        assertTrue(true);
    }

    /**
     * The walk, so the stride is read as motion rather than as four unrelated poses.
     *
     * <p>Overhead a walk is almost entirely the boots sliding past each other along his line of
     * march, with the shoulders rocking a little. That is less than a side view gets, but it is
     * honest, and a four-frame cycle at twenty ticks a second does not need more.
     */
    @Test
    public void theWalkReadsAsAWalk() throws IOException {
        int cell = GRID * SHOWN;
        int pad = 6;
        int[] facings = {0, 1, 2, 6};
        PixelCanvas sheet = new PixelCanvas(pad + facings.length * (cell + pad),
                pad + 4 * (cell + pad));
        sheet.fill(PALE);
        for (int frame = 0; frame < 4; frame++) {
            for (int i = 0; i < facings.length; i++) {
                Ink ink = partisan(facings[i], frame);
                sheet.blit(ink.toCanvas(KREISAU, SHOWN).fine(), pad + i * (cell + pad),
                        pad + frame * (cell + pad));
            }
        }
        save(sheet, "ink-partisan-walk.png");
        assertTrue(true);
    }

    // --- the recipe ----------------------------------------------------------------------------

    /**
     * A Kreisau partisan: salvaged Allied kit, a steel helmet and a submachine gun.
     *
     * <p>Drawn back to front, because a painter's grid has no depth test and draw order is the
     * only layering there is. Boots underneath, then the pack and the back, then the arms and the
     * weapon over them, and the helmet last because from above the helmet is on top of everything.
     *
     * <p>Each group is shaded as it is laid down rather than all at the end. Shading follows the
     * silhouette, and a part is the silhouette only until the next part covers it.
     */
    private Ink partisan(int facing, int frame) {
        Ink ink = new Ink(GRID, GRID);
        InkBody.Figure f = new InkBody.Figure(ink, facing, GRID / 2f, GRID / 2f, 1.15f);

        // Four frames: the boots swing along his line of march. Nothing else pumps - an earlier
        // frame set changed the shoulder radii with the stride and the body breathed like a
        // bellows instead of walking.
        float[] swing = {0f, 3.5f, 0f, -3.5f};
        float stride = swing[frame % swing.length];

        InkBody.boots(f, stride, Ink.BOOT);
        ink.shade(Ink.BOOT, Ink.BOOT_DARK, 1, 1, 2);

        InkBody.pack(f, 4f, Ink.KIT, Ink.KIT_DARK);
        InkBody.shoulders(f, 10.5f, 7.5f, Ink.COAT);
        InkBody.webbing(f, 8f, Ink.KIT_DARK);
        ink.shade(Ink.COAT, Ink.COAT_DARK, 1, 1, 3);

        // Both hands on the weapon: the near one at the grip, the far one at the fore-end.
        InkBody.arm(f, 1f, 2.2f, 4.5f, Ink.COAT, Ink.SKIN);
        InkBody.arm(f, -1f, 0.8f, 8f, Ink.COAT, Ink.SKIN);
        InkBody.weapon(f, 15f, 1.4f, Ink.METAL, Ink.WOOD);
        InkBody.magazine(f, Ink.METAL_DARK);

        InkBody.helmet(f, Ink.HELM, Ink.HELM_DARK, Ink.HELM_LIGHT);
        ink.outline();
        return ink;
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
