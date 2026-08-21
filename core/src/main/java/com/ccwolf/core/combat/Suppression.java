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

    private Suppression() {
    }

    /**
     * How much one shot of a weapon rattles whoever it was aimed at.
     *
     * <p>Not proportional to damage. A sniper round does far more harm than a burst of rifle
     * fire and is far less use at pinning a line, because the thing that keeps heads down is
     * volume. A flamethrower is terrifying out of all proportion to what it does to armour.
     */
    public static int perShot(WeaponClass weapon) {
        switch (weapon) {
            case SMALL_ARMS:
                return 6;
            case SNIPER:
                return 4;
            case CANNON:
                return 15;
            case ROCKET:
                return 16;
            case GRENADE:
                return 20;
            case FLAME:
                return 24;
            case MELEE:
                return 9;
            case OCCULT:
            default:
                return 14;
        }
    }

    /**
     * How much of a weapon's damage cover takes away, at full cover.
     *
     * <p>This asymmetry is the point of the whole system. A trench is nearly proof against
     * rifles and does almost nothing against a grenade dropped into it, which is why artillery
     * and grenadiers exist and why a dug-in line is a problem to be solved rather than a wall.
     */
    private static float coverEffect(WeaponClass weapon) {
        switch (weapon) {
            case SMALL_ARMS:
            case SNIPER:
                // Aimed fire at a man behind masonry mostly hits masonry.
                return 0.65f;
            case CANNON:
            case ROCKET:
            case OCCULT:
                return 0.30f;
            case GRENADE:
            case FLAME:
                // Both go over and around. Cover barely helps.
                return 0.15f;
            case MELEE:
            default:
                // Nothing to hide behind when it is already on top of you.
                return 0f;
        }
    }

    /**
     * Damage multiplier for a target in cover.
     *
     * @param coverLevel 0 to TileMap.MAX_COVER
     * @param maxCover the scale coverLevel is measured on
     */
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
