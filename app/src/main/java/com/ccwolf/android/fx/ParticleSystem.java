package com.ccwolf.android.fx;

import android.graphics.Canvas;
import android.graphics.Paint;
import com.ccwolf.android.art.WolfPalette;
import com.ccwolf.android.render.Camera;
import java.util.Random;

/**
 * A fixed-size pool of particles: sparks, smoke, blood, debris and flame.
 *
 * <p>Pooled and hard-capped on purpose. A battle involving fifty units firing produces
 * thousands of effect spawns a second, and a system that allocates per particle would spend
 * the frame budget in the garbage collector on exactly the frames that matter most.
 *
 * <p>Particles are drawn as flat rectangles from the game's own palette rather than as textured
 * sprites, so the effects sit inside the pixel art rather than on top of it.
 */
public final class ParticleSystem {

    /** Live particles allowed at once. Spawns past this are dropped, oldest-first. */
    public static final int CAPACITY = 700;

    /** Fraction of a particle's life after which it starts fading out. */
    private static final float TAIL_FADE = 0.65f;

    /** How a particle behaves and what it is drawn as. */
    public enum Kind {
        /** Bright, fast, short-lived: bullet strikes and metal-on-metal. */
        SPARK,
        /** Rises, spreads and fades. */
        SMOKE,
        /** Falls under gravity, then stains the ground. */
        BLOOD,
        /** Tumbles under gravity: hull plate, masonry, gibs. */
        DEBRIS,
        /** Flickers hot to cool and dies quickly. */
        FLAME,
        /** Dust and grit kicked off the ground. */
        DUST,
        /** Occult discharge: green, erratic. */
        PLASMA
    }

    private final float[] x = new float[CAPACITY];
    private final float[] y = new float[CAPACITY];
    private final float[] vx = new float[CAPACITY];
    private final float[] vy = new float[CAPACITY];
    private final float[] life = new float[CAPACITY];
    private final float[] maxLife = new float[CAPACITY];
    private final float[] size = new float[CAPACITY];
    private final Kind[] kind = new Kind[CAPACITY];
    private final boolean[] alive = new boolean[CAPACITY];

    /**
     * Per-particle offset into the palette ramp.
     *
     * <p>Colour is otherwise a function of age alone, so every particle in one puff picks the
     * same shade on the same frame and an explosion draws as a heap of identical yellow
     * bricks. This spreads a burst across neighbouring shades so it has a hot core and cooler
     * edges without needing per-particle colour storage.
     */
    private final int[] heat = new int[CAPACITY];

    private final Paint paint = new Paint();
    private final Random random;
    private int cursor;
    private int liveCount;

    public ParticleSystem(long seed) {
        this.random = new Random(seed);
        paint.setAntiAlias(false);
        paint.setStyle(Paint.Style.FILL);
    }

    public int liveCount() {
        return liveCount;
    }

    public void clear() {
        for (int i = 0; i < CAPACITY; i++) {
            alive[i] = false;
        }
        liveCount = 0;
    }

    /**
     * Spawns one particle. Velocities are in tiles per second, life in seconds.
     *
     * <p>When the pool is full the oldest slot is reused rather than the spawn being dropped:
     * the newest effect is the one the player is looking at.
     */
    public void spawn(Kind what, float px, float py, float velX, float velY, float lifeSeconds,
                      float sizeTiles) {
        int slot = -1;
        for (int i = 0; i < CAPACITY; i++) {
            int candidate = (cursor + i) % CAPACITY;
            if (!alive[candidate]) {
                slot = candidate;
                break;
            }
        }
        if (slot < 0) {
            slot = cursor % CAPACITY; // Pool full: overwrite the oldest.
        } else {
            liveCount++;
        }
        cursor = (slot + 1) % CAPACITY;

        x[slot] = px;
        y[slot] = py;
        vx[slot] = velX;
        vy[slot] = velY;
        life[slot] = lifeSeconds;
        maxLife[slot] = lifeSeconds;
        heat[slot] = random.nextInt(3) - 1;
        size[slot] = sizeTiles;
        kind[slot] = what;
        alive[slot] = true;
    }

    /** Spawns {@code count} particles in a cone around a direction, with spread and speed. */
    public void burst(Kind what, float px, float py, float dirX, float dirY, int count,
                      float speed, float spread, float lifeSeconds, float sizeTiles) {
        float baseAngle = (float) Math.atan2(dirY, dirX);
        for (int i = 0; i < count; i++) {
            float angle = baseAngle + (random.nextFloat() - 0.5f) * spread;
            float magnitude = speed * (0.4f + random.nextFloat());
            spawn(what, px, py,
                    (float) Math.cos(angle) * magnitude,
                    (float) Math.sin(angle) * magnitude,
                    lifeSeconds * (0.6f + random.nextFloat() * 0.7f), sizeTiles);
        }
    }

    /** Spawns particles evenly in all directions. */
    public void puff(Kind what, float px, float py, int count, float speed, float lifeSeconds,
                     float sizeTiles) {
        for (int i = 0; i < count; i++) {
            double angle = random.nextFloat() * Math.PI * 2;
            float magnitude = speed * (0.3f + random.nextFloat());
            spawn(what, px, py, (float) Math.cos(angle) * magnitude,
                    (float) Math.sin(angle) * magnitude,
                    lifeSeconds * (0.6f + random.nextFloat() * 0.8f), sizeTiles);
        }
    }

    public void update(float dt) {
        for (int i = 0; i < CAPACITY; i++) {
            if (!alive[i]) {
                continue;
            }
            life[i] -= dt;
            if (life[i] <= 0f) {
                alive[i] = false;
                liveCount--;
                continue;
            }

            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;

            switch (kind[i]) {
                case SMOKE:
                    // Rises, slows and spreads.
                    vy[i] -= 0.4f * dt;
                    vx[i] *= 0.94f;
                    size[i] += 0.35f * dt;
                    break;
                case BLOOD:
                case DEBRIS:
                    // Thrown, then pulled down and dragged to a stop.
                    vy[i] += 6f * dt;
                    vx[i] *= 0.92f;
                    vy[i] *= 0.98f;
                    break;
                case FLAME:
                    vy[i] -= 0.9f * dt;
                    vx[i] *= 0.88f;
                    break;
                case SPARK:
                    vx[i] *= 0.86f;
                    vy[i] *= 0.86f;
                    break;
                case DUST:
                    vx[i] *= 0.90f;
                    vy[i] *= 0.90f;
                    size[i] += 0.2f * dt;
                    break;
                case PLASMA:
                default:
                    vx[i] *= 0.90f;
                    vy[i] *= 0.90f;
                    break;
            }
        }
    }

    public void draw(Canvas canvas, Camera camera) {
        // Two passes, so the fire in an explosion is not buried under its own smoke. Spawn
        // order alone put the flame down first and the smoke plume straight over the top of
        // it, which turned every detonation into a grey cauliflower.
        drawPass(canvas, camera, true);
        drawPass(canvas, camera, false);
    }

    private void drawPass(Canvas canvas, Camera camera, boolean background) {
        float px = camera.tilePx();
        for (int i = 0; i < CAPACITY; i++) {
            if (!alive[i] || isBackground(kind[i]) != background) {
                continue;
            }
            float age = 1f - life[i] / maxLife[i];
            // Everything thins out as it dies. Without this a dust cloud or a flame pops out
            // of existence at full opacity, which reads as the effect being cut off rather
            // than burning down.
            paint.setColor(fade(colourFor(kind[i], age, heat[i]), age));

            float screenX = camera.screenX(x[i]);
            float screenY = camera.screenY(y[i]);
            // Clamped to a couple of pixels at the small end so a spark stays a spark rather
            // than disappearing, and never allowed to become a brick at the large end.
            float half = Math.max(1f, Math.min(px * 0.22f, size[i] * px * 0.5f));
            canvas.drawRect(screenX - half, screenY - half, screenX + half, screenY + half,
                    paint);
        }
    }

    /**
     * Particles walk down a palette ramp as they age rather than fading through alpha, which
     * is what keeps them looking hand-drawn instead of like a modern particle system.
     */
    /** Scales alpha over the last third of a particle's life. */
    /** Nudges a ramp index by a particle's heat offset. WolfPalette.shade clamps the rest. */
    private static int step(int shade, int heat) {
        return shade + heat;
    }

    /** Smoke, dust and tumbling debris sit behind the hot particles. */
    private static boolean isBackground(Kind what) {
        return what == Kind.SMOKE || what == Kind.DUST || what == Kind.DEBRIS;
    }

    private static int fade(int colour, float age) {
        if (age < TAIL_FADE) {
            return colour;
        }
        int alpha = (colour >>> 24) == 0 ? 255 : (colour >>> 24);
        alpha = (int) (alpha * (1f - (age - TAIL_FADE) / (1f - TAIL_FADE)));
        return (Math.max(0, alpha) << 24) | (colour & 0x00FFFFFF);
    }

    private static int colourFor(Kind what, float age, int heat) {
        switch (what) {
            case SPARK:
                return WolfPalette.shade(WolfPalette.FIRE, step(age < 0.4f ? 0 : (age < 0.75f ? 1 : 2), heat));
            case SMOKE: {
                int shade = step(age < 0.3f ? 1 : (age < 0.6f ? 2 : 3), heat);
                int colour = WolfPalette.shade(WolfPalette.SMOKE, shade);
                // Smoke is the one thing that fades out, or the map fills up with grey blocks.
                int alpha = (int) (200 * (1f - age));
                return (alpha << 24) | (colour & 0x00FFFFFF);
            }
            case BLOOD:
                // Dark and venous. Drawn from the hot end of the ramp it read as bright red
                // confetti rather than as blood, so it only ever varies darker: letting the
                // heat offset run the other way puts those bright specks straight back.
                return WolfPalette.shade(WolfPalette.BLOOD,
                        step(age < 0.4f ? 2 : 4, Math.max(0, heat)));
            case DEBRIS:
                return WolfPalette.shade(WolfPalette.GUNMETAL, step(age < 0.5f ? 2 : 3, heat));
            case FLAME:
                return WolfPalette.shade(WolfPalette.FIRE, step(age < 0.25f ? 0
                        : (age < 0.55f ? 1 : (age < 0.8f ? 2 : 3)), heat));
            case DUST:
                return WolfPalette.shade(WolfPalette.DIRT, step(age < 0.5f ? 1 : 2, heat));
            case PLASMA:
            default:
                return WolfPalette.shade(WolfPalette.OCCULT, step(age < 0.4f ? 0 : (age < 0.7f ? 1 : 3), heat));
        }
    }
}
