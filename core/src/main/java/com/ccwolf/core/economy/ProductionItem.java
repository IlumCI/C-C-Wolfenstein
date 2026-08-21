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
    private int progressTicks;

    public static ProductionItem forUnit(UnitType type) {
        return new ProductionItem(type, null, type.cost(), type.buildTicks());
    }

    public static ProductionItem forBuilding(BuildingType type) {
        return new ProductionItem(null, type, type.cost(), type.buildTicks());
    }

    private ProductionItem(UnitType unitType, BuildingType buildingType, int cost, int totalTicks) {
        this.unitType = unitType;
        this.buildingType = buildingType;
        this.cost = cost;
        this.totalTicks = Math.max(1, totalTicks);
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
        return unitType != null ? unitType.displayName() : buildingType.displayName();
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
