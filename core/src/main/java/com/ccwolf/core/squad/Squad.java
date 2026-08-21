package com.ccwolf.core.squad;

import com.ccwolf.core.entity.UnitType;

/**
 * A body of infantry that moves, fights and breaks as one.
 *
 * <p>A squad owns the thinking its members would otherwise each do for themselves: one route,
 * one enemy scan, one decision about whether to advance or hold. That is what makes hundreds of
 * units per side affordable — the expensive parts of a unit's tick are the parts a squad can do
 * once for eight men.
 *
 * <h2>What a squad is not</h2>
 *
 * <p>It is not where its members live. The units are in {@code GameWorld}'s list like any
 * others, and a squad holds their ids. Keeping a second list of the members themselves would
 * mean two things to keep in step, and one of them would rot the first time something died in
 * an unexpected order.
 *
 * <h2>Slots</h2>
 *
 * <p>Members keep the slot they were given. When one dies the formation thins rather than
 * closing up, which is both what it should look like and cheaper than shuffling everyone
 * sideways every time somebody is shot. A squad only re-forms once it is badly enough reduced
 * that the gaps are worse than the shuffle.
 */
public final class Squad {

    /** Below this fraction of starting strength, the survivors close up the gaps. */
    private static final float REFORM_THRESHOLD = 0.6f;

    private final int id;
    private int ownerId;
    private final UnitType type;

    /** Member ids by slot; -1 for a slot whose occupant is dead. */
    private final int[] memberIds;
    private final int initialStrength;
    private int strength;

    private float anchorX;
    private float anchorY;
    /** Radians, 0 being east — the direction the formation is laid out along. */
    private float heading;

    private Formation formation = Formation.WEDGE;

    private SquadOrder order = SquadOrder.HOLD;

    /** Where the squad has been sent, in tiles. Meaningless unless the order uses it. */
    private int destTileX = -1;
    private int destTileY = -1;

    /** The specific thing an ATTACK order is about, or -1. */
    private int orderTargetId = -1;

    /**
     * The route the anchor is walking, packed tiles, and where along it we are.
     *
     * <p>One route for the whole squad. This is the entire reason a squad is cheaper than the
     * men in it: eight members following an anchor cost one search between them.
     */
    private int[] path;
    private int pathIndex;
    private int pathDestX = -1;
    private int pathDestY = -1;

    /**
     * How long the squad has been standing still waiting for stragglers.
     *
     * <p>Exists because waiting for cohesion has to be able to give up. A member whose slot
     * falls inside a building can never reach it, and without a limit the squad waits for him
     * forever — which is exactly what happened: every AI squad sat at its factory door for the
     * whole match, three waypoints into a fifty-waypoint route, while the credits piled up.
     */
    private int waitTicks;

    /** The one target the whole squad is working on, or -1. */
    private int engagedTargetId = -1;

    /**
     * The squad's nerve, 0 to 100.
     *
     * <p>Held by the squad rather than by the men, because breaking is something a body of
     * troops does together. One frightened rifleman is a detail; a section that has stopped
     * believing it can hold is a hole in the line.
     */
    private int morale = 100;

    /** True while the squad is running. Set by the simulation, cleared when it rallies. */
    private boolean broken;

    /** Tick at which a broken squad may pull itself together. */
    private int rallyAtTick;

    /**
     * When this squad last lost somebody.
     *
     * <p>Needed because losing men is the plainest form of being in contact, and the survivors
     * may show no sign of it: a sniper that kills with one shot leaves nobody wounded and
     * nobody who has fired back, so a squad being picked apart from cover it cannot see looked
     * to the morale rules exactly like a squad at rest.
     */
    private int lastCasualtyTick = -1000;

    public Squad(int id, int ownerId, UnitType type, int[] memberIds, float anchorX,
            float anchorY) {
        this.id = id;
        this.ownerId = ownerId;
        this.type = type;
        this.memberIds = memberIds.clone();
        this.initialStrength = memberIds.length;
        this.strength = memberIds.length;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
    }

    public int id() {
        return id;
    }

    public int ownerId() {
        return ownerId;
    }

    public void setOwnerId(int value) {
        this.ownerId = value;
    }

    public UnitType type() {
        return type;
    }

    public int slotCount() {
        return memberIds.length;
    }

    /** The unit in a slot, or -1 if that slot's occupant is gone. */
    public int memberAt(int slot) {
        return slot >= 0 && slot < memberIds.length ? memberIds[slot] : -1;
    }

    /** Members still alive. */
    public int strength() {
        return strength;
    }

    public int initialStrength() {
        return initialStrength;
    }

    public int casualties() {
        return initialStrength - strength;
    }

    public boolean isWipedOut() {
        return strength <= 0;
    }

    public float strengthFraction() {
        return initialStrength == 0 ? 0f : strength / (float) initialStrength;
    }

    /**
     * Removes a member.
     *
     * @return true if that emptied the squad
     */
    public int lastCasualtyTick() {
        return lastCasualtyTick;
    }

    public void noteCasualty(int tick) {
        this.lastCasualtyTick = tick;
    }

    public boolean removeMember(int unitId) {
        for (int slot = 0; slot < memberIds.length; slot++) {
            if (memberIds[slot] == unitId) {
                memberIds[slot] = -1;
                strength--;
                break;
            }
        }
        return isWipedOut();
    }

    /**
     * Puts a replacement into the first empty slot.
     *
     * @return the slot taken, or -1 if the squad is already at full strength
     */
    public int addMember(int unitId) {
        for (int slot = 0; slot < memberIds.length; slot++) {
            if (memberIds[slot] < 0) {
                memberIds[slot] = unitId;
                strength++;
                return slot;
            }
        }
        return -1;
    }

    /** Men still owed to bring this squad back to strength, ignoring any already walking. */
    public int shortfall() {
        return initialStrength - strength;
    }

    public boolean contains(int unitId) {
        for (int slot = 0; slot < memberIds.length; slot++) {
            if (memberIds[slot] == unitId) {
                return true;
            }
        }
        return false;
    }

    /** True once losses have opened enough gaps to be worth closing. */
    public boolean shouldReform() {
        return strength > 0 && strengthFraction() < REFORM_THRESHOLD && hasGapBeforeEnd();
    }

    private boolean hasGapBeforeEnd() {
        boolean seenEmpty = false;
        for (int slot = 0; slot < memberIds.length; slot++) {
            if (memberIds[slot] < 0) {
                seenEmpty = true;
            } else if (seenEmpty) {
                return true;
            }
        }
        return false;
    }

    /**
     * Closes the gaps, keeping the survivors in the order they were standing.
     *
     * @return the slot each surviving member moved to, indexed the same as the returned ids
     */
    public void reform() {
        int write = 0;
        for (int slot = 0; slot < memberIds.length; slot++) {
            if (memberIds[slot] >= 0) {
                memberIds[write++] = memberIds[slot];
            }
        }
        for (int slot = write; slot < memberIds.length; slot++) {
            memberIds[slot] = -1;
        }
    }

    /** The slot a member currently occupies, or -1. */
    public int slotOf(int unitId) {
        for (int slot = 0; slot < memberIds.length; slot++) {
            if (memberIds[slot] == unitId) {
                return slot;
            }
        }
        return -1;
    }

    public float anchorX() {
        return anchorX;
    }

    public float anchorY() {
        return anchorY;
    }

    public void setAnchor(float x, float y) {
        this.anchorX = x;
        this.anchorY = y;
    }

    public float heading() {
        return heading;
    }

    public void setHeading(float radians) {
        this.heading = radians;
    }

    public Formation formation() {
        return formation;
    }

    public void setFormation(Formation value) {
        this.formation = value;
    }

    public SquadOrder order() {
        return order;
    }

    public int destTileX() {
        return destTileX;
    }

    public int destTileY() {
        return destTileY;
    }

    public int orderTargetId() {
        return orderTargetId;
    }

    /** Sends the squad somewhere, discarding whatever route it was on. */
    public void setDestination(SquadOrder order, int tileX, int tileY) {
        this.order = order;
        this.destTileX = tileX;
        this.destTileY = tileY;
        this.orderTargetId = -1;
        clearPath();
    }

    public void setAttackTarget(int targetId) {
        this.order = SquadOrder.ATTACK;
        this.orderTargetId = targetId;
        this.destTileX = -1;
        this.destTileY = -1;
        clearPath();
    }

    /**
     * Reached the ground it was sent to dig.
     *
     * <p>Unlike arriving on a move order, this does not put the squad back to HOLD: standing on
     * the spot is not the end of an entrench order, it is the start of it. What is dropped is
     * the destination, so that nothing tries to path to a tile the squad is already on — an
     * arrived squad with a live destination it can never satisfy is a pathfinding search every
     * tick, for as long as it holds the position.
     */
    public void arrivedToDig() {
        this.destTileX = -1;
        this.destTileY = -1;
        clearPath();
    }

    public void hold() {
        this.order = SquadOrder.HOLD;
        this.orderTargetId = -1;
        clearPath();
    }

    public int[] path() {
        return path;
    }

    public int pathIndex() {
        return pathIndex;
    }

    public void advancePath() {
        pathIndex++;
    }

    public boolean pathComplete() {
        return path == null || pathIndex >= path.length;
    }

    public boolean hasPathTo(int tileX, int tileY) {
        return path != null && pathDestX == tileX && pathDestY == tileY
                && pathIndex < path.length;
    }

    public void setPath(int[] packedTiles, int tileX, int tileY) {
        this.path = packedTiles;
        this.pathIndex = 0;
        this.pathDestX = tileX;
        this.pathDestY = tileY;
    }

    public void clearPath() {
        this.path = null;
        this.pathIndex = 0;
        this.pathDestX = -1;
        this.pathDestY = -1;
    }

    public float anchorDistanceTo(float x, float y) {
        float dx = x - anchorX;
        float dy = y - anchorY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public int anchorTileX() {
        return (int) anchorX;
    }

    public int anchorTileY() {
        return (int) anchorY;
    }

    /** @return how many consecutive ticks the squad has been held up */
    public int noteWaiting() {
        return ++waitTicks;
    }

    public void clearWaiting() {
        waitTicks = 0;
    }

    public int engagedTargetId() {
        return engagedTargetId;
    }

    public void setEngagedTargetId(int value) {
        this.engagedTargetId = value;
    }

    public int morale() {
        return morale;
    }

    public void setMorale(int value) {
        this.morale = Math.max(0, Math.min(100, value));
    }

    public void changeMorale(int delta) {
        setMorale(morale + delta);
    }

    public boolean isBroken() {
        return broken;
    }

    public int rallyAtTick() {
        return rallyAtTick;
    }

    /** The squad's nerve has gone. It runs until at least {@code rallyAtTick}. */
    public void breakAt(int rallyAtTick) {
        this.broken = true;
        this.rallyAtTick = rallyAtTick;
        this.morale = 0;
        // A broken squad has no orders of its own; running is the order.
        this.order = SquadOrder.HOLD;
        clearPath();
        this.engagedTargetId = -1;
    }

    /** Back in hand, but shaken: it rallies well short of full confidence. */
    public void rally(int startingMorale) {
        this.broken = false;
        this.morale = Math.max(0, Math.min(100, startingMorale));
    }

    /**
     * Where a slot should stand, given the anchor and heading.
     *
     * <p><b>StrictMath, not Math.</b> This decides where units actually stand, so it reaches the
     * simulation state and the determinism digest with it. {@code Math.cos} and {@code Math.sin}
     * are specified only to within one unit in the last place, which means two correct JVMs may
     * disagree about them and two correct machines may then disagree about where a squad is
     * standing. {@code StrictMath} is specified exactly and reproduces everywhere. The rule is
     * in StateDigest's contract: nothing fed by a loosely specified function may reach the
     * digest. Rendering may use the fast versions; this may not.
     *
     * @param out a two-element array the position is written into, to avoid allocating
     */
    public void slotPosition(int slot, float[] out) {
        float forward = formation.offsetX(slot);
        float across = formation.offsetY(slot);
        float cos = (float) StrictMath.cos(heading);
        float sin = (float) StrictMath.sin(heading);
        out[0] = anchorX + forward * cos - across * sin;
        out[1] = anchorY + forward * sin + across * cos;
    }
}
