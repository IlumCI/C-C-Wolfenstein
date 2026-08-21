package com.ccwolf.core.sim;

import com.ccwolf.core.combat.Weapon;
import com.ccwolf.core.economy.ProductionItem;
import com.ccwolf.core.economy.ProductionQueue;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.event.GameEvent;
import com.ccwolf.core.fog.FogGrid;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.core.order.HarvestOrder;
import com.ccwolf.core.order.MoveOrder;
import com.ccwolf.core.order.Order;
import com.ccwolf.core.path.AStar;
import com.ccwolf.core.path.Mover;
import com.ccwolf.core.path.OccupancyGrid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The authoritative game simulation: entities, orders, combat, economy, production, fog and
 * the victory check. Advances in fixed {@value #TICKS_PER_SECOND} Hz steps so behaviour does
 * not change with frame rate, and contains no Android types at all — the renderer reads state
 * out of here, and input pushes orders in.
 */
public final class GameWorld {

    public static final int TICKS_PER_SECOND = 20;
    public static final float TICK_SECONDS = 1f / TICKS_PER_SECOND;

    /** Credits both sides start a skirmish with. */
    public static final int STARTING_CREDITS = 5000;

    /** How often (in ticks) fog is recomputed. Cheap enough at 4 Hz, invisible to the eye. */
    private static final int FOG_INTERVAL = 5;

    /** How often uranium seams creep back, in ticks. */
    private static final int ORE_REGROW_INTERVAL = 100;

    /** Uranium added to each seam per regrowth pass. */
    private static final int ORE_REGROW_AMOUNT = 3;

    /** Ticks between repair instalments. */
    private static final int REPAIR_INTERVAL = 4;

    /** Hit points mended per instalment. */
    private static final int REPAIR_HP_PER_STEP = 12;

    /** Repairing a structure from scrap costs this fraction of building it new. */
    private static final float REPAIR_COST_FACTOR = 0.5f;

    /** How often an idle armed unit looks around for something to shoot. */
    private static final int ACQUIRE_INTERVAL = 8;

    private final TileMap map;
    private final OccupancyGrid grid;
    private final Mover mover = new Mover();
    private final Random random;
    private final SpatialIndex spatialIndex;

    private final List<Player> players = new ArrayList<Player>();
    private final Map<Integer, Entity> entitiesById = new HashMap<Integer, Entity>();
    private final List<Unit> units = new ArrayList<Unit>();
    private final List<Building> buildings = new ArrayList<Building>();
    private final List<FogGrid> fogGrids = new ArrayList<FogGrid>();
    private final List<GameEvent> events = new ArrayList<GameEvent>();

    private final List<Unit> queryScratch = new ArrayList<Unit>();

    private int nextEntityId = 1;
    private int tick;
    private boolean gameOver;
    private int winnerId = -1;
    private boolean fogEnabled = true;

    public GameWorld(TileMap map, long seed) {
        this.map = map;
        this.grid = new OccupancyGrid(map);
        this.random = new Random(seed);
        this.spatialIndex = new SpatialIndex(map.width(), map.height());
    }

    // --- world access ---------------------------------------------------------------------

    public TileMap map() {
        return map;
    }

    public OccupancyGrid grid() {
        return grid;
    }

    public Mover mover() {
        return mover;
    }

    public Random random() {
        return random;
    }

    public int tick() {
        return tick;
    }

    public float elapsedSeconds() {
        return tick * TICK_SECONDS;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    /** Player id of the winner, or -1 while the match is still running. */
    public int winnerId() {
        return winnerId;
    }

    public boolean isFogEnabled() {
        return fogEnabled;
    }

    public void setFogEnabled(boolean enabled) {
        this.fogEnabled = enabled;
        if (!enabled) {
            for (int i = 0; i < fogGrids.size(); i++) {
                fogGrids.get(i).revealAll();
            }
        }
    }

    public List<Player> players() {
        return Collections.unmodifiableList(players);
    }

    public Player player(int id) {
        return players.get(id);
    }

    public FogGrid fogFor(int playerId) {
        return fogGrids.get(playerId);
    }

    public List<Unit> units() {
        return Collections.unmodifiableList(units);
    }

    public List<Building> buildings() {
        return Collections.unmodifiableList(buildings);
    }

    public Entity entity(int id) {
        return entitiesById.get(Integer.valueOf(id));
    }

    /** Two-sided for now: anyone who is not you is hostile. */
    public boolean areEnemies(int ownerA, int ownerB) {
        return ownerA != ownerB;
    }

    // --- setup ----------------------------------------------------------------------------

    public Player addPlayer(Faction faction, boolean ai, String name) {
        Player p = new Player(players.size(), faction, ai, name, STARTING_CREDITS);
        players.add(p);
        fogGrids.add(new FogGrid(map.width(), map.height()));
        return p;
    }

    /**
     * Places a starting base: command post, a generator, a refinery and one harvester.
     * Used by the skirmish setup and by the headless harness.
     */
    public void createStartingBase(int playerId, int tileX, int tileY) {
        Building post = placeBuilding(playerId, BuildingType.COMMAND_POST, tileX, tileY, true);
        placeBuilding(playerId, BuildingType.GENERATOR, tileX + 4, tileY, true);
        Building refinery = placeBuilding(playerId, BuildingType.REFINERY, tileX, tileY + 4, true);
        if (post != null) {
            spawnUnitNear(playerId, UnitType.HARVESTER, tileX + 2, tileY + 7);
        }
        if (refinery != null) {
            Unit harvester = spawnUnitNear(playerId, UnitType.HARVESTER, tileX + 4, tileY + 6);
            if (harvester != null) {
                harvester.setOrder(new HarvestOrder());
            }
        }
        for (Unit u : units) {
            if (u.ownerId() == playerId && u.type().isHarvester() && u.isIdle()) {
                u.setOrder(new HarvestOrder());
            }
        }
    }

    public Unit spawnUnit(int ownerId, UnitType type, float x, float y) {
        Unit unit = new Unit(nextEntityId++, ownerId, type, x, y);
        units.add(unit);
        entitiesById.put(Integer.valueOf(unit.id()), unit);
        return unit;
    }

    /**
     * Spawns at the nearest tile to the requested one that is both walkable and not already
     * crowded.
     *
     * <p>Spawning several units on the exact same tile is what turns a factory exit into a
     * gridlocked scrum: perfectly stacked units have no separation direction to resolve and
     * every one of them ends up wedged. So occupied tiles are skipped here rather than left
     * for the steering to sort out.
     *
     * @return the new unit, or null if there is nowhere to put it right now
     */
    public Unit spawnUnitNear(int ownerId, UnitType type, int tileX, int tileY) {
        for (int r = 0; r <= 8; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (r > 0 && Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue;
                    }
                    int tx = tileX + dx;
                    int ty = tileY + dy;
                    if (grid.isBlocked(tx, ty) || isTileCrowded(tx, ty)) {
                        continue;
                    }
                    return spawnUnit(ownerId, type, tx + 0.5f, ty + 0.5f);
                }
            }
        }
        return null;
    }

    /**
     * True if a unit is already sitting on this tile.
     *
     * <p>Scans the unit list directly rather than the spatial index: the index is only
     * rebuilt at the start of a tick, and several units can be spawned between ticks.
     */
    public boolean isTileCrowded(int tileX, int tileY) {
        for (int i = 0; i < units.size(); i++) {
            Unit u = units.get(i);
            if (u.isAlive() && u.tileX() == tileX && u.tileY() == tileY) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts a structure on the map.
     *
     * @return the new structure, or null if the footprint is not clear
     */
    public Building placeBuilding(int ownerId, BuildingType type, int tileX, int tileY,
                                  boolean complete) {
        if (!grid.canPlace(type, tileX, tileY)) {
            return null;
        }
        Building b = new Building(nextEntityId++, ownerId, type, tileX, tileY, complete);
        buildings.add(b);
        entitiesById.put(Integer.valueOf(b.id()), b);
        grid.addBuilding(b);
        evictUnitsFrom(b);
        events.add(GameEvent.at(complete ? GameEvent.Type.BUILDING_COMPLETED
                : GameEvent.Type.BUILDING_STARTED, ownerId, b.id(), b.x(), b.y()));
        return b;
    }

    /** Shoves any unit standing where a new structure just went up out onto free ground. */
    private void evictUnitsFrom(Building b) {
        for (int i = 0; i < units.size(); i++) {
            Unit u = units.get(i);
            if (!u.isAlive() || !b.covers(u.tileX(), u.tileY())) {
                continue;
            }
            int packed = grid.nearestFreeTile(u.tileX(), u.tileY(), 6);
            if (packed >= 0) {
                u.setPosition(AStar.packX(packed) + 0.5f, AStar.packY(packed) + 0.5f);
                u.clearPath();
            }
        }
    }

    /**
     * True if a structure of this type would leave a walkable one-tile border around itself.
     * Used by the AI so it does not brick its own base shut; players may still build tightly.
     */
    public boolean hasBuildClearance(BuildingType type, int tileX, int tileY) {
        for (int ty = tileY - 1; ty <= tileY + type.tilesHigh(); ty++) {
            for (int tx = tileX - 1; tx <= tileX + type.tilesWide(); tx++) {
                boolean insideFootprint = tx >= tileX && ty >= tileY
                        && tx < tileX + type.tilesWide() && ty < tileY + type.tilesHigh();
                if (insideFootprint) {
                    continue;
                }
                if (grid.structureAt(tx, ty) >= 0) {
                    return false;
                }
            }
        }
        return true;
    }

    // --- orders ---------------------------------------------------------------------------

    /** Replaces a unit's orders, ignoring units that are not the issuing player's. */
    public void issueOrder(int playerId, Unit unit, Order order) {
        if (unit == null || unit.ownerId() != playerId || !unit.isAlive()) {
            return;
        }
        unit.setOrder(order);
    }

    public void queueOrder(int playerId, Unit unit, Order order) {
        if (unit == null || unit.ownerId() != playerId || !unit.isAlive()) {
            return;
        }
        unit.queueOrder(order);
    }

    // --- production -----------------------------------------------------------------------

    /** True if the player has a completed structure of this type. */
    public boolean hasCompletedBuilding(int playerId, BuildingType type) {
        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            if (b.ownerId() == playerId && b.type() == type && b.isOperational()) {
                return true;
            }
        }
        return false;
    }

    public Building findBuilding(int playerId, BuildingType type) {
        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            if (b.ownerId() == playerId && b.type() == type && b.isOperational()) {
                return b;
            }
        }
        return null;
    }

    /** Whether the tech and structure requirements for a unit are met right now. */
    public boolean canProduce(int playerId, UnitType type) {
        Player p = player(playerId);
        if (!type.availableTo(p.faction())) {
            return false;
        }
        if (!hasCompletedBuilding(playerId, type.producedBy())) {
            return false;
        }
        return type.prerequisite() == null || hasCompletedBuilding(playerId, type.prerequisite());
    }

    public boolean canProduce(int playerId, BuildingType type) {
        if (!hasCompletedBuilding(playerId, BuildingType.COMMAND_POST)) {
            return false;
        }
        return type.prerequisite() == null || hasCompletedBuilding(playerId, type.prerequisite());
    }

    /**
     * Queues a unit. Cost is taken immediately and refunded in full on cancellation.
     *
     * @return true if it was queued
     */
    public boolean enqueueUnit(int playerId, UnitType type) {
        Player p = player(playerId);
        if (!canProduce(playerId, type)) {
            return false;
        }
        ProductionQueue queue = p.queueFor(type.producedBy());
        if (queue.isFull()) {
            return false;
        }
        if (!p.spend(type.cost())) {
            events.add(GameEvent.at(GameEvent.Type.INSUFFICIENT_FUNDS, playerId, -1, 0, 0));
            return false;
        }
        queue.add(ProductionItem.forUnit(type));
        return true;
    }

    /** Queues a structure. When it finishes it waits to be placed with {@link #placeQueued}. */
    public boolean enqueueBuilding(int playerId, BuildingType type) {
        Player p = player(playerId);
        if (!canProduce(playerId, type)) {
            return false;
        }
        ProductionQueue queue = p.structureQueue();
        if (queue.isFull()) {
            return false;
        }
        if (!p.spend(type.cost())) {
            events.add(GameEvent.at(GameEvent.Type.INSUFFICIENT_FUNDS, playerId, -1, 0, 0));
            return false;
        }
        queue.add(ProductionItem.forBuilding(type));
        return true;
    }

    /**
     * Sells a structure for part of its cost back.
     *
     * @return the credits refunded, or -1 if the structure was not the player's to sell
     */
    public int sellBuilding(int playerId, int buildingId) {
        Entity e = entity(buildingId);
        if (!(e instanceof Building) || e.ownerId() != playerId || !e.isAlive()) {
            return -1;
        }
        Building b = (Building) e;
        int refund = b.refundValue();
        player(playerId).refund(refund);
        events.add(new GameEvent(GameEvent.Type.BUILDING_SOLD, playerId, b.id(), b.x(), b.y(),
                b.x(), b.y(), refund));
        // kill() rather than damage: a sale is not a kill, and must not credit an attacker.
        b.kill();
        if (b.type().isProducer()) {
            player(playerId).clearPrimaryProducer(b.type());
        }
        return refund;
    }

    /** Turns the repair crews on or off for one structure. */
    public boolean setRepairing(int playerId, int buildingId, boolean repairing) {
        Entity e = entity(buildingId);
        if (!(e instanceof Building) || e.ownerId() != playerId || !e.isAlive()) {
            return false;
        }
        ((Building) e).setRepairing(repairing);
        return true;
    }

    /** Chooses which structure of its kind new units walk out of. */
    public boolean setPrimaryProducer(int playerId, int buildingId) {
        Entity e = entity(buildingId);
        if (!(e instanceof Building) || e.ownerId() != playerId || !e.isOperational()) {
            return false;
        }
        Building b = (Building) e;
        if (!b.type().isProducer()) {
            return false;
        }
        player(playerId).setPrimaryProducer(b.type(), b.id());
        return true;
    }

    /**
     * Why a unit cannot be queued right now, phrased for the player, or null if it can.
     *
     * <p>The HUD used to guess at this and got it wrong for tech prerequisites; there is now
     * exactly one place that decides, and both the button state and its label come from it.
     */
    public String productionBlocker(int playerId, UnitType type) {
        Player p = player(playerId);
        if (!type.availableTo(p.faction())) {
            return "Not available to " + p.faction().displayName();
        }
        if (!hasCompletedBuilding(playerId, type.producedBy())) {
            return "Needs " + type.producedBy().displayName();
        }
        if (type.prerequisite() != null && !hasCompletedBuilding(playerId, type.prerequisite())) {
            return "Needs " + type.prerequisite().displayName();
        }
        if (p.queueFor(type.producedBy()).isFull()) {
            return "Queue full";
        }
        if (!p.canAfford(type.cost())) {
            return "Needs " + type.cost() + " credits";
        }
        return null;
    }

    /** Why a structure cannot be queued right now, or null if it can. */
    public String productionBlocker(int playerId, BuildingType type) {
        Player p = player(playerId);
        if (!hasCompletedBuilding(playerId, BuildingType.COMMAND_POST)) {
            return "Needs " + BuildingType.COMMAND_POST.displayName();
        }
        if (type.prerequisite() != null && !hasCompletedBuilding(playerId, type.prerequisite())) {
            return "Needs " + type.prerequisite().displayName();
        }
        if (p.structureQueue().isFull()) {
            return "Queue full";
        }
        if (!p.canAfford(type.cost())) {
            return "Needs " + type.cost() + " credits";
        }
        return null;
    }

    /** Cancels the newest entry on a line and refunds it. */
    public void cancelLast(int playerId, ProductionQueue queue) {
        ProductionItem item = queue.cancelLast();
        if (item != null) {
            player(playerId).refund(item.cost());
        }
    }

    /**
     * Drops a finished structure from the build queue onto the map.
     *
     * @return the placed structure, or null if the site is not clear or nothing is ready
     */
    public Building placeQueued(int playerId, int tileX, int tileY) {
        Player p = player(playerId);
        ProductionQueue queue = p.structureQueue();
        if (!queue.isHeadReady()) {
            return null;
        }
        BuildingType type = queue.head().buildingType();
        if (!isValidPlacement(playerId, type, tileX, tileY)) {
            return null;
        }
        Building placed = placeBuilding(playerId, type, tileX, tileY, true);
        if (placed == null) {
            return null;
        }
        queue.removeHead();
        if (type == BuildingType.REFINERY) {
            // A refinery ships with its harvester, as it should.
            Unit harvester = spawnUnitNear(playerId, UnitType.HARVESTER,
                    tileX + type.tilesWide() / 2, tileY + type.tilesHigh() + 1);
            if (harvester != null) {
                harvester.setOrder(new HarvestOrder());
            }
        }
        return placed;
    }

    /**
     * Placement rules: the footprint must be clear, and it must sit within
     * {@code BUILD_RADIUS} tiles of one of the player's existing structures.
     */
    public boolean isValidPlacement(int playerId, BuildingType type, int tileX, int tileY) {
        if (!grid.canPlace(type, tileX, tileY)) {
            return false;
        }
        float cx = tileX + type.tilesWide() / 2f;
        float cy = tileY + type.tilesHigh() / 2f;
        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            if (b.ownerId() != playerId || !b.isAlive()) {
                continue;
            }
            float dx = Math.abs(b.x() - cx) - b.tilesWide() / 2f;
            float dy = Math.abs(b.y() - cy) - b.tilesHigh() / 2f;
            if (Math.max(dx, dy) <= BUILD_RADIUS) {
                return true;
            }
        }
        return false;
    }

    /** How far from an existing structure a new one may be placed. */
    public static final float BUILD_RADIUS = 6f;

    // --- simulation -----------------------------------------------------------------------

    /** Advances the world by exactly one tick. */
    public void step() {
        if (gameOver) {
            return;
        }
        tick++;

        spatialIndex.rebuild(units);
        updatePower();
        updateProduction();
        updateUnits();
        updateBuildings();
        updateRepairs();
        applySeparation();
        removeDead();
        if (tick % ORE_REGROW_INTERVAL == 0) {
            map.regrowOre(ORE_REGROW_AMOUNT);
        }
        // Also on tick 1: waiting for the first interval leaves the opening frames black.
        if (tick == 1 || tick % FOG_INTERVAL == 0) {
            updateFog();
        }
        checkVictory();
    }

    private void updatePower() {
        int[] produced = new int[players.size()];
        int[] drawn = new int[players.size()];
        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            if (!b.isOperational()) {
                continue;
            }
            produced[b.ownerId()] += b.type().powerProduced();
            drawn[b.ownerId()] += b.type().powerDrawn();
        }
        for (int i = 0; i < players.size(); i++) {
            players.get(i).setPower(produced[i], drawn[i]);
        }

        // Defences are the first thing a brownout takes: production merely slows, but a turret
        // with no juice is a concrete box. This is what makes generators worth bombing.
        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            if (b.type().powerDrawn() <= 0 || b.type().weapon() == null) {
                continue;
            }
            boolean shouldBePowered = !players.get(b.ownerId()).isLowPower();
            if (shouldBePowered != b.isPowered()) {
                b.setPowered(shouldBePowered);
                events.add(GameEvent.at(shouldBePowered ? GameEvent.Type.POWER_RESTORED
                        : GameEvent.Type.POWER_LOST, b.ownerId(), b.id(), b.x(), b.y()));
            }
        }
    }

    private void updateProduction() {
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (p.isDefeated()) {
                continue;
            }
            float rate = p.powerFactor();
            advanceUnitQueue(p, p.infantryQueue(), BuildingType.BARRACKS, rate);
            advanceUnitQueue(p, p.vehicleQueue(), BuildingType.WAR_WORKS, rate);
            advanceStructureQueue(p, rate);
        }
    }

    private void advanceUnitQueue(Player p, ProductionQueue queue, BuildingType producer,
                                  float rate) {
        ProductionItem head = queue.head();
        if (head == null) {
            return;
        }
        Building factory = productionExit(p, producer);
        if (factory == null) {
            // The factory was destroyed mid-build: hold the queue rather than silently eat it.
            return;
        }
        if (!head.isFinished()) {
            head.advance(rate);
            return;
        }
        Unit spawned = spawnUnitNear(p.id(), head.unitType(),
                factory.tileX() + factory.tilesWide() / 2, factory.tileY() + factory.tilesHigh());
        if (spawned == null) {
            return; // Exit blocked; try again next tick.
        }
        queue.removeHead();
        p.noteUnitBuilt();
        events.add(GameEvent.at(GameEvent.Type.UNIT_TRAINED, p.id(), spawned.id(),
                spawned.x(), spawned.y()));
        if (spawned.type().isHarvester()) {
            spawned.setOrder(new HarvestOrder());
        } else {
            spawned.setOrder(new MoveOrder(factory.rallyX(), factory.rallyY()));
        }
    }

    /**
     * Where a finished unit walks out of: the player's chosen primary structure if they set
     * one and it is still standing, otherwise whichever one is.
     */
    private Building productionExit(Player p, BuildingType producer) {
        int primaryId = p.primaryProducer(producer);
        if (primaryId >= 0) {
            Entity e = entity(primaryId);
            if (e instanceof Building && e.isOperational() && ((Building) e).type() == producer) {
                return (Building) e;
            }
            p.clearPrimaryProducer(producer); // It died; stop pointing at a hole in the ground.
        }
        return findBuilding(p.id(), producer);
    }

    private void advanceStructureQueue(Player p, float rate) {
        ProductionItem head = p.structureQueue().head();
        if (head == null) {
            return;
        }
        if (!hasCompletedBuilding(p.id(), BuildingType.COMMAND_POST)) {
            return;
        }
        if (!head.isFinished()) {
            boolean justDone = head.advance(rate);
            if (justDone) {
                events.add(GameEvent.at(GameEvent.Type.PLACEMENT_READY, p.id(), -1, 0, 0));
            }
        }
    }

    private void updateUnits() {
        for (int i = 0; i < units.size(); i++) {
            Unit u = units.get(i);
            if (!u.isAlive()) {
                continue;
            }
            u.tickCooldown();

            Order order = u.currentOrder();
            if (order != null) {
                if (order.update(this, u, TICK_SECONDS)) {
                    u.finishCurrentOrder();
                }
            } else {
                u.setVelocity(0f, 0f);
                if ((tick + u.id()) % ACQUIRE_INTERVAL == 0) {
                    autoDefend(u);
                }
            }
        }
    }

    /** An idle armed unit shoots anything hostile that wanders into range. It does not chase. */
    private void autoDefend(Unit u) {
        Weapon w = u.weapon();
        if (w == null) {
            return;
        }
        Entity target = findNearestEnemy(u.ownerId(), u.x(), u.y(), w.range(), true);
        if (target != null) {
            u.faceToward(target.x(), target.y());
            tryAttack(u, target);
        }
    }

    private void updateBuildings() {
        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            if (!b.isAlive()) {
                continue;
            }
            if (!b.isComplete()) {
                if (b.advanceConstruction(1)) {
                    events.add(GameEvent.at(GameEvent.Type.BUILDING_COMPLETED, b.ownerId(),
                            b.id(), b.x(), b.y()));
                }
                continue;
            }
            b.tickCooldown();
            Weapon w = b.weapon();
            if (w != null && b.weaponReady()) {
                Entity target = findNearestEnemy(b.ownerId(), b.x(), b.y(), w.range(), false);
                if (target != null) {
                    tryAttack(b, target);
                }
            }
        }
    }

    /**
     * Charges for and applies structure repairs. Repairs are paid for in small instalments, so
     * a player who runs out of money mid-repair simply stops mending rather than going into
     * debt or getting the rest for free.
     */
    private void updateRepairs() {
        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            if (!b.isRepairing() || !b.isAlive()) {
                continue;
            }
            if (b.hp() >= b.maxHp()) {
                b.setRepairing(false);
                continue;
            }
            b.setRepairTicks(b.repairTicks() + 1);
            if (b.repairTicks() < REPAIR_INTERVAL) {
                continue;
            }
            b.setRepairTicks(0);

            int missing = b.maxHp() - b.hp();
            int amount = Math.min(REPAIR_HP_PER_STEP, missing);
            int cost = repairCost(b.type(), amount);
            Player owner = player(b.ownerId());
            if (!owner.spend(cost)) {
                b.setRepairing(false);
                if (owner.id() == 0) {
                    events.add(GameEvent.at(GameEvent.Type.INSUFFICIENT_FUNDS, owner.id(),
                            b.id(), b.x(), b.y()));
                }
                continue;
            }
            owner.noteRepairSpend(cost);
            b.heal(amount);
            if (b.hp() >= b.maxHp()) {
                b.setRepairing(false);
            }
        }
    }

    /** What patching up {@code hitPoints} of a structure costs. Never free. */
    public static int repairCost(BuildingType type, int hitPoints) {
        float perHp = type.cost() * REPAIR_COST_FACTOR / Math.max(1, type.maxHp());
        return Math.max(1, Math.round(perHp * hitPoints));
    }

    /**
     * Pushes overlapping units apart. This is what keeps a moving group looking like a group
     * instead of a single stacked sprite, without making units block each other's paths.
     */
    private void applySeparation() {
        for (int i = 0; i < units.size(); i++) {
            Unit a = units.get(i);
            if (!a.isAlive()) {
                continue;
            }
            queryScratch.clear();
            float reach = a.radius() * 2f + 1f;
            spatialIndex.query(a.x(), a.y(), reach, queryScratch);
            float pushX = 0f;
            float pushY = 0f;
            for (int j = 0; j < queryScratch.size(); j++) {
                Unit b = queryScratch.get(j);
                if (b == a || !b.isAlive()) {
                    continue;
                }
                float dx = a.x() - b.x();
                float dy = a.y() - b.y();
                float minDist = a.radius() + b.radius();
                float d2 = dx * dx + dy * dy;
                if (d2 >= minDist * minDist) {
                    continue;
                }
                float d = (float) Math.sqrt(d2);
                if (d < 1e-4f) {
                    // Exactly stacked: shove apart deterministically using ids, not randomness,
                    // so the simulation stays reproducible from a seed.
                    dx = ((a.id() % 2) == 0) ? 0.01f : -0.01f;
                    dy = ((a.id() % 3) == 0) ? 0.01f : -0.01f;
                    d = 0.014f;
                }
                float overlap = (minDist - d) * 0.5f;
                pushX += dx / d * overlap;
                pushY += dy / d * overlap;
            }
            if (pushX != 0f || pushY != 0f) {
                float nx = clamp(a.x() + pushX, map.width());
                float ny = clamp(a.y() + pushY, map.height());
                if (!grid.isBlocked((int) nx, (int) ny)) {
                    a.setPosition(nx, ny);
                } else if (!grid.isBlocked((int) nx, a.tileY())) {
                    // Slide along the wall rather than stopping dead against it.
                    a.setPosition(nx, a.y());
                } else if (!grid.isBlocked(a.tileX(), (int) ny)) {
                    a.setPosition(a.x(), ny);
                }
            }
        }
    }

    private static float clamp(float v, int max) {
        return Math.max(0.01f, Math.min(max - 0.01f, v));
    }

    private void removeDead() {
        for (int i = units.size() - 1; i >= 0; i--) {
            Unit u = units.get(i);
            if (!u.isAlive()) {
                units.remove(i);
                entitiesById.remove(Integer.valueOf(u.id()));
                player(u.ownerId()).noteUnitLost();
            }
        }
        for (int i = buildings.size() - 1; i >= 0; i--) {
            Building b = buildings.get(i);
            if (!b.isAlive()) {
                buildings.remove(i);
                entitiesById.remove(Integer.valueOf(b.id()));
                grid.removeBuilding(b);
                player(b.ownerId()).noteBuildingLost();
            }
        }
    }

    private void updateFog() {
        if (!fogEnabled) {
            return;
        }
        for (int i = 0; i < fogGrids.size(); i++) {
            fogGrids.get(i).beginRefresh();
        }
        for (int i = 0; i < units.size(); i++) {
            Unit u = units.get(i);
            fogGrids.get(u.ownerId()).reveal(u.x(), u.y(), u.sight());
        }
        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            fogGrids.get(b.ownerId()).reveal(b.x(), b.y(), b.sight());
        }
    }

    private void checkVictory() {
        int alivePlayers = 0;
        int lastAlive = -1;
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (p.isDefeated()) {
                continue;
            }
            if (!hasAnythingLeft(p.id())) {
                p.setDefeated(true);
                events.add(GameEvent.at(GameEvent.Type.PLAYER_DEFEATED, p.id(), -1, 0, 0));
                continue;
            }
            alivePlayers++;
            lastAlive = p.id();
        }
        if (alivePlayers <= 1 && players.size() > 1) {
            gameOver = true;
            winnerId = lastAlive;
        }
    }

    private boolean hasAnythingLeft(int playerId) {
        for (int i = 0; i < buildings.size(); i++) {
            if (buildings.get(i).ownerId() == playerId && buildings.get(i).isAlive()) {
                return true;
            }
        }
        for (int i = 0; i < units.size(); i++) {
            if (units.get(i).ownerId() == playerId && units.get(i).isAlive()) {
                return true;
            }
        }
        return false;
    }

    // --- combat helpers -------------------------------------------------------------------

    /**
     * Fires at a target if it is in range and the weapon is off cooldown.
     *
     * @return true if a shot went off
     */
    public boolean tryAttack(Entity attacker, Entity target) {
        Weapon weapon = attacker.weapon();
        if (weapon == null || target == null || !target.isAlive()) {
            return false;
        }
        boolean ready = attacker.isBuilding() ? ((Building) attacker).weaponReady()
                : ((Unit) attacker).weaponReady();
        if (!ready) {
            return false;
        }
        if (!inWeaponRange(attacker, target)) {
            return false;
        }

        int damage = weapon.damageAgainst(target.armor());
        boolean killed = target.applyDamage(damage, attacker.id(), tick);
        if (attacker.isBuilding()) {
            ((Building) attacker).startWeaponCooldown();
        } else {
            ((Unit) attacker).startWeaponCooldown();
        }

        events.add(GameEvent.shot(attacker.ownerId(), attacker.id(), attacker.x(), attacker.y(),
                target.x(), target.y(), damage));
        events.add(GameEvent.at(GameEvent.Type.UNDER_ATTACK, target.ownerId(), target.id(),
                target.x(), target.y()));
        if (killed) {
            events.add(GameEvent.at(GameEvent.Type.ENTITY_DESTROYED, target.ownerId(),
                    target.id(), target.x(), target.y()));
        }
        return true;
    }

    /** Range is measured to the target's edge, so big structures are hittable from outside. */
    public boolean inWeaponRange(Entity attacker, Entity target) {
        Weapon weapon = attacker.weapon();
        if (weapon == null) {
            return false;
        }
        float gap = attacker.distanceTo(target) - target.radius();
        return gap <= weapon.range();
    }

    /**
     * Nearest hostile entity within {@code radius} of a point.
     *
     * @param includeBuildings whether structures are considered; turrets ignore them so they
     *     shoot the thing walking at them rather than a wall in the distance
     */
    public Entity findNearestEnemy(int ownerId, float x, float y, float radius,
                                   boolean includeBuildings) {
        Entity best = null;
        float bestDist = Float.MAX_VALUE;

        queryScratch.clear();
        spatialIndex.query(x, y, radius + 1f, queryScratch);
        for (int i = 0; i < queryScratch.size(); i++) {
            Unit u = queryScratch.get(i);
            if (!u.isAlive() || !areEnemies(ownerId, u.ownerId())) {
                continue;
            }
            float d = u.distanceTo(x, y) - u.radius();
            if (d <= radius && d < bestDist) {
                bestDist = d;
                best = u;
            }
        }
        if (includeBuildings) {
            for (int i = 0; i < buildings.size(); i++) {
                Building b = buildings.get(i);
                if (!b.isAlive() || !areEnemies(ownerId, b.ownerId())) {
                    continue;
                }
                float d = b.distanceTo(x, y) - b.radius();
                if (d <= radius && d < bestDist) {
                    bestDist = d;
                    best = b;
                }
            }
        }
        return best;
    }

    /**
     * Nearest hostile entity anywhere on the map, ignoring fog.
     *
     * <p>{@code preferBuildings} only sets the <em>preference</em>: if the enemy has no
     * structures left, this still returns their surviving units. Without that fallback an
     * army with nothing left to bomb simply stops attacking, and a beaten player's last two
     * harvesters keep the match alive forever.
     *
     * @return the target, or null if the enemy has nothing left at all
     */
    public Entity findNearestEnemyAnywhere(int ownerId, float x, float y,
                                           boolean preferBuildings) {
        Entity building = nearestEnemyBuilding(ownerId, x, y);
        Entity unit = nearestEnemyUnit(ownerId, x, y);
        if (preferBuildings) {
            return building != null ? building : unit;
        }
        return unit != null ? unit : building;
    }

    private Entity nearestEnemyBuilding(int ownerId, float x, float y) {
        Entity best = null;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            if (!b.isAlive() || !areEnemies(ownerId, b.ownerId())) {
                continue;
            }
            float d = b.distanceTo(x, y);
            if (d < bestDist) {
                bestDist = d;
                best = b;
            }
        }
        return best;
    }

    private Entity nearestEnemyUnit(int ownerId, float x, float y) {
        Entity best = null;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < units.size(); i++) {
            Unit u = units.get(i);
            if (!u.isAlive() || !areEnemies(ownerId, u.ownerId())) {
                continue;
            }
            float d = u.distanceTo(x, y);
            if (d < bestDist) {
                bestDist = d;
                best = u;
            }
        }
        return best;
    }

    // --- harvesting helpers ---------------------------------------------------------------

    /** Nearest tile with uranium in it, searched in rings. Returns a packed tile or -1. */
    public int findOreTile(float x, float y, int maxRadius) {
        int cx = (int) x;
        int cy = (int) y;
        if (map.ore(cx, cy) > 0) {
            return AStar.pack(cx, cy);
        }
        for (int r = 1; r <= maxRadius; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue;
                    }
                    int nx = cx + dx;
                    int ny = cy + dy;
                    if (map.ore(nx, ny) > 0 && !grid.isBlocked(nx, ny)) {
                        return AStar.pack(nx, ny);
                    }
                }
            }
        }
        return -1;
    }

    public Building findNearestRefinery(int playerId, float x, float y) {
        Building best = null;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            if (b.ownerId() != playerId || b.type() != BuildingType.REFINERY
                    || !b.isOperational()) {
                continue;
            }
            float d = b.distanceTo(x, y);
            if (d < bestDist) {
                bestDist = d;
                best = b;
            }
        }
        return best;
    }

    /** Called by {@link HarvestOrder} when a harvester docks. */
    public void deliverOre(Unit harvester) {
        int amount = harvester.unloadOre();
        if (amount <= 0) {
            return;
        }
        Player p = player(harvester.ownerId());
        p.deposit(amount);
        events.add(new GameEvent(GameEvent.Type.ORE_DELIVERED, p.id(), harvester.id(),
                harvester.x(), harvester.y(), harvester.x(), harvester.y(), amount));
    }

    // --- events ---------------------------------------------------------------------------

    public void addEvent(GameEvent event) {
        events.add(event);
    }

    /** Hands over everything that happened since the last drain and clears the queue. */
    public List<GameEvent> drainEvents(List<GameEvent> out) {
        out.addAll(events);
        events.clear();
        return out;
    }

    public void clearEvents() {
        events.clear();
    }
}
