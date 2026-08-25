package com.ccwolf.core.order;

import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.sim.GameWorld;

/**
 * Get to a piece of ground and dig into it.
 *
 * <p>The order does not end. A man who has finished his hole stays in it and shoots what comes
 * at him, which is the point of having dug it: this is how ground stops being somewhere an army
 * passed through and starts being somewhere it holds.
 *
 * <p>The rule that gives the whole mechanic its shape is that <b>a man cannot dig and shoot at
 * the same time</b>, and cannot dig at all with his head down. So earthworks are the reward for
 * getting somewhere first and for winning the firefight once you are there — and a line that
 * arrives under fire never hardens. Nothing else in the order does any work; it is the
 * exclusion that makes trenches a decision rather than an entitlement.
 */
public final class EntrenchOrder implements Order {

    /** How often a dug-in unit looks for something to shoot. Ticks. */
    private static final int SCAN_INTERVAL = 6;

    /** Close enough to the ordered tile to stop walking and start digging. */
    private static final float ARRIVED = 1.2f;

    private final int tileX;
    private final int tileY;
    private int engagedTargetId = -1;

    public EntrenchOrder(int tileX, int tileY) {
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
        if (unit.distanceTo(tileX + 0.5f, tileY + 0.5f) > ARRIVED) {
            // Still on the way. Walk, and do not dig a hole somewhere you are not staying.
            unit.resetDigging();
            world.mover().moveTowards(world.grid(), unit, tileX, tileY, dt);
            return false;
        }

        world.mover().stop(unit);

        Entity target = engagedTargetId >= 0 ? world.entity(engagedTargetId) : null;
        if (target != null && (!target.isAlive() || !world.inWeaponRange(unit, target))) {
            target = null;
            engagedTargetId = -1;
        }
        if (target == null && unit.weapon() != null
                && (world.tick() + unit.id()) % SCAN_INTERVAL == 0) {
            target = world.findNearestEnemy(unit.ownerId(), unit.x(), unit.y(),
                    unit.weapon().range(), true);
            engagedTargetId = target == null ? -1 : target.id();
        }

        if (target != null && target.isAlive() && world.inWeaponRange(unit, target)) {
            unit.faceToward(target.x(), target.y());
            world.tryAttack(unit, target);
            // Shooting, therefore not digging. Whatever was banked stays banked: a man
            // interrupted goes back to the same hole, he does not start a new one.
            return false;
        }

        world.digIn(unit);
        return false;
    }

    @Override
    public String describe() {
        return "Dug in at " + tileX + "," + tileY;
    }
}
