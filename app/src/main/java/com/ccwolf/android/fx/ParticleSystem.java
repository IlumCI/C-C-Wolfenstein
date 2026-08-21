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
        float px = camera.tilePx();
        for (int i = 0; i < CAPACITY; i++) {
            if (!alive[i]) {
                continue;
            }
            float age = 1f - life[i] / maxLife[i];
            paint.setColor(colourFor(kind[i], age));

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
    private static int colourFor(Kind what, float age) {
        switch (what) {
            case SPARK:
                return WolfPalette.shade(WolfPalette.FIRE, age < 0.4f ? 0 : (age < 0.75f ? 1 : 2));
            case SMOKE: {
                int shade = age < 0.3f ? 1 : (age < 0.6f ? 2 : 3);
                int colour = WolfPalette.shade(WolfPalette.SMOKE, shade);
                // Smoke is the one thing that fades out, or the map fills up with grey blocks.
                int alpha = (int) (200 * (1f - age));
                return (alpha << 24) | (colour & 0x00FFFFFF);
            }
            case BLOOD:
                // Dark and venous. Drawn from the hot end of the ramp it read as bright red
                // confetti rather than as blood.
                return WolfPalette.shade(WolfPalette.BLOOD, age < 0.4f ? 2 : 4);
            case DEBRIS:
                return WolfPalette.shade(WolfPalette.GUNMETAL, age < 0.5f ? 2 : 3);
            case FLAME:
                return WolfPalette.shade(WolfPalette.FIRE, age < 0.25f ? 0
                        : (age < 0.55f ? 1 : (age < 0.8f ? 2 : 3)));
            case DUST:
                return WolfPalette.shade(WolfPalette.DIRT, age < 0.5f ? 1 : 2);
            case PLASMA:
            default:
                return WolfPalette.shade(WolfPalette.OCCULT, age < 0.4f ? 0 : (age < 0.7f ? 1 : 3));
        }
    }
}
