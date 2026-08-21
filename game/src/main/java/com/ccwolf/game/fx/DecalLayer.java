package com.ccwolf.game.fx;

import com.ccwolf.gfx.Surface;
import com.ccwolf.gfx.Brush;
import com.ccwolf.game.art.WolfPalette;
import com.ccwolf.game.render.Camera;
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

    private final Brush paint = new Brush();
    private final Random random;
    private int cursor;

    public DecalLayer(long seed) {
        this.random = new Random(seed);
        paint.setAntiAlias(false);
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

    public void draw(Surface surface, Camera camera) {
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
            drawBlob(surface, screenX, screenY, radius, variant[i], scatterOf(kind[i]));

            if (kind[i] == Kind.CRATER) {
                paint.setColor(withAlpha(
                        WolfPalette.shade(WolfPalette.DIRT, 1), (int) (alpha * 0.8f)));
                drawBlob(surface, screenX, screenY - radius * 0.25f, radius * 0.55f,
                        variant[i] + 1, scatterOf(kind[i]));
            }
        }
    }

    /**
     * Stains are built from several small rectangles at varying sizes. Three big ones read as
     * a box; a spread of smaller ones reads as something that soaked into the ground.
     */
    private void drawBlob(Surface surface, float cx, float cy, float radius, int seed,
            float scatter) {
        // A small core with pieces placed around a jittered ring, so the pieces break the
        // outline rather than filling it in. Packing them inside the core is what made every
        // scorch mark draw as a rectangle with four square corners.
        float core = radius * (0.6f - scatter * 0.22f);
        surface.fillRect(cx - core, cy - core * 0.7f, cx + core, cy + core * 0.7f, paint);

        int pieces = 12;
        for (int i = 0; i < pieces; i++) {
            // Deterministic per-mark jitter: the same decal must not shimmer between frames.
            int noise = (seed * 37 + i * 131) & 0xFF;
            double angle = i * (Math.PI * 2 / pieces) + (noise / 255.0 - 0.5) * 0.7;
            float ring = radius * (0.45f + scatter * 0.55f) * (0.6f + (noise & 7) / 12f);
            float px = cx + (float) Math.cos(angle) * ring;
            float py = cy + (float) Math.sin(angle) * ring * 0.72f;
            float r = radius * (0.34f - scatter * 0.16f) * (0.6f + ((noise >> 3) & 3) * 0.3f);
            surface.fillRect(px - r, py - r * 0.72f, px + r, py + r * 0.72f, paint);
        }
    }

    /** How far a mark's pieces fly apart: 1 is a splatter, 0 is a solid patch. */
    private static float scatterOf(Kind what) {
        switch (what) {
            case BLOOD:
                return 1.0f;
            case OIL:
                return 0.45f;
            case BURN:
                return 0.6f;
            case CRATER:
                return 0.3f;
            case SCORCH:
            default:
                return 0.5f;
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
