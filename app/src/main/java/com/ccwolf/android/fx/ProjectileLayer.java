package com.ccwolf.android.fx;

import android.graphics.Canvas;
import android.graphics.Paint;
import com.ccwolf.android.art.WolfPalette;
import com.ccwolf.android.render.Camera;
import com.ccwolf.core.combat.WeaponClass;
import com.ccwolf.core.event.GameEvent;

/**
 * Rounds in flight.
 *
 * <p>The simulation is hitscan — damage lands the tick the shot is fired — so these are purely
 * visual: a projectile flies from muzzle to the point of impact and then tells the
 * {@link FxDirector} to play the impact effect. Making them real entities with travel time
 * would be more honest, and would rebalance every engagement in the game.
 *
 * <p>Because damage has already been applied, a projectile is only ever a few hundred
 * milliseconds behind the truth, and the payoff is that a battle reads: you can see who is
 * shooting at whom, and with what.
 */
public final class ProjectileLayer {

    public static final int CAPACITY = 160;

    /** How a round looks and moves. */
    public enum Kind { BULLET, SNIPER_ROUND, SHELL, ROCKET, GRENADE, PLASMA }

    private final float[] fromX = new float[CAPACITY];
    private final float[] fromY = new float[CAPACITY];
    private final float[] toX = new float[CAPACITY];
    private final float[] toY = new float[CAPACITY];
    private final float[] progress = new float[CAPACITY];
    private final float[] speed = new float[CAPACITY];
    private final Kind[] kind = new Kind[CAPACITY];
    private final int[] targetKind = new int[CAPACITY];
    private final boolean[] alive = new boolean[CAPACITY];

    private final Paint paint = new Paint();
    private int cursor;

    public ProjectileLayer() {
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

    /** Maps a weapon class onto the round it fires. */
    public static Kind kindFor(WeaponClass weapon) {
        if (weapon == null) {
            return Kind.BULLET;
        }
        switch (weapon) {
            case SNIPER:
                return Kind.SNIPER_ROUND;
            case CANNON:
                return Kind.SHELL;
            case ROCKET:
                return Kind.ROCKET;
            case GRENADE:
                return Kind.GRENADE;
            case OCCULT:
                return Kind.PLASMA;
            case SMALL_ARMS:
            case FLAME:
            case MELEE:
            default:
                return Kind.BULLET;
        }
    }

    /** Flight speed in fractions of the journey per second: fast rounds cross almost at once. */
    private static float speedFor(Kind what) {
        switch (what) {
            case SNIPER_ROUND:
                return 14f;
            case BULLET:
                return 9f;
            case SHELL:
                return 6f;
            case PLASMA:
                return 5f;
            case GRENADE:
                return 3.2f;
            case ROCKET:
            default:
                return 3.6f;
        }
    }

    public void fire(Kind what, float sx, float sy, float tx, float ty,
                     GameEvent.TargetKind hit) {
        int slot = -1;
        for (int i = 0; i < CAPACITY; i++) {
            int candidate = (cursor + i) % CAPACITY;
            if (!alive[candidate]) {
                slot = candidate;
                break;
            }
        }
        if (slot < 0) {
            return; // Too much in the air already; dropping one round is invisible.
        }
        cursor = (slot + 1) % CAPACITY;

        fromX[slot] = sx;
        fromY[slot] = sy;
        toX[slot] = tx;
        toY[slot] = ty;
        progress[slot] = 0f;
        speed[slot] = speedFor(what);
        kind[slot] = what;
        targetKind[slot] = hit == null ? 0 : hit.ordinal();
        alive[slot] = true;
    }

    /**
     * Advances every round, and reports each arrival to the director so it can play the
     * impact.
     */
    public void update(float dt, FxDirector director, ParticleSystem particles) {
        for (int i = 0; i < CAPACITY; i++) {
            if (!alive[i]) {
                continue;
            }
            progress[i] += speed[i] * dt;

            float px = current(fromX[i], toX[i], progress[i], kind[i]);
            float py = currentY(i);

            // Trails: what a round leaves behind is most of how you tell it apart in flight.
            // Trails are jittered and small. Spawned dead centre at a fixed size they came
            // out as an evenly spaced string of beads rather than a wake.
            if (kind[i] == Kind.ROCKET) {
                particles.puff(ParticleSystem.Kind.SMOKE, px, py, 2, 0.5f, 0.55f, 0.09f);
                particles.spawn(ParticleSystem.Kind.FLAME, px, py, 0f, 0f, 0.1f, 0.06f);
            } else if (kind[i] == Kind.PLASMA) {
                particles.puff(ParticleSystem.Kind.PLASMA, px, py, 2, 0.6f, 0.22f, 0.06f);
            }

            if (progress[i] >= 1f) {
                alive[i] = false;
                director.onImpact(kind[i], toX[i], toY[i],
                        GameEvent.TargetKind.values()[targetKind[i]]);
            }
        }
    }

    private float current(float from, float to, float t, Kind what) {
        return from + (to - from) * Math.min(1f, t);
    }

    /** Grenades lob: the arc is faked by lifting the round off the ground mid-flight. */
    private float currentY(int i) {
        float t = Math.min(1f, progress[i]);
        float base = fromY[i] + (toY[i] - fromY[i]) * t;
        if (kind[i] == Kind.GRENADE) {
            base -= 4f * t * (1f - t);
        }
        return base;
    }

    public void draw(Canvas canvas, Camera camera) {
        float tile = camera.tilePx();
        for (int i = 0; i < CAPACITY; i++) {
            if (!alive[i]) {
                continue;
            }
            float t = Math.min(1f, progress[i]);
            float px = fromX[i] + (toX[i] - fromX[i]) * t;
            float py = currentY(i);
            float screenX = camera.screenX(px);
            float screenY = camera.screenY(py);

            switch (kind[i]) {
                case BULLET:
                case SNIPER_ROUND: {
                    // A streak, drawn back along its own path.
                    float tailT = Math.max(0f, t - (kind[i] == Kind.SNIPER_ROUND ? 0.22f : 0.10f));
                    float tailX = camera.screenX(fromX[i] + (toX[i] - fromX[i]) * tailT);
                    float tailY = camera.screenY(fromY[i] + (toY[i] - fromY[i]) * tailT);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(1.5f, tile * 0.045f));
                    paint.setColor(WolfPalette.shade(WolfPalette.FIRE,
                            kind[i] == Kind.SNIPER_ROUND ? 0 : 1));
                    canvas.drawLine(tailX, tailY, screenX, screenY, paint);
                    paint.setStyle(Paint.Style.FILL);
                    break;
                }
                case SHELL: {
                    paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 1));
                    float r = tile * 0.09f;
                    canvas.drawRect(screenX - r, screenY - r, screenX + r, screenY + r, paint);
                    break;
                }
                case ROCKET: {
                    paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 2));
                    float r = tile * 0.11f;
                    canvas.drawRect(screenX - r, screenY - r * 0.7f, screenX + r,
                            screenY + r * 0.7f, paint);
                    paint.setColor(WolfPalette.shade(WolfPalette.FIRE, 1));
                    canvas.drawRect(screenX - r * 1.6f, screenY - r * 0.35f, screenX - r,
                            screenY + r * 0.35f, paint);
                    break;
                }
                case GRENADE: {
                    // The charge, plus its shadow on the ground so the arc reads.
                    float groundY = camera.screenY(fromY[i] + (toY[i] - fromY[i]) * t);
                    paint.setColor(0x66000000);
                    float sr = tile * 0.09f;
                    canvas.drawRect(screenX - sr, groundY - sr * 0.5f, screenX + sr,
                            groundY + sr * 0.5f, paint);
                    paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 2));
                    float r = tile * 0.1f;
                    canvas.drawRect(screenX - r, screenY - r, screenX + r, screenY + r, paint);
                    break;
                }
                case PLASMA:
                default: {
                    paint.setColor(WolfPalette.shade(WolfPalette.OCCULT, 2));
                    float outer = tile * 0.17f;
                    canvas.drawRect(screenX - outer, screenY - outer, screenX + outer,
                            screenY + outer, paint);
                    paint.setColor(WolfPalette.shade(WolfPalette.OCCULT, 0));
                    float inner = tile * 0.08f;
                    canvas.drawRect(screenX - inner, screenY - inner, screenX + inner,
                            screenY + inner, paint);
                    break;
                }
            }
        }
    }
}
