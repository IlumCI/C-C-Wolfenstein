package com.ccwolf.core.api;

import com.ccwolf.core.economy.ProductionItem;
import com.ccwolf.core.economy.ProductionQueue;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.fog.FogGrid;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.sim.Player;
import java.util.List;

/**
 * One player's read-only window onto the match: what they own, what they can see, what they
 * can afford and what they are not allowed to build yet.
 *
 * <p>Paired with {@link CommandBus}, this is the whole seam: the interface layer queries
 * through here and mutates through the bus. Nothing here changes game state.
 */
public final class WorldView {

    private final GameWorld world;
    private final int playerId;

    public WorldView(GameWorld world, int playerId) {
        this.world = world;
        this.playerId = playerId;
    }

    // --- identity -------------------------------------------------------------------------

    public int playerId() {
        return playerId;
    }

    public Player player() {
        return world.player(playerId);
    }

    public Faction faction() {
        return player().faction();
    }

    public TileMap map() {
        return world.map();
    }

    public int tick() {
        return world.tick();
    }

    public boolean isGameOver() {
        return world.isGameOver();
    }

    public boolean hasWon() {
        return world.isGameOver() && world.winnerId() == playerId;
    }

    // --- economy --------------------------------------------------------------------------

    public int credits() {
        return player().credits();
    }

    public int powerProduced() {
        return player().powerProduced();
    }

    public int powerDrawn() {
        return player().powerDrawn();
    }

    public boolean isLowPower() {
        return player().isLowPower();
    }

    // --- entities -------------------------------------------------------------------------

    public List<Unit> units() {
        return world.units();
    }

    public List<Building> buildings() {
        return world.buildings();
    }

    public Entity entity(int id) {
        return world.entity(id);
    }

    public boolean isMine(Entity e) {
        return e != null && e.ownerId() == playerId;
    }

    public boolean isHostile(Entity e) {
        return e != null && world.areEnemies(playerId, e.ownerId());
    }

    /**
     * Whether an enemy entity should be drawn at all: units need live vision, structures only
     * need to have been seen once, which is what makes a remembered base persist under fog.
     */
    public boolean isDiscovered(Entity e) {
        if (e == null) {
            return false;
        }
        if (isMine(e) || !world.isFogEnabled()) {
            return true;
        }
        FogGrid fog = world.fogFor(playerId);
        return e.isBuilding() ? fog.isExplored(e.tileX(), e.tileY())
                : fog.isVisible(e.tileX(), e.tileY());
    }

    public boolean isVisible(int tileX, int tileY) {
        return !world.isFogEnabled() || world.fogFor(playerId).isVisible(tileX, tileY);
    }

    public boolean isExplored(int tileX, int tileY) {
        return !world.isFogEnabled() || world.fogFor(playerId).isExplored(tileX, tileY);
    }

    public FogGrid fog() {
        return world.fogFor(playerId);
    }

    public boolean isFogEnabled() {
        return world.isFogEnabled();
    }

    // --- production -----------------------------------------------------------------------

    public ProductionQueue queueFor(BuildingType producer) {
        return player().queueFor(producer);
    }

    public ProductionQueue structureQueue() {
        return player().structureQueue();
    }

    /** Null when the item can be queued; otherwise the reason, ready to display. */
    public String blockerFor(UnitType type) {
        return world.productionBlocker(playerId, type);
    }

    public String blockerFor(BuildingType type) {
        return world.productionBlocker(playerId, type);
    }

    /** The structure sitting finished at the head of the build queue, or null. */
    public BuildingType readyStructure() {
        ProductionQueue queue = structureQueue();
        return queue.isHeadReady() ? queue.head().buildingType() : null;
    }

    public ProductionItem headOf(ProductionQueue queue) {
        return queue.head();
    }

    /** Whether a structure would be legal here, with the tap point as its centre. */
    public boolean canPlaceCentred(BuildingType type, int tileX, int tileY) {
        return world.isValidPlacement(playerId, type, tileX - type.tilesWide() / 2,
                tileY - type.tilesHigh() / 2);
    }

    /** Whether this structure is the chosen exit for units of its kind. */
    public boolean isPrimary(Building b) {
        return b != null && b.type().isProducer()
                && player().primaryProducer(b.type()) == b.id();
    }

    public int repairCostFor(Building b) {
        return GameWorld.repairCost(b.type(), b.maxHp() - b.hp());
    }

    /** Escape hatch for the few places that still need the world itself (the AI, tests). */
    public GameWorld world() {
        return world;
    }
}
