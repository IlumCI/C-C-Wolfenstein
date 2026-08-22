package com.ccwolf.core.sim;

import com.ccwolf.core.combat.Weapon;

/**
 * Rounds in the air.
 *
 * <p>The first thing in this game that exists between being fired and arriving. Every other
 * weapon is hitscan: {@code tryAttack} decides the damage and applies it on the same tick, so
 * there has never been anything to miss with. A shell is a commitment made two seconds early,
 * which is what lets a squad walk out from under one.
 *
 * <h2>What a shell knows</h2>
 *
 * <p>A point and some integers, and deliberately not a reference to anything. The gun that fired
 * it may be destroyed while it is still climbing, and {@code removeDead} will have taken that
 * entity out of the world by the time it lands — so the shell carries the owner (to decide who
 * counts as an enemy when it goes off) and the firer's id (to attribute the kill), and neither
 * of those can dangle into a stale object.
 *
 * <p>It also means a battery that changes hands mid-flight does not retrospectively change whose
 * shell is in the air. It was fired by whoever fired it.
 *
 * <h2>Why a list and not a pool</h2>
 *
 * <p>The effects layers use a fixed-capacity ring of slots and silently drop a spawn when full,
 * which is right for a puff of smoke and wrong here: a dropped shell is a lost volley, and it
 * would be lost as a function of everything that happened earlier in the match. Still perfectly
 * reproducible, and still the worst kind of bug report — "sometimes my guns just don't fire".
 *
 * <p>So this is a plain growing list compacted in place, the same shape as {@code removeDead},
 * and the iteration order is firing order, which is what anybody reading it would assume.
 */
public final class ShellLayer {

    /** How fast a shell travels, in tiles per second, whatever fired it. */
    public static final float SPEED_TILES_PER_SECOND = 9f;

    /**
     * Where the rounds of a salvo land relative to the aim point, in units of blast radius.
     *
     * <p>A fixed pattern, never rotated and never randomised. That is not laziness on two
     * counts: rotating it would want trig, and where a shell lands feeds the digest through
     * everything it kills; and scattering it would want the random number generator, which must
     * be drawn from the same number of times every tick regardless of which branch the
     * simulation took. A salvo is a beaten zone, not a spread of misses.
     */
    private static final float[][] SALVO_PATTERN = {
        {0f, 0f},
        {-1.1f, -0.6f},
        {1.1f, 0.5f},
        {0.2f, 1.2f},
        {-0.4f, 1.3f},
        {0.9f, -1.2f},
    };

    private int count;
    private int[] ownerId = new int[16];
    private int[] firedById = new int[16];
    private int[] weaponOrdinal = new int[16];
    private float[] fromX = new float[16];
    private float[] fromY = new float[16];
    private int[] toTileX = new int[16];
    private int[] toTileY = new int[16];
    private int[] firedTick = new int[16];
    private int[] impactTick = new int[16];

    private static final Weapon[] WEAPONS = Weapon.values();

    public int count() {
        return count;
    }

    public int ownerId(int i) {
        return ownerId[i];
    }

    public int firedById(int i) {
        return firedById[i];
    }

    public Weapon weapon(int i) {
        return WEAPONS[weaponOrdinal[i]];
    }

    public float fromX(int i) {
        return fromX[i];
    }

    public float fromY(int i) {
        return fromY[i];
    }

    public int toTileX(int i) {
        return toTileX[i];
    }

    public int toTileY(int i) {
        return toTileY[i];
    }

    public int firedTick(int i) {
        return firedTick[i];
    }

    public int impactTick(int i) {
        return impactTick[i];
    }

    /** Where round {@code i} is aimed, as a world point at the centre of its tile. */
    public float toX(int i) {
        return toTileX[i] + 0.5f;
    }

    public float toY(int i) {
        return toTileY[i] + 0.5f;
    }

    /**
     * How long a round takes to cover a distance.
     *
     * <p>Proportional to range, so a shot across the map is more dodgeable than one across the
     * street — which is the whole reason artillery is answered by moving. Floored rather than
     * rounded so no shot sits on a half-tick boundary, and never less than one tick, or a short
     * shot would quietly become hitscan again.
     *
     * <p>{@code Math.sqrt} is exactly specified by the language, unlike {@code sin} and
     * {@code atan2}, so this is safe to let into the digest. {@code Math.hypot} is <em>not</em>
     * — it is specified only to within two units in the last place — and must never be used
     * here.
     */
    public static int flightTicks(float fromX, float fromY, float toX, float toY,
                                  int ticksPerSecond) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        int ticks = (int) (distance / SPEED_TILES_PER_SECOND * ticksPerSecond);
        return Math.max(1, ticks);
    }

    /**
     * Puts a round, or a salvo of them, into the air.
     *
     * <p>Every round of a salvo is timed from its own landing point, so a wide pattern arrives
     * raggedly rather than all at once. That is both more truthful and more useful: it stretches
     * the moment a squad has to survive rather than concentrating it.
     *
     * @param rounds how many, capped at the pattern's length
     * @return how many were actually launched
     */
    public int fire(int ownerId, int firedById, Weapon weapon, float fromX, float fromY,
                    int aimTileX, int aimTileY, int rounds, int tick, int ticksPerSecond) {
        int salvo = Math.max(1, Math.min(rounds, SALVO_PATTERN.length));
        float spread = Math.max(1f, weapon.blastRadius());
        for (int r = 0; r < salvo; r++) {
            int tileX = aimTileX + (int) (SALVO_PATTERN[r][0] * spread);
            int tileY = aimTileY + (int) (SALVO_PATTERN[r][1] * spread);
            add(ownerId, firedById, weapon, fromX, fromY, tileX, tileY, tick, ticksPerSecond);
        }
        return salvo;
    }

    private void add(int owner, int firer, Weapon weapon, float ox, float oy,
                     int tileX, int tileY, int tick, int ticksPerSecond) {
        if (count == ownerId.length) {
            grow();
        }
        ownerId[count] = owner;
        firedById[count] = firer;
        weaponOrdinal[count] = weapon.ordinal();
        fromX[count] = ox;
        fromY[count] = oy;
        toTileX[count] = tileX;
        toTileY[count] = tileY;
        firedTick[count] = tick;
        impactTick[count] = tick
                + flightTicks(ox, oy, tileX + 0.5f, tileY + 0.5f, ticksPerSecond);
        count++;
    }

    /**
     * Drops the rounds that have landed, keeping the rest in firing order.
     *
     * <p>Forward compaction rather than swap-removal, for the same reason {@code removeDead}
     * uses it: the order things are visited in is part of the simulation's behaviour, and
     * shuffling a list to save a copy is not worth making that order depend on history.
     */
    public void removeLanded(int tick) {
        int write = 0;
        for (int i = 0; i < count; i++) {
            if (impactTick[i] > tick) {
                if (write != i) {
                    ownerId[write] = ownerId[i];
                    firedById[write] = firedById[i];
                    weaponOrdinal[write] = weaponOrdinal[i];
                    fromX[write] = fromX[i];
                    fromY[write] = fromY[i];
                    toTileX[write] = toTileX[i];
                    toTileY[write] = toTileY[i];
                    firedTick[write] = firedTick[i];
                    impactTick[write] = impactTick[i];
                }
                write++;
            }
        }
        count = write;
    }

    public void clear() {
        count = 0;
    }

    private void grow() {
        int size = ownerId.length * 2;
        ownerId = java.util.Arrays.copyOf(ownerId, size);
        firedById = java.util.Arrays.copyOf(firedById, size);
        weaponOrdinal = java.util.Arrays.copyOf(weaponOrdinal, size);
        fromX = java.util.Arrays.copyOf(fromX, size);
        fromY = java.util.Arrays.copyOf(fromY, size);
        toTileX = java.util.Arrays.copyOf(toTileX, size);
        toTileY = java.util.Arrays.copyOf(toTileY, size);
        firedTick = java.util.Arrays.copyOf(firedTick, size);
        impactTick = java.util.Arrays.copyOf(impactTick, size);
    }
}
