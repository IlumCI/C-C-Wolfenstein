package com.ccwolf.core.combat;

import com.ccwolf.core.entity.Doctrine;
import com.ccwolf.core.map.TileMap;

/**
 * What each doctrine is actually worth, in one table.
 *
 * <p>The numbers live here rather than on {@link Doctrine} itself so that the enum stays a list
 * of choices and this stays a balance file — the thing that gets retuned after a sweep, in one
 * place, without touching what a doctrine <em>is</em>.
 *
 * <p>Every accessor takes the doctrine and is null-safe, because a player who picked nothing is
 * the ordinary case and must go on playing exactly as they did before doctrines existed. Null
 * returns the plain value in every one of these, and that is the property the tests pin.
 *
 * <h2>Filled by a setter, checked at class load</h2>
 *
 * <p>Same shape as {@link Earthworks}, {@link Suppression} and {@link DamageTable}, and for the
 * same reason: all three of those started life as switches with a {@code default}, and a
 * {@code default} is exactly the wrong answer for a table whose whole job is to be complete. A
 * doctrine added to the enum without a row here throws when the class loads rather than fighting
 * a match with silently ordinary numbers.
 */
public final class Doctrines {

    /** How long a level of digging takes anyone who has no doctrine about it. */
    public static final int PLAIN_DIG_TICKS = Earthworks.TICKS_PER_LEVEL;

    /** Deep Works digs a level in this many ticks instead — about half again as fast. */
    private static final int TIEFBAU_DIG_TICKS = 52;

    /**
     * How much more the same cover is worth to a Hardened Line.
     *
     * <p>A multiplier on the fraction cover removes, not on the damage — so it improves what is
     * already there rather than inventing protection where a man is standing in the open, and it
     * does nothing at all against the classes cover never helped against anyway.
     */
    private static final float STAHLBETON_COVER = 1.5f;

    /** How much wider a Dispersal squad stands. */
    private static final float ZERSTREUUNG_SPREAD = 1.5f;

    /**
     * As deep as a side digs.
     *
     * <p>{@link TileMap#FULL_COVER} for everyone: four is where the ordinary protection curve
     * tops out, and it is what every side could reach before doctrines existed. Deep Works goes
     * one further, to a roofed dugout, and it is the only thing on the map that can.
     */
    public static final int PLAIN_MAX_DEPTH = TileMap.FULL_COVER;

    private static final int[] DIG_TICKS = new int[Doctrine.values().length];
    private static final boolean[] DIGS_UNDER_FIRE = new boolean[Doctrine.values().length];
    private static final float[] COVER_SCALE = new float[Doctrine.values().length];
    private static final float[] SPREAD = new float[Doctrine.values().length];
    private static final int[] MAX_DEPTH = new int[Doctrine.values().length];
    private static final boolean[] FILLED = new boolean[Doctrine.values().length];

    static {
        int plainTicks = PLAIN_DIG_TICKS;
        int plainDepth = PLAIN_MAX_DEPTH;

        //   doctrine             dig ticks    under fire  cover   spread  depth
        set(Doctrine.TIEFBAU,     TIEFBAU_DIG_TICKS, true,  1f, 1f, Earthworks.ROOFED);
        set(Doctrine.STAHLBETON,  plainTicks, false, STAHLBETON_COVER, 1f, plainDepth);
        set(Doctrine.ZERSTREUUNG, plainTicks, false, 1f, ZERSTREUUNG_SPREAD, plainDepth);

        // The Regime's three do nothing to the ground or to the men holding it. They buy
        // something instead, and what they buy is a roster entry rather than a number here.
        set(Doctrine.GASKRIEG,    plainTicks, false, 1f, 1f, plainDepth);
        set(Doctrine.BRANDSTURM,  plainTicks, false, 1f, 1f, plainDepth);
        set(Doctrine.AUSMERZUNG,  plainTicks, false, 1f, 1f, plainDepth);

        for (Doctrine d : Doctrine.values()) {
            if (!FILLED[d.ordinal()]) {
                throw new IllegalStateException("Doctrines has no row for " + d
                        + ". Add one - a doctrine with no numbers is a doctrine that does"
                        + " nothing, and it would have done nothing silently.");
            }
        }
    }

    private static void set(Doctrine d, int digTicks, boolean digsUnderFire, float coverScale,
                            float spread, int maxDepth) {
        int i = d.ordinal();
        DIG_TICKS[i] = digTicks;
        DIGS_UNDER_FIRE[i] = digsUnderFire;
        COVER_SCALE[i] = coverScale;
        SPREAD[i] = spread;
        MAX_DEPTH[i] = maxDepth;
        FILLED[i] = true;
    }

    private Doctrines() {
    }

    /** Ticks of uninterrupted work per level of trench. */
    public static int digTicks(Doctrine d) {
        return d == null ? PLAIN_DIG_TICKS : DIG_TICKS[d.ordinal()];
    }

    /**
     * Whether men of this doctrine go on digging with their heads down.
     *
     * <p>Everyone else stops: {@code EntrenchOrder}'s javadoc states the rule plainly — a man
     * cannot dig and shoot at the same time, and a line that arrives under fire never hardens.
     * Deep Works is the one answer to that, and it is deliberately the answer to artillery,
     * which is the weapon that makes a line arrive under fire.
     */
    public static boolean digsUnderFire(Doctrine d) {
        return d != null && DIGS_UNDER_FIRE[d.ordinal()];
    }

    /** Multiplier on how much of a shot cover takes off. 1 for everyone without an opinion. */
    public static float coverScale(Doctrine d) {
        return d == null ? 1f : COVER_SCALE[d.ordinal()];
    }

    /** Multiplier on how far apart a squad's men stand. 1 is the formation as authored. */
    public static float spread(Doctrine d) {
        return d == null ? 1f : SPREAD[d.ordinal()];
    }

    /** How deep this side's men will dig, counting whatever the ground already offered. */
    public static int maxDepth(Doctrine d) {
        return d == null ? PLAIN_MAX_DEPTH : MAX_DEPTH[d.ordinal()];
    }
}
