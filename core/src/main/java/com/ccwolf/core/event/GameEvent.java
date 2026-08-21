package com.ccwolf.core.event;

/**
 * Something the presentation layer may want to react to — a shot to draw a tracer for, a
 * kill to play an explosion on, a production line finishing.
 *
 * <p>Keeping these as plain data (rather than callbacks) is what lets {@code core} stay free
 * of any Android dependency: the renderer drains the queue each frame and does as it likes.
 */
public final class GameEvent {

    public enum Type {
        SHOT_FIRED,
        ENTITY_DESTROYED,
        UNIT_TRAINED,
        BUILDING_STARTED,
        BUILDING_COMPLETED,
        ORE_DELIVERED,
        UNDER_ATTACK,
        INSUFFICIENT_FUNDS,
        PLACEMENT_READY,
        PLAYER_DEFEATED
    }

    private final Type type;
    private final int ownerId;
    private final int entityId;
    private final float x;
    private final float y;
    private final float toX;
    private final float toY;
    private final int amount;

    public GameEvent(Type type, int ownerId, int entityId, float x, float y, float toX, float toY,
                     int amount) {
        this.type = type;
        this.ownerId = ownerId;
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.toX = toX;
        this.toY = toY;
        this.amount = amount;
    }

    public static GameEvent at(Type type, int ownerId, int entityId, float x, float y) {
        return new GameEvent(type, ownerId, entityId, x, y, x, y, 0);
    }

    public static GameEvent shot(int ownerId, int shooterId, float fromX, float fromY, float toX,
                                 float toY, int damage) {
        return new GameEvent(Type.SHOT_FIRED, ownerId, shooterId, fromX, fromY, toX, toY, damage);
    }

    public Type type() {
        return type;
    }

    public int ownerId() {
        return ownerId;
    }

    public int entityId() {
        return entityId;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float toX() {
        return toX;
    }

    public float toY() {
        return toY;
    }

    /** Damage dealt, credits delivered, or 0 — depends on the event type. */
    public int amount() {
        return amount;
    }
}
