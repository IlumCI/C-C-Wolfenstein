package com.ccwolf.core.combat;

/**
 * How badly being shot at stops a man doing his job, and how much cover helps.
 *
 * <p>The two together are what turn a firefight into something other than an arithmetic race.
 * Without them, two lines of infantry walk into each other and the one with more hit points
 * wins; with them, fire pins men down, cover decides whether that fire matters, and taking a
 * position costs something other than health.
 *
 * <p>All integers and clamps — no interpolation curves and no randomness. Combat has to stay
 * reproducible from a seed, and a table anyone can read is easier to balance than a formula
 * nobody can.
 */
public final class Suppression {

    /** Fully suppressed. */
    public static final int MAX = 100;

    /** At or above this, a man goes to ground: slower, harder to hit, slower to shoot. */
    public static final int PRONE = 40;

    /** At or above this, he stops advancing altogether. He can still shoot. */
    public static final int PINNED = 85;

    /**
     * Points shed per tick in the open, and behind cover.
     *
     * <p>These are set against what one section can put out, and the balance between them is
     * the whole feel of the system. Recovery has to be close to a single squad's output, so
     * that pinning a position takes more than one squad's worth of fire and lifts the moment
     * that fire slackens.
     *
     * <p>The first attempt had a squad of eight generating around 140 points a second against
     * 40 of recovery, which pinned anything instantly and permanently. Every attack in the game
     * stalled at first contact and twelve matches in twenty ended in stalemate — the trench
     * deadlock, arrived at by accident and with no artillery yet to break it.
     */
    public static final int RECOVERY = 3;

    public static final int RECOVERY_IN_COVER = 6;

    /** No sane suppression value, so a missing table entry cannot look like a real one. */
    private static final int UNSET = Integer.MIN_VALUE;

    private Suppression() {
    }

    /**
     * How much one shot of a weapon rattles whoever it was aimed at, by weapon class.
     *
     * <p>Not proportional to damage. A sniper round does far more harm than a burst of rifle
     * fire and is far less use at pinning a line, because the thing that keeps heads down is
     * volume. A flamethrower is terrifying out of all proportion to what it does to armour.
     *
     * <p>A table rather than a switch, because a switch needs a {@code default:} and a
     * {@code default:} that returns a legal number is a silent wrong answer: a weapon class
     * added without a line here would have got whatever the fall-through happened to be, and
     * nothing would have complained. The sentinel below makes that a failure at class load.
     */
    private static final int[] PER_SHOT = new int[WeaponClass.values().length];

    /**
     * How much of a weapon's damage cover takes away, at full cover.
     *
     * <p>This asymmetry is the point of the whole system. A trench is nearly proof against
     * rifles and does almost nothing against a grenade dropped into it, which is why artillery
     * and grenadiers exist and why a dug-in line is a problem to be solved rather than a wall.
     */
    private static final float[] COVER_EFFECT = new float[WeaponClass.values().length];

    static {
        java.util.Arrays.fill(PER_SHOT, UNSET);
        java.util.Arrays.fill(COVER_EFFECT, Float.NaN);

        // Aimed fire at a man behind masonry mostly hits masonry.
        set(WeaponClass.SMALL_ARMS, 6, 0.65f);
        set(WeaponClass.SNIPER, 4, 0.65f);
        set(WeaponClass.CANNON, 15, 0.30f);
        set(WeaponClass.ROCKET, 16, 0.30f);
        // Grenades and flame go over and around. Cover barely helps.
        set(WeaponClass.GRENADE, 20, 0.15f);
        set(WeaponClass.FLAME, 24, 0.15f);
        // Nothing to hide behind when it is already on top of you.
        set(WeaponClass.MELEE, 9, 0f);
        // The alien gun does not care what you are hiding behind, and what it does to nerve is
        // most of what it does at all. Its first user is the Resonanzkanone; until now nothing
        // fired OCCULT, so these numbers were never load-bearing and were never chosen.
        set(WeaponClass.OCCULT, 28, 0f);
        // Shellfire is the loudest thing in the game and cover is very little help against
        // something that arrives from above.
        set(WeaponClass.ARTILLERY, 32, 0.12f);

        for (WeaponClass w : WeaponClass.values()) {
            if (PER_SHOT[w.ordinal()] == UNSET || Float.isNaN(COVER_EFFECT[w.ordinal()])) {
                throw new IllegalStateException("Suppression has no entry for " + w
                        + " - add one above, and decide what it should be rather than "
                        + "letting it default");
            }
        }
    }

    private static void set(WeaponClass weapon, int perShot, float coverEffect) {
        PER_SHOT[weapon.ordinal()] = perShot;
        COVER_EFFECT[weapon.ordinal()] = coverEffect;
    }

    public static int perShot(WeaponClass weapon) {
        return PER_SHOT[weapon.ordinal()];
    }

    private static float coverEffect(WeaponClass weapon) {
        return COVER_EFFECT[weapon.ordinal()];
    }

    /**
     * Damage multiplier for a target in cover.
     *
     * @param coverLevel 0 to TileMap.MAX_COVER
     * @param maxCover the scale coverLevel is measured on
     */
    /**
     * The same, with a doctrine's opinion of how much cover is worth folded in.
     *
     * <p>The scale multiplies the <em>fraction cover removes</em>, not the damage: it makes an
     * existing trench better rather than inventing protection for a man in the open, and it does
     * nothing at all against the classes cover never helped against, which is what stops a
     * defensive doctrine from being an answer to everything.
     *
     * <p>Clamped here rather than at the caller. A scale high enough to take the fraction past
     * one would make a shot heal, and the clamp belongs next to the arithmetic it protects.
     */
    /**
     * The fraction of a shot that full cover removes, for whoever needs to check the curve
     * rather than reproduce it. Not used by the simulation, which goes through the methods
     * below so that the clamping happens in one place.
     */
    public static float coverEffectFor(WeaponClass weapon) {
        return coverEffect(weapon);
    }

    public static float damageInCover(WeaponClass weapon, int coverLevel, int maxCover,
                                      float coverScale) {
        if (coverLevel <= 0 || maxCover <= 0) {
            return 1f;
        }
        float fraction = Math.min(1f, coverLevel / (float) maxCover);
        float effect = Math.min(1f, coverEffect(weapon) * coverScale);
        return 1f - effect * fraction;
    }

    public static float damageInCover(WeaponClass weapon, int coverLevel, int maxCover) {
        if (coverLevel <= 0 || maxCover <= 0) {
            return 1f;
        }
        float fraction = Math.min(1f, coverLevel / (float) maxCover);
        return 1f - coverEffect(weapon) * fraction;
    }

    /** Damage multiplier for a man who has gone to ground. */
    public static float damageWhenProne() {
        return 0.7f;
    }

    /** Speed multiplier at a given suppression, from upright through prone to pinned. */
    public static float speedFactor(int suppression) {
        if (suppression >= PINNED) {
            return 0f;
        }
        if (suppression >= PRONE) {
            return 0.45f;
        }
        return 1f;
    }

    /** Weapon cooldown multiplier: a man with his face in the dirt shoots slowly. */
    public static float cooldownFactor(int suppression) {
        return suppression >= PRONE ? 1.35f : 1f;
    }
}
