package com.ccwolf.core.order;

import com.ccwolf.core.combat.Weapon;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.sim.GameWorld;

/**
 * Shell a piece of ground, and go on shelling it.
 *
 * <p>Like {@link EntrenchOrder} this order never completes. A gun told to bombard somewhere is
 * not performing a task with an end; it has been given a job, and it keeps at it until it is
 * told otherwise or something walks close enough to make it somebody else's problem.
 *
 * <p>Two things make it different from every other attack order in the game, and both come from
 * the same place — a weapon with a dead zone in the middle of its range.
 *
 * <p><b>It gives ground.</b> Everything else reads "cannot fire" as "walk closer". A gun that
 * did that would walk further into the one place it cannot shoot from and stay there. So when
 * something hostile gets inside the minimum, this backs off instead of closing.
 *
 * <p><b>It aims at a place, not a thing.</b> The tile is the order. If the enemy walks off it
 * before the shells arrive, the shells still land there — which is the whole reason artillery
 * can miss, and the reason a player choosing where to put a barrage is making a real decision
 * rather than clicking a target.
 */
public final class BombardOrder implements Order {

    /** How close the gun tries to get before it is content to start firing. */
    private static final float SETTLE = 1.5f;

    /** How often it checks whether something has got in under its guns. Ticks. */
    private static final int THREAT_SCAN = 8;

    /** How near the stand-off tile counts as having got clear. */
    private static final float CLEAR = 1.2f;

    private final int tileX;
    private final int tileY;

    /** Reused so that giving ground allocates nothing. */
    private final int[] standOff = new int[2];

    /**
     * Where the gun is currently backing away to, or -1.
     *
     * <p>Sticky, and it has to be. Scanning for threats every tick is wasteful, but a retreat
     * decided on a scan tick and then forgotten is worse than useless: on the seven ticks in
     * between, the order fell through to its firing branch and called {@code stop()}, which
     * cancelled the move it had just ordered. The gun twitched in place and never went
     * anywhere, which is precisely the behaviour this branch exists to prevent.
     */
    private int retreatX = -1;
    private int retreatY = -1;

    public BombardOrder(int tileX, int tileY) {
        this.tileX = tileX;
        this.tileY = tileY;
    }

    public int tileX() {
        return tileX;
    }

    public int tileY() {
        return tileY;
    }

    @Override
    public boolean update(GameWorld world, Unit unit, float dt) {
        Weapon weapon = unit.weapon();
        if (weapon == null) {
            return true;
        }

        // Anything inside the dead zone is the immediate problem, whatever the orders say.
        if (weapon.hasMinRange() && (world.tick() + unit.id()) % THREAT_SCAN == 0) {
            Entity crowding = world.findNearestEnemy(unit.ownerId(), unit.x(), unit.y(),
                    weapon.minRange(), false);
            if (crowding == null) {
                retreatX = -1;
            } else if (world.standOffTile(unit, crowding.x(), crowding.y(), weapon.minRange(),
                    standOff)) {
                retreatX = standOff[0];
                retreatY = standOff[1];
            } else {
                // Nowhere to give. Stand and be overrun, which is what a battery caught in the
                // open deserves - it should not be able to reverse out of every mistake.
                retreatX = -1;
                world.mover().stop(unit);
                return false;
            }
        }

        if (retreatX >= 0) {
            if (unit.distanceTo(retreatX + 0.5f, retreatY + 0.5f) <= CLEAR) {
                retreatX = -1;
            } else {
                world.mover().moveTowards(world.grid(), unit, retreatX, retreatY, dt);
                return false;
            }
        }

        float gap = unit.distanceTo(tileX + 0.5f, tileY + 0.5f);
        if (gap > weapon.range() - SETTLE) {
            world.mover().moveTowards(world.grid(), unit, tileX, tileY, dt);
            return false;
        }

        world.mover().stop(unit);
        world.tryBombard(unit, tileX, tileY);
        return false;
    }

    @Override
    public String describe() {
        return "Bombarding " + tileX + "," + tileY;
    }
}
