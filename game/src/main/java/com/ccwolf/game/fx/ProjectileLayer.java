package com.ccwolf.game.fx;

import com.ccwolf.gfx.Surface;
import com.ccwolf.gfx.Brush;
import com.ccwolf.game.art.WolfPalette;
import com.ccwolf.game.render.Camera;
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
    public enum Kind {
        BULLET, SNIPER_ROUND, SHELL, ROCKET, GRENADE, PLASMA,
        /**
         * An artillery round, and the only one whose flight this layer does not invent.
         *
         * <p>Every other kind here is a decoration over a hitscan shot: the damage landed the
         * tick the trigger was pulled and the round is drawn crossing afterwards for the look
         * of it. A shell is real. The simulation decides when it arrives, tells this layer how
         * long that takes, and plays the impact itself — so this kind is told its duration and
         * stays silent when it lands, or the explosion happens twice.
         */
        LOBBED
    }

    private final float[] fromX = new float[CAPACITY];
    private final float[] fromY = new float[CAPACITY];
    private final float[] toX = new float[CAPACITY];
    private final float[] toY = new float[CAPACITY];
    private final float[] progress = new float[CAPACITY];
    private final float[] speed = new float[CAPACITY];
    private final Kind[] kind = new Kind[CAPACITY];
    /** How high this round arcs, in tiles. Zero for anything that goes in a straight line. */
    private final float[] arc = new float[CAPACITY];
    private final int[] targetKind = new int[CAPACITY];
    private final boolean[] alive = new boolean[CAPACITY];

    private final Brush paint = new Brush();
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
            case ARTILLERY:
                return Kind.LOBBED;
            case GAS:
                // A gas shell flies like any other shell; what makes it different starts on
                // the ground, and the ground layer draws that part.
                return Kind.LOBBED;
            case OCCULT:
                // The Resonanzkanone throws nothing, so what it sends is drawn as a bolt
                // rather than as a shell - but it still takes time to arrive, so it is lobbed
                // like one and told its own flight time.
                return Kind.LOBBED;
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
            case LOBBED:
                // Never used: a lobbed round is always told its duration by the simulation.
                return 1f;
            case ROCKET:
            default:
                return 3.6f;
        }
    }

    public void fire(Kind what, float sx, float sy, float tx, float ty,
                     GameEvent.TargetKind hit) {
        fire(what, sx, sy, tx, ty, hit, 0f);
    }

    /**
     * The same, for a round whose flight time is not this layer's to invent.
     *
     * <p>{@code speedFor} is a guess that looks right; a shell's is a fact the simulation
     * already knows, because it decided two seconds ago which tick the thing lands on. Passing
     * it in is what keeps the picture and the damage on the same schedule.
     *
     * @param seconds how long the round takes, or 0 to use this layer's own guess
     */
    public void fire(Kind what, float sx, float sy, float tx, float ty,
                     GameEvent.TargetKind hit, float seconds) {
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
        speed[slot] = seconds > 0f ? 1f / seconds : speedFor(what);
        kind[slot] = what;
        // A long shot should climb higher than a short one, so a barrage across the map reads
        // differently from one across the street. Scaled off the journey, capped so it does not
        // leave the top of the screen.
        float dx = tx - sx;
        float dy = ty - sy;
        arc[slot] = what == Kind.LOBBED
                ? Math.min(7f, 1.5f + (float) Math.sqrt(dx * dx + dy * dy) * 0.35f) : 0f;
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
            } else if (kind[i] == Kind.LOBBED) {
                particles.puff(ParticleSystem.Kind.SMOKE, px, py, 1, 0.3f, 0.7f, 0.07f);
            }

            if (progress[i] >= 1f) {
                alive[i] = false;
                // A lobbed round stays silent. The simulation raises its own impact event at
                // the tick it actually lands, and that is what plays the blast - letting this
                // one speak too would explode everything twice.
                if (kind[i] != Kind.LOBBED) {
                    director.onImpact(kind[i], toX[i], toY[i],
                            GameEvent.TargetKind.values()[targetKind[i]]);
                }
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
        } else if (kind[i] == Kind.LOBBED) {
            base -= arc[i] * 4f * t * (1f - t);
        }
        return base;
    }

    public void draw(Surface surface, Camera camera) {
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
                    paint.setStrokeWidth(Math.max(1.5f, tile * 0.045f));
                    paint.setColor(WolfPalette.shade(WolfPalette.FIRE,
                            kind[i] == Kind.SNIPER_ROUND ? 0 : 1));
                    surface.drawLine(tailX, tailY, screenX, screenY, paint);
                    break;
                }
                case SHELL: {
                    paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 1));
                    float r = tile * 0.09f;
                    surface.fillRect(screenX - r, screenY - r, screenX + r, screenY + r, paint);
                    break;
                }
                case ROCKET: {
                    paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 2));
                    float r = tile * 0.11f;
                    surface.fillRect(screenX - r, screenY - r * 0.7f, screenX + r,
                            screenY + r * 0.7f, paint);
                    paint.setColor(WolfPalette.shade(WolfPalette.FIRE, 1));
                    surface.fillRect(screenX - r * 1.6f, screenY - r * 0.35f, screenX - r,
                            screenY + r * 0.35f, paint);
                    break;
                }
                case GRENADE: {
                    // The charge, plus its shadow on the ground so the arc reads.
                    float groundY = camera.screenY(fromY[i] + (toY[i] - fromY[i]) * t);
                    paint.setColor(0x66000000);
                    float sr = tile * 0.09f;
                    surface.fillRect(screenX - sr, groundY - sr * 0.5f, screenX + sr,
                            groundY + sr * 0.5f, paint);
                    // Taller than it is wide: a square charge reads as a floating crate.
                    paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 2));
                    float r = tile * 0.07f;
                    surface.fillRect(screenX - r, screenY - r * 1.4f, screenX + r,
                            screenY + r * 1.4f, paint);
                    break;
                }
                case LOBBED: {
                    // The shadow is the whole trick. Without something on the ground tracking
                    // underneath it, a round drawn higher up the screen is not a round climbing
                    // - it is a round somewhere else.
                    float groundY = camera.screenY(fromY[i] + (toY[i] - fromY[i]) * t);
                    paint.setColor(0x66000000);
                    float sr = tile * 0.08f;
                    surface.fillRect(screenX - sr, groundY - sr * 0.5f, screenX + sr,
                            groundY + sr * 0.5f, paint);
                    paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 4));
                    float r = tile * 0.1f;
                    surface.fillRect(screenX - r, screenY - r * 1.5f, screenX + r,
                            screenY + r * 1.5f, paint);
                    paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 1));
                    surface.fillRect(screenX - r * 0.5f, screenY - r * 1.5f, screenX + r * 0.5f,
                            screenY - r * 0.4f, paint);
                    break;
                }
                case PLASMA:
                default: {
                    // A cross rather than a filled square. A solid block of green at this
                    // size read as a hovering tile, not as a bolt of anything.
                    paint.setColor(WolfPalette.shade(WolfPalette.OCCULT, 2));
                    float arm = tile * 0.13f;
                    float thin = tile * 0.045f;
                    surface.fillRect(screenX - arm, screenY - thin, screenX + arm,
                            screenY + thin, paint);
                    surface.fillRect(screenX - thin, screenY - arm, screenX + thin,
                            screenY + arm, paint);
                    paint.setColor(WolfPalette.shade(WolfPalette.OCCULT, 0));
                    float inner = tile * 0.055f;
                    surface.fillRect(screenX - inner, screenY - inner, screenX + inner,
                            screenY + inner, paint);
                    break;
                }
            }
        }
    }
}
