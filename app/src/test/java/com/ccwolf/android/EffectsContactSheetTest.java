package com.ccwolf.android;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import com.ccwolf.android.fx.DecalLayer;
import com.ccwolf.android.fx.FxDirector;
import com.ccwolf.android.fx.ParticleSystem;
import com.ccwolf.android.render.Camera;
import com.ccwolf.android.render.Palette;
import com.ccwolf.core.combat.WeaponClass;
import com.ccwolf.core.event.GameEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Lays every combat effect out side by side so they can be compared rather than glimpsed.
 *
 * <p>Effects are procedural — they are particles drawn straight to the canvas, not baked
 * sprites — so unlike the sprite sheets these cells cannot be read out of the atlas. Each one
 * stages its effect into a fresh director, runs it forward to a chosen age, and renders it over
 * a strip of grass at the same tile scale the game uses. A tracer that vanishes against the
 * terrain or a blood spray that reads as red confetti shows up here and nowhere else.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EffectsContactSheetTest {

    private static final int CELL = 150;
    private static final int PAD = 6;
    private static final int LABEL_H = 15;
    private static final int GUTTER = 132;
    private static final int BACKDROP = 0xFF1B1D18;

    /** One simulation frame at the rate the renderer actually runs. */
    private static final float DT = 1f / 60f;

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blit = new Paint();

    public EffectsContactSheetTest() {
        text.setColor(0xFFD8D4BC);
        text.setTextSize(11f);
        blit.setFilterBitmap(false);
        blit.setAntiAlias(false);
    }

    /**
     * Every weapon class across its whole life: in flight, then landing on each of the three
     * things it can hit. Read down a column to compare weapons; read across to check that an
     * impact tells you what was hit.
     */
    @Test
    public void everyWeaponClassFromMuzzleToImpact() throws IOException {
        WeaponClass[] classes = WeaponClass.values();
        String[] columns = {"in flight", "vs infantry", "vs vehicle", "vs structure"};
        GameEvent.TargetKind[] targets = {
            GameEvent.TargetKind.NONE,
            GameEvent.TargetKind.INFANTRY,
            GameEvent.TargetKind.VEHICLE,
            GameEvent.TargetKind.STRUCTURE,
        };

        Sheet sheet = new Sheet(columns.length, classes.length, columns);
        for (int row = 0; row < classes.length; row++) {
            sheet.label(row, classes[row].name());
            for (int col = 0; col < columns.length; col++) {
                final WeaponClass weapon = classes[row];
                final GameEvent.TargetKind target = targets[col];
                // Column 0 catches the round mid-flight over a long shot. The impact columns
                // fire almost point blank and wait for the round to actually land before
                // sampling: rockets and grenades are slow, and a fixed frame count caught
                // them still in the air with nothing to show.
                final boolean inFlight = col == 0;
                final float range = inFlight ? 1.6f : 0.35f;
                sheet.cell(row, col, new Staging() {
                    @Override
                    public void stage(FxDirector fx, float x, float y) {
                        // Fired from the left edge of the cell into its centre, so the
                        // impact lands where the eye is and the flight is still visible.
                        fx.consume(Collections.singletonList(GameEvent.shot(
                                0, 1, x - range, y, x, y, 20, weapon, target)), 0);
                        if (inFlight) {
                            run(fx, 4);
                        } else {
                            waitForImpact(fx);
                            // Far enough past the impact to see the effect open out, close
                            // enough that a spark burst has not already died.
                            run(fx, 10);
                        }
                    }
                });
            }
        }
        // Two of the classes have nothing to show in flight: the flamethrower has no round
        // and melee has no projectile at all.
        assertTrue("a weapon sheet with no effects on it is not evidence of anything: "
                        + sheet.litCells() + " of " + classes.length * columns.length,
                sheet.litCells() >= classes.length * columns.length - 2);
        save(sheet.bitmap, "sheet-effects-weapons.png");
    }

    /**
     * Death, staged. Each row is one kind of thing dying, sampled at four ages, so the
     * progression — plume, then flame, then what is left on the ground — can be checked
     * rather than assumed.
     */
    @Test
    public void deathsUnfoldOverTime() throws IOException {
        GameEvent.TargetKind[] kinds = {
            GameEvent.TargetKind.INFANTRY,
            GameEvent.TargetKind.VEHICLE,
            GameEvent.TargetKind.STRUCTURE,
        };
        final int[] ages = {2, 12, 40, 110};
        String[] columns = new String[ages.length];
        for (int i = 0; i < ages.length; i++) {
            columns[i] = ages[i] + " frames";
        }

        Sheet sheet = new Sheet(ages.length, kinds.length, columns);
        for (int row = 0; row < kinds.length; row++) {
            sheet.label(row, kinds[row].name() + " death");
            for (int col = 0; col < ages.length; col++) {
                final GameEvent.TargetKind kind = kinds[row];
                final int frames = ages[col];
                sheet.cell(row, col, new Staging() {
                    @Override
                    public void stage(FxDirector fx, float x, float y) {
                        fx.consume(Collections.singletonList(
                                GameEvent.destroyed(0, 1, x, y, kind)), 0);
                        run(fx, frames);
                    }
                });
            }
        }
        // The last column is deliberately late: by then only the ground layer should remain.
        save(sheet.bitmap, "sheet-effects-deaths.png");
    }

    /**
     * The ground layer on its own, fresh and faded, plus the two effects that have no
     * projectile behind them.
     */
    @Test
    public void groundMarksAndTheEffectsWithoutARound() throws IOException {
        DecalLayer.Kind[] decals = DecalLayer.Kind.values();
        String[] columns = {"fresh", "half faded", "nearly gone"};
        final float[] aged = {0f, 0.5f, 0.85f};

        Sheet sheet = new Sheet(columns.length + 1, decals.length + 2, columns);
        for (int row = 0; row < decals.length; row++) {
            sheet.label(row, decals[row].name());
            for (int col = 0; col < columns.length; col++) {
                final DecalLayer.Kind kind = decals[row];
                final float fraction = aged[col];
                sheet.cell(row, col, new Staging() {
                    @Override
                    public void stage(FxDirector fx, float x, float y) {
                        float life = 20f;
                        fx.decals().add(kind, x, y, 0.9f, life);
                        // Decals fade on wall-clock seconds, so age them by running the layer.
                        run(fx, (int) (life * fraction * 60f));
                    }
                });
            }
        }

        sheet.label(decals.length, "SABOTAGE arc");
        sheet.cell(decals.length, 0, new Staging() {
            @Override
            public void stage(FxDirector fx, float x, float y) {
                fx.consume(Collections.singletonList(
                        GameEvent.at(GameEvent.Type.SABOTAGED, 0, 1, x, y)), 0);
                run(fx, 6);
            }
        });
        sheet.label(decals.length + 1, "HIJACK");
        sheet.cell(decals.length + 1, 0, new Staging() {
            @Override
            public void stage(FxDirector fx, float x, float y) {
                fx.consume(Collections.singletonList(
                        GameEvent.at(GameEvent.Type.HIJACKED, 0, 1, x, y)), 0);
                run(fx, 6);
            }
        });

        save(sheet.bitmap, "sheet-effects-ground.png");
    }

    /** Every particle kind on its own, so a bad palette ramp has nowhere to hide. */
    @Test
    public void everyParticleKindAgainstGrass() throws IOException {
        ParticleSystem.Kind[] kinds = ParticleSystem.Kind.values();
        final int[] ages = {2, 10, 30};
        String[] columns = {"just spawned", "mid-life", "fading"};

        Sheet sheet = new Sheet(ages.length, kinds.length, columns);
        for (int row = 0; row < kinds.length; row++) {
            sheet.label(row, kinds[row].name());
            for (int col = 0; col < ages.length; col++) {
                final ParticleSystem.Kind kind = kinds[row];
                final int frames = ages[col];
                sheet.cell(row, col, new Staging() {
                    @Override
                    public void stage(FxDirector fx, float x, float y) {
                        fx.particles().puff(kind, x, y, 26, 2.4f, 1.2f, 0.08f);
                        run(fx, frames);
                    }
                });
            }
        }
        assertTrue("every particle kind should draw something when freshly spawned",
                sheet.litCells() >= kinds.length);
        save(sheet.bitmap, "sheet-effects-particles.png");
    }

    // --- staging --------------------------------------------------------------------------

    private interface Staging {
        void stage(FxDirector fx, float x, float y);
    }

    /** Runs the layer on until every round in the air has landed. */
    private static void waitForImpact(FxDirector fx) {
        for (int i = 0; i < 240 && fx.projectiles().liveCount() > 0; i++) {
            fx.update(DT);
        }
    }

    private static void run(FxDirector fx, int frames) {
        for (int i = 0; i < frames; i++) {
            fx.update(DT);
        }
    }

    /**
     * A labelled grid of cells, each rendered by its own director over its own patch of grass.
     *
     * <p>Cells are rendered in isolation rather than into one shared world so that a long-lived
     * effect in one cell cannot bleed into its neighbour and be mistaken for that one's output.
     */
    private final class Sheet {

        final Bitmap bitmap;
        private final Canvas canvas;
        private final int columns;

        Sheet(int columns, int rows, String[] headers) {
            this.columns = columns;
            bitmap = Bitmap.createBitmap(GUTTER + columns * (CELL + PAD) + PAD,
                    LABEL_H + rows * (CELL + PAD) + PAD, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bitmap);
            canvas.drawColor(BACKDROP);
            for (int i = 0; i < headers.length && i < columns; i++) {
                canvas.drawText(headers[i], GUTTER + i * (CELL + PAD), LABEL_H - 4, text);
            }
        }

        void label(int row, String name) {
            canvas.drawText(name, 4, LABEL_H + row * (CELL + PAD) + CELL / 2, text);
        }

        void cell(int row, int col, Staging staging) {
            FxDirector fx = new FxDirector(1234L + row * 31L + col);
            Camera camera = new Camera();
            camera.setViewport(0, 0, CELL, CELL);

            Bitmap cell = Bitmap.createBitmap(CELL, CELL, Bitmap.Config.ARGB_8888);
            Canvas cellCanvas = new Canvas(cell);

            // Grass, tiled by hand: the terrain renderer needs a whole world, and these cells
            // exist to judge the effects, not the ground under them.
            float px = camera.tilePx();
            Paint ground = new Paint();
            for (int ty = 0; ty <= CELL / px + 1; ty++) {
                for (int tx = 0; tx <= CELL / px + 1; tx++) {
                    ground.setColor((tx + ty) % 2 == 0 ? Palette.GRASS : Palette.GRASS_ALT);
                    cellCanvas.drawRect(tx * px, ty * px, (tx + 1) * px, (ty + 1) * px, ground);
                }
            }

            // Stage at whatever the camera considers its centre. Asking for a tile of our
            // own choosing does not work: with no map set the camera clamps the view to a
            // 1x1 world, and the effect lands off-screen.
            float cx = camera.centerX();
            float cy = camera.centerY();
            staging.stage(fx, cx, cy);

            fx.drawDecals(cellCanvas, camera);
            fx.drawOverlay(cellCanvas, camera);

            int x = GUTTER + col * (CELL + PAD);
            int y = LABEL_H + row * (CELL + PAD);
            canvas.drawBitmap(cell, null, new Rect(x, y, x + CELL, y + CELL), blit);
            lit.add(Boolean.valueOf(differsFromGrass(cell)));
        }

        private final List<Boolean> lit = new ArrayList<Boolean>();

        /** How many cells actually drew something over the grass. */
        int litCells() {
            int count = 0;
            for (int i = 0; i < lit.size(); i++) {
                if (lit.get(i).booleanValue()) {
                    count++;
                }
            }
            return count;
        }

        private boolean differsFromGrass(Bitmap cell) {
            int off = 0;
            for (int y = 0; y < CELL; y++) {
                for (int x = 0; x < CELL; x++) {
                    int c = cell.getPixel(x, y);
                    if (c != Palette.GRASS && c != Palette.GRASS_ALT
                            && Color.alpha(c) > 0) {
                        off++;
                    }
                }
            }
            return off > 12;
        }
    }

    private void save(Bitmap bitmap, String name) throws IOException {
        File dir = new File("build/test-frames");
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        FileOutputStream out = new FileOutputStream(new File(dir, name));
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            out.close();
        }
    }
}
