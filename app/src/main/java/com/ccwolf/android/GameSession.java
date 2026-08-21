package com.ccwolf.android;

import com.ccwolf.android.render.Camera;
import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.event.GameEvent;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.order.AttackMoveOrder;
import com.ccwolf.core.order.AttackOrder;
import com.ccwolf.core.order.HarvestOrder;
import com.ccwolf.core.order.MoveOrder;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.sim.Player;
import com.ccwolf.core.sim.Skirmish;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything the on-screen game needs on top of the simulation: the camera, the player's
 * selection, what they are currently placing, and the short-lived visual effects that shots
 * and explosions leave behind.
 *
 * <p>The simulation itself is stepped here at a fixed rate, decoupled from the frame rate.
 */
public final class GameSession {

    /** Never simulate more than this many ticks in one frame, or a stall becomes a freeze. */
    private static final int MAX_CATCHUP_TICKS = 6;

    /** A tracer stays on screen for this long. */
    private static final long TRACER_MS = 90;

    /** An explosion animates over this long. */
    private static final long EXPLOSION_MS = 420;

    /** One short-lived thing to draw: a tracer line or an explosion. */
    public static final class Effect {
        public final boolean tracer;
        public final float x;
        public final float y;
        public final float toX;
        public final float toY;
        public final int ownerId;
        public long remainingMs;
        public final long totalMs;

        Effect(boolean tracer, float x, float y, float toX, float toY, int ownerId, long ms) {
            this.tracer = tracer;
            this.x = x;
            this.y = y;
            this.toX = toX;
            this.toY = toY;
            this.ownerId = ownerId;
            this.remainingMs = ms;
            this.totalMs = ms;
        }

        public float progress() {
            return 1f - Math.max(0f, remainingMs / (float) totalMs);
        }
    }

    private final Skirmish skirmish;
    private final GameWorld world;
    private final Camera camera = new Camera();
    private final int playerId;

    private final List<Integer> selection = new ArrayList<Integer>();
    private final List<Effect> effects = new ArrayList<Effect>();
    private final List<GameEvent> eventScratch = new ArrayList<GameEvent>();

    private BuildingType placing;
    private int placeTileX = -1;
    private int placeTileY = -1;
    private float accumulator;
    private boolean paused;

    private String message = "";
    private long messageUntilMs;

    public GameSession(Faction faction, Difficulty difficulty, long seed) {
        this.skirmish = Skirmish.createVersusAi(MapCatalog.load(MapCatalog.KREISAU_VALLEY),
                faction, difficulty, seed);
        this.world = skirmish.world();
        this.playerId = skirmish.humanPlayerId();
        camera.setMap(world.map());
        int[] spawn = world.map().spawnPoint(playerId);
        camera.centerOn(spawn[0] + 3f, spawn[1] + 3f);
    }

    public GameWorld world() {
        return world;
    }

    public Camera camera() {
        return camera;
    }

    public int playerId() {
        return playerId;
    }

    public Player player() {
        return world.player(playerId);
    }

    public List<Integer> selection() {
        return selection;
    }

    public List<Effect> effects() {
        return effects;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public BuildingType placing() {
        return placing;
    }

    public void setPlacing(BuildingType type) {
        this.placing = type;
    }

    /** Where the build ghost currently sits; follows the finger while placing. */
    public int placeTileX() {
        return placeTileX;
    }

    public int placeTileY() {
        return placeTileY;
    }

    public void setPlaceTile(int tileX, int tileY) {
        this.placeTileX = tileX;
        this.placeTileY = tileY;
    }

    public String message() {
        return message;
    }

    public boolean hasMessage(long nowMs) {
        return nowMs < messageUntilMs && !message.isEmpty();
    }

    public void showMessage(String text) {
        this.message = text;
        this.messageUntilMs = System.currentTimeMillis() + 2500;
    }

    // --- simulation -----------------------------------------------------------------------

    /** Advances the simulation by real elapsed time, in fixed ticks. */
    public void update(float deltaSeconds) {
        ageEffects((long) (deltaSeconds * 1000f));
        if (paused || world.isGameOver()) {
            world.clearEvents();
            return;
        }

        accumulator += Math.min(deltaSeconds, 0.5f);
        int ticks = 0;
        while (accumulator >= GameWorld.TICK_SECONDS && ticks < MAX_CATCHUP_TICKS) {
            skirmish.step();
            accumulator -= GameWorld.TICK_SECONDS;
            ticks++;
        }
        if (ticks == MAX_CATCHUP_TICKS) {
            accumulator = 0f; // Dropped frames: give up on catching up rather than spiralling.
        }
        collectEffects();
        pruneSelection();
    }

    private void collectEffects() {
        eventScratch.clear();
        world.drainEvents(eventScratch);
        for (int i = 0; i < eventScratch.size(); i++) {
            GameEvent e = eventScratch.get(i);
            if (e.type() == GameEvent.Type.SHOT_FIRED) {
                effects.add(new Effect(true, e.x(), e.y(), e.toX(), e.toY(), e.ownerId(),
                        TRACER_MS));
            } else if (e.type() == GameEvent.Type.ENTITY_DESTROYED) {
                effects.add(new Effect(false, e.x(), e.y(), e.x(), e.y(), e.ownerId(),
                        EXPLOSION_MS));
            } else if (e.type() == GameEvent.Type.PLACEMENT_READY && e.ownerId() == playerId) {
                showMessage("Structure ready - tap the map to place it");
            } else if (e.type() == GameEvent.Type.INSUFFICIENT_FUNDS && e.ownerId() == playerId) {
                showMessage("Insufficient funds");
            }
        }
    }

    private void ageEffects(long elapsedMs) {
        for (int i = effects.size() - 1; i >= 0; i--) {
            Effect fx = effects.get(i);
            fx.remainingMs -= elapsedMs;
            if (fx.remainingMs <= 0) {
                effects.remove(i);
            }
        }
    }

    private void pruneSelection() {
        for (int i = selection.size() - 1; i >= 0; i--) {
            Entity e = world.entity(selection.get(i).intValue());
            if (e == null || !e.isAlive()) {
                selection.remove(i);
            }
        }
    }

    // --- selection ------------------------------------------------------------------------

    /** Selects whatever is under a world point, or clears the selection if that is nothing. */
    public void selectAt(float worldX, float worldY) {
        Entity hit = entityAt(worldX, worldY);
        selection.clear();
        if (hit != null && hit.ownerId() == playerId) {
            selection.add(Integer.valueOf(hit.id()));
        }
    }

    /** Box-selects the player's units inside a world-space rectangle. Structures are excluded. */
    public void selectInBox(float x0, float y0, float x1, float y1) {
        float minX = Math.min(x0, x1);
        float maxX = Math.max(x0, x1);
        float minY = Math.min(y0, y1);
        float maxY = Math.max(y0, y1);

        selection.clear();
        for (Unit u : world.units()) {
            if (u.ownerId() != playerId) {
                continue;
            }
            if (u.x() >= minX && u.x() <= maxX && u.y() >= minY && u.y() <= maxY) {
                selection.add(Integer.valueOf(u.id()));
            }
        }
        // A drag that catches nothing but happens to sit on one of our structures selects it,
        // which is how you get at a factory's rally point.
        if (selection.isEmpty()) {
            Entity hit = entityAt((minX + maxX) / 2f, (minY + maxY) / 2f);
            if (hit != null && hit.ownerId() == playerId) {
                selection.add(Integer.valueOf(hit.id()));
            }
        }
    }

    public Entity entityAt(float worldX, float worldY) {
        for (Unit u : world.units()) {
            if (u.distanceTo(worldX, worldY) <= Math.max(0.45f, u.radius() + 0.15f)) {
                return u;
            }
        }
        for (Building b : world.buildings()) {
            if (b.covers((int) worldX, (int) worldY)) {
                return b;
            }
        }
        return null;
    }

    public boolean hasSelection() {
        return !selection.isEmpty();
    }

    /** The single selected entity, or null when nothing or several things are selected. */
    public Entity singleSelection() {
        return selection.size() == 1 ? world.entity(selection.get(0).intValue()) : null;
    }

    // --- orders ---------------------------------------------------------------------------

    /**
     * Issues the natural order for a tap: attack what is hostile, harvest an ore tile with a
     * harvester, otherwise move. A long press turns a move into an attack-move.
     */
    public void commandAt(float worldX, float worldY, boolean attackMove) {
        if (selection.isEmpty()) {
            return;
        }
        int tileX = (int) worldX;
        int tileY = (int) worldY;
        Entity target = entityAt(worldX, worldY);
        boolean hostile = target != null && world.areEnemies(playerId, target.ownerId());

        for (int i = 0; i < selection.size(); i++) {
            Entity e = world.entity(selection.get(i).intValue());
            if (e == null || !e.isAlive()) {
                continue;
            }
            if (e.isBuilding()) {
                // Structures cannot move; a tap sets the rally point instead.
                ((Building) e).setRally(tileX, tileY);
                continue;
            }
            Unit u = (Unit) e;
            if (hostile) {
                world.issueOrder(playerId, u, new AttackOrder(target.id()));
            } else if (u.type().isHarvester() && world.map().ore(tileX, tileY) > 0) {
                world.issueOrder(playerId, u, new HarvestOrder());
            } else if (attackMove) {
                world.issueOrder(playerId, u, new AttackMoveOrder(tileX, tileY));
            } else {
                world.issueOrder(playerId, u, new MoveOrder(tileX, tileY));
            }
        }
        showMessage(hostile ? "Attacking" : (attackMove ? "Attack-move" : "Moving out"));
    }

    // --- production -----------------------------------------------------------------------

    public void queueUnit(UnitType type) {
        if (!world.canProduce(playerId, type)) {
            showMessage("Requires " + type.producedBy().displayName());
            return;
        }
        if (!world.enqueueUnit(playerId, type)) {
            showMessage("Cannot build " + type.displayName());
        }
    }

    public void queueBuilding(BuildingType type) {
        if (!world.canProduce(playerId, type)) {
            showMessage("Requires " + (type.prerequisite() == null
                    ? BuildingType.COMMAND_POST.displayName() : type.prerequisite().displayName()));
            return;
        }
        if (!world.enqueueBuilding(playerId, type)) {
            showMessage("Cannot build " + type.displayName());
        }
    }

    /** True if the structure queue has something finished and waiting for a site. */
    public boolean hasStructureReady() {
        return player().structureQueue().isHeadReady();
    }

    public BuildingType readyStructure() {
        return hasStructureReady() ? player().structureQueue().head().buildingType() : null;
    }

    /**
     * Drops the finished structure at a tile.
     *
     * @return true if it went down
     */
    public boolean placeAt(int tileX, int tileY) {
        BuildingType type = readyStructure();
        if (type == null) {
            return false;
        }
        int originX = tileX - type.tilesWide() / 2;
        int originY = tileY - type.tilesHigh() / 2;
        if (world.placeQueued(playerId, originX, originY) == null) {
            showMessage("Cannot build there");
            return false;
        }
        placing = null;
        return true;
    }

    /** Whether a ghost at this tile would be a legal site, for the placement preview. */
    public boolean isPlacementValid(BuildingType type, int tileX, int tileY) {
        return world.isValidPlacement(playerId, type,
                tileX - type.tilesWide() / 2, tileY - type.tilesHigh() / 2);
    }
}
