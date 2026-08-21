package com.ccwolf.core.order;

import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.squad.Squad;

/**
 * Stand where the squad says, and shoot what the squad is shooting.
 *
 * <p>Deliberately thin. All the thinking — where the squad is going, what it has found, whether
 * to advance or hold — is done once by the squad; a member only reads its slot and walks to it.
 * That is the whole reason a squad is cheaper than the men in it: the expensive parts of a
 * unit's tick are exactly the parts that can be done once for eight.
 *
 * <p>This order is also the exemption that makes break-up work. Any <em>other</em> order given
 * to a squad member takes it out of the squad (see {@code GameWorld.issueOrder}), so a player
 * who tells one man to do something has, by saying so, made him an individual.
 */
public final class SquadMemberOrder implements Order {

    /**
     * How far behind its slot a member may fall before it needs a real route.
     *
     * <p>Steering straight at a slot walks into walls. Usually that does not matter, because
     * the slot is a few tiles from an anchor that is already following a proper path — but a
     * member shoved into a pocket by a building can sit there forever. Past this distance it
     * gives up on steering and paths like anything else.
     */
    private static final float SLOT_LEASH = 6f;

    private final int squadId;

    /** Reused so that moving a squad allocates nothing. */
    private final float[] slot = new float[2];

    public SquadMemberOrder(int squadId) {
        this.squadId = squadId;
    }

    public int squadId() {
        return squadId;
    }

    @Override
    public boolean update(GameWorld world, Unit unit, float dt) {
        Squad squad = world.squads().byId(squadId);
        if (squad == null || !unit.isInSquad() || unit.squadId() != squadId) {
            // The squad is gone or this unit has left it; it is on its own now.
            world.mover().stop(unit);
            return true;
        }

        // Shoot what the squad is shooting. The squad did the looking; this is just the
        // trigger pull, which is the only part that has to happen per man.
        Entity target = squad.engagedTargetId() >= 0
                ? world.entity(squad.engagedTargetId()) : null;
        if (target != null && target.isAlive() && world.inWeaponRange(unit, target)) {
            world.mover().stop(unit);
            unit.faceToward(target.x(), target.y());
            world.tryAttack(unit, target);
            return false;
        }

        squad.slotPosition(unit.squadSlot(), slot);
        float slotX = slot[0];
        float slotY = slot[1];

        if (unit.distanceTo(slotX, slotY) > SLOT_LEASH) {
            // Too far adrift to steer back; walk a real route to the slot's tile.
            world.mover().moveTowards(world.grid(), unit, (int) slotX, (int) slotY, dt);
            return false;
        }

        world.mover().steerTowards(world.grid(), unit, slotX, slotY, dt);
        return false;
    }

    @Override
    public String describe() {
        return "Formed up in squad #" + squadId;
    }
}
