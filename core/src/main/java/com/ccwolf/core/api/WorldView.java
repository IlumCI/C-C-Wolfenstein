package com.ccwolf.core.api;

import com.ccwolf.core.economy.ProductionItem;
import com.ccwolf.core.economy.ProductionQueue;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Doctrine;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.fog.FogGrid;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.sim.Player;
import com.ccwolf.core.squad.Squad;
import java.util.ArrayList;
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

    private final List<Integer> selectedSquadIds = new ArrayList<Integer>();

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

    /** How this side fights, or null if it picked nothing. */
    public Doctrine doctrine() {
        return player().doctrine();
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
    // --- squads ---------------------------------------------------------------------------

    /** Every squad the viewing player owns, in creation order. */
    public List<Squad> mySquads() {
        List<Squad> out = new ArrayList<Squad>();
        List<Squad> all = world.squads().all();
        for (int i = 0; i < all.size(); i++) {
            Squad squad = all.get(i);
            if (squad.ownerId() == playerId && !squad.isWipedOut()) {
                out.add(squad);
            }
        }
        return out;
    }

    public Squad squad(int squadId) {
        return world.squads().byId(squadId);
    }

    /** The squad a unit belongs to, or null if it fights alone. */
    public Squad squadOf(Unit unit) {
        return world.squadOf(unit);
    }

    /** Where a squad member should be standing, for drawing its place in the line. */
    public void slotPosition(Squad squad, int slot, float[] out) {
        squad.slotPosition(slot, out);
    }

    /**
     * Whether a unit is part of a squad the interface currently has selected.
     *
     * <p>Set by the interface each frame rather than derived here: the view knows what exists,
     * not what the player has picked up.
     */
    public boolean isInSelectedSquad(Unit unit) {
        return unit != null && unit.isInSquad() && selectedSquadIds.contains(
                Integer.valueOf(unit.squadId()));
    }

    /** Told to the view by the interface so the renderer can ask a simple question. */
    public void setSelectedSquads(List<Integer> squadIds) {
        selectedSquadIds.clear();
        selectedSquadIds.addAll(squadIds);
    }

    /** How much fight this player's army has left, 0 when spent and 1 when fresh. */
    public float stamina() {
        return world.player(playerId).stamina() / 100f;
    }

    public boolean isDiscovered(Entity e) {
        if (e == null) {
            return false;
        }
        if (isMine(e)) {
            return true;
        }
        // Concealment beats fog: a marksman lying up in ground you can see is still not there
        // as far as you are concerned.
        if (!e.isBuilding() && ((Unit) e).isConcealed(world.tick())) {
            return false;
        }
        if (!world.isFogEnabled()) {
            return true;
        }
        FogGrid fog = world.fogFor(playerId);
        return e.isBuilding() ? fog.isExplored(e.tileX(), e.tileY())
                : fog.isVisible(e.tileX(), e.tileY());
    }

    /** True while one of our own stealthy units is hidden, so the interface can dim it. */
    public boolean isHiddenAlly(Entity e) {
        return isMine(e) && !e.isBuilding() && ((Unit) e).isConcealed(world.tick());
    }

    /** True while sabotage has this entity switched off. */
    public boolean isDisabled(Entity e) {
        return e != null && e.isDisabled(world.tick());
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

    /**
     * Who holds this ground, from our side: positive is ours, negative is theirs.
     *
     * <p>Surfaced as a question rather than by handing out the grid, matching {@code isVisible}
     * and {@code canObserve}. The renderer has no business reaching through {@code world()} for
     * this — an overlay is exactly the sort of thing that would casually punch through the seam
     * and start reading simulation internals per frame.
     */
    public float control(int tileX, int tileY) {
        return world.control(playerId, tileX, tileY);
    }

    /** Cell dimensions of the control field, for anything that wants to walk it. */
    public int controlCellsAcross() {
        return world.controlCellsAcross();
    }

    public int controlCellsDown() {
        return world.controlCellsDown();
    }

    public float controlAtCell(int cellX, int cellY) {
        return world.controlAtCell(playerId, cellX, cellY);
    }

    public int controlVersion() {
        return world.controlVersion();
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
