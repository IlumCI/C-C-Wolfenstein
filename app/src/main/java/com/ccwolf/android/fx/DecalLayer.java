package com.ccwolf.android.fx;

import android.graphics.Canvas;
import android.graphics.Paint;
import com.ccwolf.android.art.WolfPalette;
import com.ccwolf.android.render.Camera;
import java.util.Random;

/**
 * Marks left on the ground: scorch, craters, blood, oil and burnt patches.
 *
 * <p>Drawn immediately after the terrain and before anything standing on it, so a battlefield
 * accumulates a history. Capped and reused oldest-first — a long match must not grow without
 * bound, and the marks under the current fighting matter more than the ones from ten minutes
 * ago.
 */
public final class DecalLayer {

    public static final int CAPACITY = 180;

    public enum Kind { SCORCH, CRATER, BLOOD, OIL, BURN }

    private final float[] x = new float[CAPACITY];
    private final float[] y = new float[CAPACITY];
    private final float[] size = new float[CAPACITY];
    private final float[] age = new float[CAPACITY];
    private final float[] maxAge = new float[CAPACITY];
    private final int[] variant = new int[CAPACITY];
    private final Kind[] kind = new Kind[CAPACITY];
    private final boolean[] alive = new boolean[CAPACITY];

    private final Paint paint = new Paint();
    private final Random random;
    private int cursor;

    public DecalLayer(long seed) {
        this.random = new Random(seed);
        paint.setAntiAlias(false);
        paint.setStyle(Paint.Style.FILL);
    }

    public void clear() {
        for (int i = 0; i < CAPACITY; i++) {
            alive[i] = false;
        }
    }

    public int liveCount() {
        int n = 0;
        for (int i = 0; i < CAPACITY; i++) {
            if (alive[i]) {
                n++;
            }
        }
        return n;
    }

    /**
     * Stamps a mark on the ground.
     *
     * @param sizeTiles roughly how wide the mark is
     * @param lifeSeconds how long before it has faded away entirely
     */
    public void add(Kind what, float px, float py, float sizeTiles, float lifeSeconds) {
        int slot = -1;
        for (int i = 0; i < CAPACITY; i++) {
            int candidate = (cursor + i) % CAPACITY;
            if (!alive[candidate]) {
                slot = candidate;
                break;
            }
        }
        if (slot < 0) {
            slot = cursor % CAPACITY;
        }
        cursor = (slot + 1) % CAPACITY;

        x[slot] = px;
        y[slot] = py;
        size[slot] = sizeTiles;
        age[slot] = 0f;
        maxAge[slot] = lifeSeconds;
        variant[slot] = random.nextInt(4);
        kind[slot] = what;
        alive[slot] = true;
    }

    public void update(float dt) {
        for (int i = 0; i < CAPACITY; i++) {
            if (!alive[i]) {
                continue;
            }
            age[i] += dt;
            if (age[i] >= maxAge[i]) {
                alive[i] = false;
            }
        }
    }

    public void draw(Canvas canvas, Camera camera) {
        float tile = camera.tilePx();
        for (int i = 0; i < CAPACITY; i++) {
            if (!alive[i]) {
                continue;
            }
            float fade = 1f - age[i] / maxAge[i];
            float screenX = camera.screenX(x[i]);
            float screenY = camera.screenY(y[i]);
            float radius = size[i] * tile * 0.5f;

            // Marks fade out over their last third rather than vanishing.
            int alpha = (int) (255 * Math.min(1f, fade * 3f));
            paint.setColor(withAlpha(baseColour(kind[i], variant[i]), alpha));

            // Blobby rather than round: three overlapping rectangles read as a stain at this
            // scale, where a clean ellipse reads as a decal from a different game.
            drawBlob(canvas, screenX, screenY, radius, variant[i]);

            if (kind[i] == Kind.CRATER) {
                paint.setColor(withAlpha(
                        WolfPalette.shade(WolfPalette.DIRT, 1), (int) (alpha * 0.8f)));
                drawBlob(canvas, screenX, screenY - radius * 0.25f, radius * 0.55f,
                        variant[i] + 1);
            }
        }
    }

    /**
     * Stains are built from several small rectangles at varying sizes. Three big ones read as
     * a box; a spread of smaller ones reads as something that soaked into the ground.
     */
    private void drawBlob(Canvas canvas, float cx, float cy, float radius, int seed) {
        for (int i = 0; i < 7; i++) {
            float offsetX = ((seed * 3 + i * 7) % 7 - 3) * radius * 0.26f;
            float offsetY = ((seed * 5 + i * 3) % 7 - 3) * radius * 0.22f;
            float r = radius * (i == 0 ? 0.85f : (0.30f + ((seed + i) % 4) * 0.12f));
            canvas.drawRect(cx + offsetX - r, cy + offsetY - r * 0.66f,
                    cx + offsetX + r, cy + offsetY + r * 0.66f, paint);
        }
    }

    private static int baseColour(Kind what, int variant) {
        switch (what) {
            case BLOOD:
                return WolfPalette.shade(WolfPalette.BLOOD, 3 + (variant & 1));
            case OIL:
                return WolfPalette.shade(WolfPalette.NIGHT, 3);
            case BURN:
                return WolfPalette.shade(WolfPalette.SMOKE, 3);
            case CRATER:
                return 0xFF1B1712;
            case SCORCH:
            default:
                return WolfPalette.shade(WolfPalette.SMOKE, 4);
        }
    }

    private static int withAlpha(int colour, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (colour & 0x00FFFFFF);
    }
}
