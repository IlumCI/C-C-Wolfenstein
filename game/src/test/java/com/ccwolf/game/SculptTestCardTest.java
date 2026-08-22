package com.ccwolf.game;

import com.ccwolf.game.art.Form;
import com.ccwolf.game.art.Light;
import com.ccwolf.game.art.PixelCanvas;
import com.ccwolf.game.art.Sculptor;
import com.ccwolf.game.art.WolfPalette;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * The lighting model, on shapes rather than on art.
 *
 * <p>This is the commit that decides whether the sculpting engine is worth building the rest of
 * the game on, so it is deliberately a test card and not a sprite: every form at three sizes,
 * every material, the depth test, the step register, the noise, the glow. If these do not look
 * obviously better than hand-placed highlights, nothing further should be drawn on this engine.
 *
 * <p>Two backdrops, because one always hides the failure. A dark ground flatters a bright rim and
 * conceals a muddy edge; a light ground does the opposite.
 */
public class SculptTestCardTest {

    static {
        Frame.useAwtBackend();
    }

    private static final int CELL = 160;
    private static final int PAD = 10;
    private static final int DARK = 0xFF23251E;
    private static final int PALE = 0xFFB9B5A6;

    @Test
    public void everyFormAndMaterialLit() throws IOException {
        int cols = 6;
        int rows = 4;
        PixelCanvas sheet = new PixelCanvas(cols * (CELL + PAD) + PAD,
                rows * (CELL + PAD) * 2 + PAD * 3);
        sheet.fill(DARK);
        // Bottom half pale, so every shape is judged against both.
        sheet.rect(0, rows * (CELL + PAD) + PAD, sheet.width(), sheet.height(), PALE);

        for (int half = 0; half < 2; half++) {
            int originY = PAD + half * (rows * (CELL + PAD) + PAD);
            int cell = 0;
            for (PixelCanvas tile : testCells()) {
                int col = cell % cols;
                int row = cell / cols;
                sheet.blit(tile, PAD + col * (CELL + PAD), originY + row * (CELL + PAD));
                cell++;
            }
        }

        save(sheet, "sculpt-testcard.png");
        assertTrue("the card should not be a flat colour", distinctColours(sheet) > 200);
    }

    /** One cell per thing worth judging, in the order a reviewer should read them. */
    private java.util.List<PixelCanvas> testCells() {
        java.util.List<PixelCanvas> cells = new java.util.ArrayList<PixelCanvas>();
        Light light = Light.overcast();
        int olive = WolfPalette.shade(WolfPalette.OLIVE, 2);
        int steel = WolfPalette.shade(WolfPalette.GUNMETAL, 2);
        int dirt = WolfPalette.shade(WolfPalette.DIRT, 2);
        int concrete = WolfPalette.shade(WolfPalette.CONCRETE, 2);

        // --- the forms, one each, same colour and material so only the shape differs ---
        for (Form form : new Form[] {Form.FLAT, Form.DOME, Form.RIDGE, Form.BEVEL}) {
            Sculptor s = new Sculptor(CELL, CELL);
            s.disc(CELL / 2f, CELL / 2f, 52f, 0f, 26f, form, olive, Sculptor.CLOTH);
            cells.add(s.light(light));
        }

        // A cut, which needs something to cut into.
        Sculptor carved = new Sculptor(CELL, CELL);
        carved.box(10, 10, CELL - 20, CELL - 20, 6f, 0f, 8f, Form.BEVEL, dirt, Sculptor.CLOTH);
        carved.capsule(20, CELL / 2f, CELL - 20, CELL / 2f, 26f, 0f, 22f, Form.CONCAVE,
                dirt, Sculptor.CLOTH);
        cells.add(carved.light(light));

        // A cylinder, which is the primitive everything else is built out of.
        Sculptor tube = new Sculptor(CELL, CELL);
        tube.capsule(26, CELL / 2f, CELL - 26, CELL / 2f, 30f, 0f, 30f, Form.ROUND,
                steel, Sculptor.STEEL);
        cells.add(tube.light(light));

        // --- scale: the same ball at three sizes, because small is where shading fails ---
        for (float radius : new float[] {56f, 30f, 14f}) {
            Sculptor s = new Sculptor(CELL, CELL);
            s.disc(CELL / 2f, CELL / 2f, radius, 0f, radius * 0.6f, Form.DOME, olive,
                    Sculptor.CLOTH);
            cells.add(s.light(light));
        }

        // --- material: the same dome from cloth to polished steel ---
        for (float material : new float[] {Sculptor.CLOTH, Sculptor.LEATHER, Sculptor.PAINT,
                Sculptor.STEEL}) {
            Sculptor s = new Sculptor(CELL, CELL);
            s.disc(CELL / 2f, CELL / 2f, 50f, 0f, 34f, Form.DOME, steel, material);
            cells.add(s.light(light));
        }

        // --- the depth test and the step register: a bar lying across a plate ---
        Sculptor stacked = new Sculptor(CELL, CELL);
        stacked.box(18, 18, CELL - 36, CELL - 36, 8f, 0f, 10f, Form.BEVEL, olive,
                Sculptor.PAINT);
        stacked.capsule(28, 54, CELL - 28, 104, 13f, 14f, 13f, Form.ROUND, steel,
                Sculptor.STEEL);
        cells.add(stacked.light(light));

        // The same thing with the bar on the same layer, so a reviewer can see what the depth
        // test is buying: without it the bar is a welt on the plate rather than a rod on it.
        Sculptor melted = new Sculptor(CELL, CELL);
        melted.box(18, 18, CELL - 36, CELL - 36, 8f, 0f, 10f, Form.BEVEL, olive, Sculptor.PAINT);
        melted.capsule(28, 54, CELL - 28, 104, 13f, 0f, 13f, Form.ROUND, steel, Sculptor.STEEL);
        cells.add(melted.light(light));

        // --- surface noise: the mechanism for texture, on concrete ---
        Sculptor smooth = new Sculptor(CELL, CELL);
        smooth.box(14, 14, CELL - 28, CELL - 28, 10f, 0f, 16f, Form.BEVEL, concrete,
                Sculptor.PAINT);
        cells.add(smooth.light(light));

        Sculptor rough = new Sculptor(CELL, CELL);
        rough.box(14, 14, CELL - 28, CELL - 28, 10f, 0f, 16f, Form.BEVEL, concrete,
                Sculptor.PAINT);
        rough.roughen(14, 14, CELL - 28, CELL - 28, 0.30f, 5f, 11);
        cells.add(rough.light(light));

        // --- a polygon, and emissive ---
        Sculptor plate = new Sculptor(CELL, CELL);
        plate.polygon(new float[] {24, 130, 60, 26, 132, 44, 116, 134}, 0f, 18f, Form.BEVEL,
                steel, Sculptor.STEEL);
        cells.add(plate.light(light));

        Sculptor lamp = new Sculptor(CELL, CELL);
        lamp.box(20, 20, CELL - 40, CELL - 40, 10f, 0f, 12f, Form.BEVEL,
                WolfPalette.shade(WolfPalette.GUNMETAL, 3), Sculptor.STEEL);
        lamp.disc(CELL / 2f, CELL / 2f, 26f, 12f, 10f, Form.DOME,
                WolfPalette.shade(WolfPalette.RESONANCE, 1), Sculptor.STEEL);
        lamp.glow(CELL / 2f, CELL / 2f, 30f, 1.1f);
        cells.add(lamp.light(light));

        // --- a small assembly, to see whether the parts read as parts ---
        Sculptor rig = new Sculptor(CELL, CELL);
        rig.box(30, 46, 100, 62, 10f, 0f, 14f, Form.BEVEL, olive, Sculptor.PAINT);
        rig.roughen(30, 46, 100, 62, 0.18f, 5f, 7);
        rig.disc(74, 74, 26f, 14f, 20f, Form.DOME, olive, Sculptor.PAINT);
        rig.capsule(92, 74, 146, 74, 7f, 30f, 7f, Form.ROUND, steel, Sculptor.STEEL);
        rig.disc(62, 62, 5f, 34f, 4f, Form.DOME, steel, Sculptor.STEEL);
        for (int i = 0; i < 5; i++) {
            rig.disc(40 + i * 12, 54, 3f, 14f, 2.5f, Form.DOME, steel, Sculptor.STEEL);
        }
        cells.add(rig.light(light));

        return cells;
    }

    private int distinctColours(PixelCanvas c) {
        java.util.HashSet<Integer> seen = new java.util.HashSet<Integer>();
        for (int y = 0; y < c.height(); y += 3) {
            for (int x = 0; x < c.width(); x += 3) {
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
