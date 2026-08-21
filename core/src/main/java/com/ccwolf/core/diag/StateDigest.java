package com.ccwolf.core.diag;

import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.sim.Player;
import java.util.List;

/**
 * A fingerprint of the whole simulation, so a change can be shown not to have altered it.
 *
 * <p>The simulation is about to be reworked for scale — the spatial index, separation steering,
 * pathfinding and the tick order are all going to move. Most of that work is supposed to change
 * only how fast the game runs, not what happens in it, and "I think it behaves the same" is not
 * a reviewable claim about a seeded twenty-hertz simulation with a thousand units in it. So
 * every change gets held against digests recorded before the work started: either it reproduces
 * them exactly, or it changed behaviour and the diff has to be justified.
 *
 * <h2>Why two of them</h2>
 *
 * <p>{@link #exact} folds in raw float bits. It catches everything, including drift far below
 * anything a player could see — but it is only as portable as floating point is, so it is
 * advisory rather than a gate.
 *
 * <p>{@link #stable} quantises positions to 1/256 of a tile and is the one tests assert on.
 * Quantising alone would let a slow drift hide, which is why both exist and why a mismatch in
 * {@code exact} alone is still worth looking at.
 *
 * <h2>The rule this depends on</h2>
 *
 * <p>Nothing fed by a transcendental function may reach the digest. {@code Math.atan2} is
 * specified only to within two units in the last place, so two correct JVMs may disagree about
 * it. There is exactly one call to it in the simulation — {@code Unit.faceToward} setting
 * {@code facing} — and {@code facing} is read by nothing but the renderer. Keep it that way: if
 * facing ever starts influencing the simulation, it has to move to {@code StrictMath} first.
 */
public final class StateDigest {

    /** FNV-1a, 64-bit. Cheap, order-sensitive, and good enough to make a collision a curiosity. */
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** Position quantisation for the stable digest: 1/256 of a tile. */
    private static final float QUANTUM = 256f;

    private StateDigest() {
    }

    /** The gate: quantised positions, exact everything else. */
    public static long stable(GameWorld world) {
        return digest(world, true);
    }

    /** Advisory: raw float bits, so nothing at all can drift unnoticed. */
    public static long exact(GameWorld world) {
        return digest(world, false);
    }

    private static long digest(GameWorld world, boolean quantise) {
        long hash = FNV_OFFSET;
        hash = fold(hash, world.tick());

        // List order is part of the state, not an accident of it: separation steering and
        // nearest-enemy tie-breaks both read these lists in order, so a change that reorders
        // them has changed the simulation and should say so.
        List<Unit> units = world.units();
        hash = fold(hash, units.size());
        for (int i = 0; i < units.size(); i++) {
            Unit u = units.get(i);
            hash = fold(hash, u.id());
            hash = fold(hash, u.ownerId());
            hash = fold(hash, u.type().ordinal());
            hash = fold(hash, position(u.x(), quantise));
            hash = fold(hash, position(u.y(), quantise));
            hash = fold(hash, u.hp());
            hash = fold(hash, u.orderCount());
            hash = fold(hash, u.oreCarried());
            hash = fold(hash, u.weaponCooldown());
        }

        List<Building> buildings = world.buildings();
        hash = fold(hash, buildings.size());
        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            hash = fold(hash, b.id());
            hash = fold(hash, b.ownerId());
            hash = fold(hash, b.type().ordinal());
            hash = fold(hash, b.tileX());
            hash = fold(hash, b.tileY());
            hash = fold(hash, b.hp());
            hash = fold(hash, flags(b));
        }

        List<Player> players = world.players();
        for (int i = 0; i < players.size(); i++) {
            hash = fold(hash, players.get(i).credits());
        }
        return hash;
    }

    /**
     * A position as an integer.
     *
     * <p>Quantised, this is the tolerance the gate allows: a unit may end up a two-hundred-and-
     * fifty-sixth of a tile from where it used to and still count as unchanged. That is roughly
     * an eighth of a pixel at normal zoom — far below anything that could alter an outcome, and
     * far above the noise of reassociating a sum of floats.
     */
    private static int position(float value, boolean quantise) {
        return quantise ? Math.round(value * QUANTUM) : Float.floatToRawIntBits(value);
    }

    private static int flags(Building b) {
        return (b.isComplete() ? 1 : 0)
                | (b.isPowered() ? 2 : 0)
                | (b.isRepairing() ? 4 : 0)
                | (b.isSabotaged() ? 8 : 0);
    }

    private static long fold(long hash, int value) {
        long h = hash;
        for (int shift = 0; shift < 32; shift += 8) {
            h ^= (value >>> shift) & 0xFF;
            h *= FNV_PRIME;
        }
        return h;
    }

    /** Fixed-width hex, so digest files line up and diff cleanly. */
    public static String format(long digest) {
        return String.format("%016x", Long.valueOf(digest));
    }
}
