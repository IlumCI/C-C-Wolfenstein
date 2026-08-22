package com.ccwolf.game;

import com.ccwolf.game.art.Anatomy;
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
 * Two soldiers, and the two things about people this engine had to be taught.
 *
 * <p>The first is skin. Everything else in the game reflects light; flesh passes it through and
 * gives it back warm, which is why a shadowed cheek is reddish where a shadowed helmet is
 * blue-grey. Shade a face with the same model as painted steel and the value comes out right and
 * the man comes out dead.
 *
 * <p>The second is that a face at this size cannot be drawn. A head is a couple of dozen pixels
 * across; an eye painted as two dark pixels is two dark pixels and reads as damage. An eye
 * <em>socket</em> is a hollow, a hollow holds a shadow, and a shadow is legible at any size. Every
 * feature here is geometry — brow ridge, carved sockets, a nose that is a ridge, a mouth that is
 * a groove — and the lighting finds all of them.
 *
 * <p>The sheet is laid out to show the faction difference doing real work: the Kreisau Circle
 * shows faces, and the Regime does not have one to show.
 */
public class SculptSoldierTest {

    static {
        Frame.useAwtBackend();
    }

    private static final int SIZE = 128;

    @Test
    public void bothSidesAtEveryFacing() throws IOException {
        PixelCanvas sheet = new PixelCanvas(SIZE * 8 + 20, SIZE * 2 + 30);
        sheet.fill(0xFF23251E);
        for (int facing = 0; facing < 8; facing++) {
            sheet.blit(partisan(facing, 0).light(Light.overcast()), 10 + facing * SIZE, 10);
            sheet.blit(soldat(facing, 0).light(Light.overcast()), 10 + facing * SIZE,
                    SIZE + 20);
        }
        save(sheet, "sculpt-soldiers.png");
        assertTrue(true);
    }

    /** The same two heads, large, because a face is not reviewable at twenty pixels. */
    @Test
    public void headsCloseUp() throws IOException {
        int big = 256;
        PixelCanvas sheet = new PixelCanvas(big * 3 + 40, big * 2 + 30);
        sheet.fill(0xFF23251E);

        // Straight at the camera, three-quarters, and turned away: the face should arrive and
        // leave rather than switch on.
        // Bare head on the top row so the face is actually visible, helmeted below it: the
        // first version drew only the helmeted pair and the helmet covered the thing under
        // review, which is a way to look at something without seeing it.
        float[] toward = {1f, 0.5f, 0f};
        for (int i = 0; i < 3; i++) {
            int skin = WolfPalette.shade(WolfPalette.FLESH, 2);
            Sculptor bare = new Sculptor(big, big);
            Anatomy.head(bare, big / 2f, big / 2f, big * 0.30f, 0f, skin, toward[i]);
            sheet.blit(bare.light(Light.overcast()), 10 + i * (big + 10), 10);

            Sculptor lidded = new Sculptor(big, big);
            Anatomy.head(lidded, big / 2f, big * 0.55f, big * 0.26f, 0f, skin, toward[i]);
            Anatomy.helmet(lidded, big / 2f, big * 0.46f, big * 0.28f, big * 0.20f,
                    WolfPalette.shade(WolfPalette.LEATHER, 2), toward[i]);
            sheet.blit(lidded.light(Light.overcast()), 10 + i * (big + 10), big + 20);
        }
        save(sheet, "sculpt-heads.png");
        assertTrue(true);
    }

    // --- the two figures --------------------------------------------------------------------

    /** How much of a facing points at the camera. South is toward the viewer. */
    private float toward(int facing) {
        float a = (float) (facing * Math.PI / 4.0);
        float dy = (float) Math.sin(a);
        return Math.max(0f, dy);
    }

    private Sculptor partisan(int facing, int frame) {
        Sculptor s = new Sculptor(SIZE, SIZE);
        int coat = WolfPalette.albedo(WolfPalette.OLIVE);
        int leather = WolfPalette.albedo(WolfPalette.LEATHER);
        int skin = WolfPalette.shade(WolfPalette.FLESH, 2);
        float t = toward(facing);

        figure(s, facing, frame, coat, leather, 3);
        Anatomy.head(s, SIZE * 0.5f, SIZE * 0.33f, SIZE * 0.060f, SIZE * 0.46f, skin, t);
        // A soft cap rather than a helmet: this is a farm rebellion, not an army.
        s.disc(SIZE * 0.5f, SIZE * 0.318f, SIZE * 0.066f, SIZE * 0.53f, SIZE * 0.032f,
                Form.DOME, leather, Sculptor.CLOTH);
        s.disc(SIZE * 0.5f, SIZE * 0.336f, SIZE * 0.076f, SIZE * 0.51f, SIZE * 0.012f,
                Form.BEVEL, leather, Sculptor.CLOTH);
        // The armband, which is all the uniform they have.
        s.box(SIZE * 0.325f, SIZE * 0.44f, SIZE * 0.05f, SIZE * 0.055f, SIZE * 0.01f,
                SIZE * 0.30f, SIZE * 0.012f, Form.BEVEL,
                WolfPalette.albedo(WolfPalette.BLOOD), Sculptor.CLOTH);
        rifle(s, facing);
        return s;
    }

    private Sculptor soldat(int facing, int frame) {
        Sculptor s = new Sculptor(SIZE, SIZE);
        int coat = WolfPalette.albedo(WolfPalette.NIGHT);
        int leather = WolfPalette.shade(WolfPalette.NIGHT, 1);
        float t = toward(facing);

        figure(s, facing, frame, coat, leather, 11);
        // No face at all: a mask, and two lenses where the eyes would be. The Regime's men are
        // not people you can see, and that is the point of them.
        Anatomy.head(s, SIZE * 0.5f, SIZE * 0.33f, SIZE * 0.056f, SIZE * 0.46f,
                WolfPalette.shade(WolfPalette.NIGHT, 1), 0f);
        if (t > 0.02f) {
            Anatomy.gasMask(s, SIZE * 0.5f, SIZE * 0.342f, SIZE * 0.054f, SIZE * 0.52f,
                    leather, WolfPalette.albedo(WolfPalette.RESONANCE),
                    WolfPalette.albedo(WolfPalette.GUNMETAL));
        }
        Anatomy.helmet(s, SIZE * 0.5f, SIZE * 0.318f, SIZE * 0.070f, SIZE * 0.58f, coat, t);
        rifle(s, facing);
        return s;
    }

    /** Everything below the neck, which both sides share. */
    private void figure(Sculptor s, int facing, int frame, int cloth, int leather, int seed) {
        float cx = SIZE * 0.5f;
        int step = frame == 1 ? 1 : 0;

        // No ground shadow here. Coverage is alpha in this engine, so an opaque black disc is
        // an opaque black disc - the first version put a solid blob under every man. A cast
        // shadow on the ground is the renderer's job anyway: it is not part of the figure, and
        // drawn there it can soften and scale with the camera for free.

        Anatomy.boot(s, cx - SIZE * 0.065f, SIZE * 0.80f + step * SIZE * 0.02f, SIZE * 0.075f,
                SIZE * 0.02f, leather);
        Anatomy.boot(s, cx + SIZE * 0.065f, SIZE * 0.80f - step * SIZE * 0.02f, SIZE * 0.075f,
                SIZE * 0.02f, leather);
        Anatomy.limb(s, cx - SIZE * 0.055f, SIZE * 0.63f, cx - SIZE * 0.065f, SIZE * 0.78f,
                SIZE * 0.038f, SIZE * 0.10f, cloth);
        Anatomy.limb(s, cx + SIZE * 0.055f, SIZE * 0.63f, cx + SIZE * 0.065f, SIZE * 0.78f,
                SIZE * 0.038f, SIZE * 0.10f, cloth);

        Anatomy.torso(s, cx, SIZE * 0.545f, SIZE * 0.175f, SIZE * 0.30f, SIZE * 0.20f, cloth, seed);
        Anatomy.webbing(s, cx, SIZE * 0.625f, SIZE * 0.175f, SIZE * 0.30f, leather, leather);
        Anatomy.shoulders(s, cx, SIZE * 0.435f, SIZE * 0.215f, SIZE * 0.32f, cloth);
        Anatomy.limb(s, cx - SIZE * 0.11f, SIZE * 0.47f, cx - SIZE * 0.085f, SIZE * 0.60f,
                SIZE * 0.032f, SIZE * 0.28f, cloth);
        Anatomy.limb(s, cx + SIZE * 0.11f, SIZE * 0.47f, cx + SIZE * 0.085f, SIZE * 0.60f,
                SIZE * 0.032f, SIZE * 0.28f, cloth);
    }

    /** A rifle held across the body, pointing where the man is looking. */
    private void rifle(Sculptor s, int facing) {
        float a = (float) (facing * Math.PI / 4.0);
        float dx = (float) Math.cos(a);
        float dy = (float) Math.sin(a);
        float cx = SIZE * 0.5f;
        float cy = SIZE * 0.56f;
        int wood = WolfPalette.albedo(WolfPalette.LEATHER);
        int steel = WolfPalette.albedo(WolfPalette.GUNMETAL);

        s.capsule(cx - dx * SIZE * 0.06f, cy - dy * SIZE * 0.06f, cx + dx * SIZE * 0.10f,
                cy + dy * SIZE * 0.10f, SIZE * 0.022f, SIZE * 0.36f, SIZE * 0.02f, Form.ROUND,
                wood, Sculptor.LEATHER);
        Machine.barrel(s, cx + dx * SIZE * 0.09f, cy + dy * SIZE * 0.09f,
                cx + dx * SIZE * 0.26f, cy + dy * SIZE * 0.26f, SIZE * 0.011f, SIZE * 0.37f,
                steel);
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
