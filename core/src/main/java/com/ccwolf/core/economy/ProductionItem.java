package com.ccwolf.core.economy;

import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.UnitType;

/**
 * One entry in a {@link ProductionQueue}. Holds either a unit type or a structure type —
 * exactly one of the two is non-null.
 */
public final class ProductionItem {

    private final UnitType unitType;
    private final BuildingType buildingType;
    private final int cost;
    private final int totalTicks;
    private final int count;
    private int progressTicks;

    /**
     * A squad this unit should walk off and join, or -1 for the rally point.
     *
     * <p>How reinforcement rides the ordinary production line rather than needing one of its
     * own: a replacement is a normal unit with somewhere specific to be.
     */
    private int joinSquadId = -1;

    public static ProductionItem forUnit(UnitType type) {
        return new ProductionItem(type, null, type.cost(), type.buildTicks(), 1);
    }

    /**
     * A whole squad, trained as one item.
     *
     * <p>Priced and timed per man, so a squad costs and takes what its members would — the
     * saving is in the queue and the tapping, not the economy. Training the men one at a time
     * and grouping them afterwards would mean queueing three hundred times to field an army,
     * which is the thing this exists to avoid.
     */
    public static ProductionItem forSquad(UnitType type, int count) {
        int men = Math.max(1, count);
        // Slightly faster than the same men one after another: a barracks trains a section
        // together. Without this, squads are strictly worse than individuals to produce.
        int ticks = (int) (type.buildTicks() * men * SQUAD_TIME_DISCOUNT);
        return new ProductionItem(type, null, type.cost() * men, ticks, men);
    }

    /** Squads train at this fraction of the time their members would take individually. */
    private static final float SQUAD_TIME_DISCOUNT = 0.75f;

    public static ProductionItem forBuilding(BuildingType type) {
        return new ProductionItem(null, type, type.cost(), type.buildTicks(), 1);
    }

    private ProductionItem(UnitType unitType, BuildingType buildingType, int cost, int totalTicks,
                           int count) {
        this.unitType = unitType;
        this.buildingType = buildingType;
        this.cost = cost;
        this.totalTicks = Math.max(1, totalTicks);
        this.count = count;
    }

    public int joinSquadId() {
        return joinSquadId;
    }

    public void setJoinSquadId(int squadId) {
        this.joinSquadId = squadId;
    }

    /** How many units this item produces. More than one means a squad. */
    public int count() {
        return count;
    }

    public boolean isSquad() {
        return count > 1;
    }

    public UnitType unitType() {
        return unitType;
    }

    public BuildingType buildingType() {
        return buildingType;
    }

    public boolean isUnit() {
        return unitType != null;
    }

    public int cost() {
        return cost;
    }

    public String displayName() {
        if (buildingType != null) {
            return buildingType.displayName();
        }
        return count > 1 ? unitType.displayName() + " Squad" : unitType.displayName();
    }

    public float progress() {
        return Math.min(1f, progressTicks / (float) totalTicks);
    }

    public boolean isFinished() {
        return progressTicks >= totalTicks;
    }

    /** @return true if this tick completed the item */
    public boolean advance(float ticks) {
        if (isFinished()) {
            return false;
        }
        progressTicks += Math.max(1, Math.round(ticks));
        return isFinished();
    }
}
