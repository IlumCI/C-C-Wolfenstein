package com.ccwolf.core.combat;

/**
 * The economy of dug ground: what it costs to make, and what it costs to take away.
 *
 * <p>Cover on a tile is the same number whether the ground came that way or a section spent two
 * minutes with entrenching tools on it. This class owns the two rates that decide whether that
 * number is worth anything — how long a man takes to raise it, and how readily a shell puts it
 * back — because between them they set whether the front freezes.
 *
 * <p>The intended shape is deliberate and worth stating, since it is the thing to retune if the
 * front ever locks. <b>Digging is slow, flattening is fast.</b> A scrape is quick enough to be
 * worth taking under fire; a full trench is a commitment of most of a minute per man. One
 * high-explosive shell in the right place undoes a good part of that. So a line held only by
 * infantry hardens over time and becomes expensive to storm, and the answer to it is guns —
 * which is exactly the counterplay artillery is being built for.
 */
public final class Earthworks {

    /**
     * Ticks of uninterrupted digging per level of cover gained.
     *
     * <p>At 20 ticks to the second: four seconds to scrape a hollow, sixteen to have a man
     * properly dug in, and a shade over half a minute for the deepest a tile goes. Fast enough
     * that digging in on arrival is a real option, slow enough that being caught mid-dig is a
     * real risk.
     */
    public static final int TICKS_PER_LEVEL = 80;

    /**
     * How much slower a tile is to cross per level of dug cover.
     *
     * <p>This is the reason a trench line is hard to storm rather than merely a good place to
     * stand. At the deepest level a tile costs almost three times what open ground does, so
     * attacking infantry spend far longer in the beaten zone in front of it.
     *
     * <p>It applies to the diggings only, not to the cover the ground already offered — rubble
     * is slow because it is rubble, and that is already in {@code Terrain}. Counting it twice
     * would make ruins nearly impassable for a reason nobody chose.
     */
    public static final float MOVE_COST_PER_LEVEL = 0.45f;

    /**
     * How many levels of earth one blast strips from the tiles it lands on, by weapon class.
     *
     * <p>Nil for anything that cannot move soil, whatever it does to men. Rifle fire does not
     * fill in a trench and a flamethrower does not either — it makes a trench a bad place to be
     * for as long as it burns, which is what the suppression table already says.
     *
     * <p>A table rather than a switch for the same reason as the suppression tables: a
     * {@code default:} of zero is exactly the wrong answer for anything meant to break a line,
     * and it would have been given silently.
     */
    private static final int[] FLATTENING = new int[WeaponClass.values().length];

    /** Not a possible number of levels, so a missing entry cannot look like a real one. */
    private static final int UNSET = Integer.MIN_VALUE;

    static {
        java.util.Arrays.fill(FLATTENING, UNSET);

        FLATTENING[WeaponClass.SMALL_ARMS.ordinal()] = 0;
        FLATTENING[WeaponClass.SNIPER.ordinal()] = 0;
        FLATTENING[WeaponClass.CANNON.ordinal()] = 1;
        FLATTENING[WeaponClass.ROCKET.ordinal()] = 2;
        FLATTENING[WeaponClass.GRENADE.ordinal()] = 1;
        FLATTENING[WeaponClass.FLAME.ordinal()] = 0;
        FLATTENING[WeaponClass.MELEE.ordinal()] = 0;
        FLATTENING[WeaponClass.OCCULT.ordinal()] = 0;

        for (WeaponClass w : WeaponClass.values()) {
            if (FLATTENING[w.ordinal()] == UNSET) {
                throw new IllegalStateException("Earthworks has no flattening entry for " + w
                        + " - add one above, and decide what it should be rather than "
                        + "letting it default");
            }
        }
    }

    public static int flattening(WeaponClass weapon) {
        return FLATTENING[weapon.ordinal()];
    }

    /** True if this weapon is worth even asking the map about. Saves a tile sweep per shot. */
    public static boolean movesEarth(WeaponClass weapon) {
        return flattening(weapon) > 0;
    }

    /**
     * Extra traversal cost from earthworks on a tile, above whatever the ground already costs.
     *
     * @param dugLevels levels above the terrain's own cover; already clamped by the map
     */
    public static float moveCostFor(int dugLevels) {
        return MOVE_COST_PER_LEVEL * Math.max(0, dugLevels);
    }

    private Earthworks() {
    }
}
