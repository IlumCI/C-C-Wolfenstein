package com.ccwolf.core.order;

import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.squad.Squad;

/**
 * Walk to a squad and fall in with it.
 *
 * <p>What a replacement does. Reinforcements are trained at the barracks like anything else and
 * then have to get to the front under their own steam, which is the point: a squad holding a
 * line a long way from home stays under strength for as long as the walk takes, and a player
 * who lets one get badly worn down pays for it in time as well as credits.
 */
public final class JoinSquadOrder implements Order {

    /** Close enough to the squad's anchor to fall in. */
    private static final float JOIN_DISTANCE = 2.5f;

    private final int squadId;

    public JoinSquadOrder(int squadId) {
        this.squadId = squadId;
    }

    public int squadId() {
        return squadId;
    }

    @Override
    public boolean update(GameWorld world, Unit unit, float dt) {
        Squad squad = world.squads().byId(squadId);
        if (squad == null || squad.isWipedOut()) {
            // Nothing left to join - the squad was wiped out while this man was walking. He
            // stays where he is as an individual rather than vanishing.
            world.mover().stop(unit);
            return true;
        }
        if (squad.ownerId() != unit.ownerId() || squad.type() != unit.type()) {
            world.mover().stop(unit);
            return true;
        }

        if (unit.distanceTo(squad.anchorX(), squad.anchorY()) <= JOIN_DISTANCE) {
            return world.attachToSquad(squad, unit);
        }

        world.mover().moveTowards(world.grid(), unit, squad.anchorTileX(), squad.anchorTileY(),
                dt);
        return false;
    }

    @Override
    public String describe() {
        return "Reinforcing squad #" + squadId;
    }
}
