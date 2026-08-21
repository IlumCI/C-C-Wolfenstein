package com.ccwolf.android;

import com.ccwolf.android.render.Camera;
import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.api.CommandBus;
import com.ccwolf.core.api.CommandResult;
import com.ccwolf.core.api.PlayerCommand;
import com.ccwolf.core.api.WorldView;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.event.GameEvent;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.sim.Skirmish;
import java.util.ArrayList;
import java.util.List;

/**
 * The interface layer's state: the camera, what is selected, what is being placed, which
 * pointer mode is active, and the short-lived effects a frame needs.
 *
 * <p>It owns no game rules. Every change to the match goes out as a {@link PlayerCommand}
 * through the {@link CommandBus}, and everything it reads comes back through {@link WorldView}.
 * Anything that looks like a rule living here is a bug.
 */
public final class GameSession {

    /** Never simulate more than this many ticks in one frame, or a stall becomes a freeze. */
    private static final int MAX_CATCHUP_TICKS = 6;

    private static final long TRACER_MS = 90;
    private static final long EXPLOSION_MS = 480;
    private static final long WRECK_MS = 9000;
    private static final long PING_MS = 550;

    /** What a tap on the battlefield currently means. */
    public enum PointerMode { COMMAND, SELL, REPAIR }

    /** One short-lived thing to draw on top of the world. */
    public static final class Effect {

        public enum Kind { TRACER, EXPLOSION, WRECK, MOVE_PING, ATTACK_PING }

        public final Kind kind;
        public final float x;
        public final float y;
        public final float toX;
        public final float toY;
        public final int ownerId;
        public final int variant;
        public long remainingMs;
        public final long totalMs;

        Effect(Kind kind, float x, float y, float toX, float toY, int ownerId, int variant,
               long ms) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.toX = toX;
            this.toY = toY;
            this.ownerId = ownerId;
            this.variant = variant;
            this.remainingMs = ms;
            this.totalMs = ms;
        }

        /** 0 at birth, 1 at expiry. */
        public float progress() {
            return 1f - Math.max(0f, Math.min(1f, remainingMs / (float) totalMs));
        }
    }

    private final Skirmish skirmish;
    private final GameWorld world;
    private final CommandBus commands;
    private final WorldView view;
    private final Camera camera = new Camera();
    private final int playerId;

    private final List<Integer> selection = new ArrayList<Integer>();
    private final List<Effect> effects = new ArrayList<Effect>();
    private final List<GameEvent> eventScratch = new ArrayList<GameEvent>();

    private PointerMode pointerMode = PointerMode.COMMAND;
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
        this.commands = skirmish.commands();
        this.playerId = skirmish.humanPlayerId();
        this.view = skirmish.viewFor(playerId);
        camera.setMap(world.map());
        int[] spawn = world.map().spawnPoint(playerId);
        camera.centerOn(spawn[0] + 3f, spawn[1] + 3f);
    }

    // --- accessors ------------------------------------------------------------------------

    public WorldView view() {
        return view;
    }

    /** Kept for the renderer, which needs entity lists and the map; it never mutates them. */
    public GameWorld world() {
        return world;
    }

    public Camera camera() {
        return camera;
    }

    public int playerId() {
        return playerId;
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

    public PointerMode pointerMode() {
        return pointerMode;
    }

    public void setPointerMode(PointerMode mode) {
        this.pointerMode = mode;
        this.placing = null;
        showMessage(mode == PointerMode.SELL ? "Sell: tap one of your structures"
                : mode == PointerMode.REPAIR ? "Repair: tap one of your structures" : "");
    }

    public void togglePointerMode(PointerMode mode) {
        setPointerMode(pointerMode == mode ? PointerMode.COMMAND : mode);
    }

    public BuildingType placing() {
        return placing;
    }

    public void setPlacing(BuildingType type) {
        this.placing = type;
    }

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

    /**
     * How far the renderer should blend between the last simulation tick and this one.
     * 0 means draw the previous positions, 1 means draw the current ones.
     */
    public float interpolation() {
        return Math.max(0f, Math.min(1f, accumulator / GameWorld.TICK_SECONDS));
    }

    // --- simulation clock -----------------------------------------------------------------

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
            accumulator = 0f; // Dropped frames: stop trying to catch up rather than spiral.
        }
        collectEffects();
        pruneSelection();
    }

    private void collectEffects() {
        eventScratch.clear();
        world.drainEvents(eventScratch);
        for (int i = 0; i < eventScratch.size(); i++) {
            GameEvent e = eventScratch.get(i);
            switch (e.type()) {
                case SHOT_FIRED:
                    effects.add(new Effect(Effect.Kind.TRACER, e.x(), e.y(), e.toX(), e.toY(),
                            e.ownerId(), 0, TRACER_MS));
                    break;
                case ENTITY_DESTROYED:
                    effects.add(new Effect(Effect.Kind.EXPLOSION, e.x(), e.y(), e.x(), e.y(),
                            e.ownerId(), 0, EXPLOSION_MS));
                    // Wrecks and craters linger, so a battlefield looks fought over.
                    effects.add(new Effect(Effect.Kind.WRECK, e.x(), e.y(), e.x(), e.y(),
                            e.ownerId(), e.entityId() & 3, WRECK_MS));
                    break;
                case PLACEMENT_READY:
                    if (e.ownerId() == playerId) {
                        showMessage("Structure ready - tap the ground to place it");
                    }
                    break;
                case INSUFFICIENT_FUNDS:
                    if (e.ownerId() == playerId) {
                        showMessage("Insufficient funds");
                    }
                    break;
                case POWER_LOST:
                    if (e.ownerId() == playerId) {
                        showMessage("Low power - defences offline");
                    }
                    break;
                case BUILDING_SOLD:
                    if (e.ownerId() == playerId) {
                        showMessage("Sold for " + e.amount() + " credits");
                    }
                    break;
                default:
                    break;
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

    public void selectAt(float worldX, float worldY) {
        Entity hit = entityAt(worldX, worldY);
        selection.clear();
        if (hit != null && view.isMine(hit)) {
            selection.add(Integer.valueOf(hit.id()));
        }
    }

    /** Box-selects the player's units; falls back to a structure under the box's centre. */
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
        if (selection.isEmpty()) {
            Entity hit = entityAt((minX + maxX) / 2f, (minY + maxY) / 2f);
            if (hit != null && view.isMine(hit)) {
                selection.add(Integer.valueOf(hit.id()));
            }
        }
    }

    public Entity entityAt(float worldX, float worldY) {
        for (Unit u : world.units()) {
            if (!view.isDiscovered(u)) {
                continue;
            }
            if (u.distanceTo(worldX, worldY) <= Math.max(0.45f, u.radius() + 0.15f)) {
                return u;
            }
        }
        for (Building b : world.buildings()) {
            if (b.covers((int) worldX, (int) worldY) && view.isDiscovered(b)) {
                return b;
            }
        }
        return null;
    }

    public boolean hasSelection() {
        return !selection.isEmpty();
    }

    public Entity singleSelection() {
        return selection.size() == 1 ? world.entity(selection.get(0).intValue()) : null;
    }

    private int[] selectedIds() {
        int[] ids = new int[selection.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = selection.get(i).intValue();
        }
        return ids;
    }

    // --- issuing commands -----------------------------------------------------------------

    /** Reports a rejection to the player; accepted commands say nothing. */
    private boolean report(CommandResult result) {
        if (!result.isAccepted() && result.reason() != null) {
            showMessage(result.reason());
        }
        return result.isAccepted();
    }

    /**
     * Handles a tap on the battlefield according to the current pointer mode: sell it, repair
     * it, or give the selection the order that fits what was tapped.
     */
    public void tapWorld(float worldX, float worldY, boolean longPress) {
        int tileX = (int) worldX;
        int tileY = (int) worldY;
        Entity target = entityAt(worldX, worldY);

        if (pointerMode == PointerMode.SELL || pointerMode == PointerMode.REPAIR) {
            if (target == null || !target.isBuilding() || !view.isMine(target)) {
                showMessage("Tap one of your own structures");
                return;
            }
            if (pointerMode == PointerMode.SELL) {
                report(commands.submit(playerId, new PlayerCommand.Sell(target.id())));
            } else {
                Building b = (Building) target;
                boolean turnOn = !b.isRepairing();
                if (report(commands.submit(playerId,
                        new PlayerCommand.Repair(target.id(), turnOn)))) {
                    showMessage(turnOn ? "Repairing " + b.displayName() : "Repairs stopped");
                }
            }
            setPointerMode(PointerMode.COMMAND);
            return;
        }

        if (placing != null) {
            placeAt(tileX, tileY);
            return;
        }

        if (!hasSelection()) {
            selectAt(worldX, worldY);
            return;
        }

        // Tapping our own thing with a selection re-selects it, unless it is a long press,
        // which always means "give the current selection an order about this spot".
        if (target != null && view.isMine(target) && !longPress) {
            selectAt(worldX, worldY);
            return;
        }
        commandAt(worldX, worldY, longPress);
    }

    /** Issues the order that fits the target: attack, harvest, rally or move. */
    public void commandAt(float worldX, float worldY, boolean attackMove) {
        if (selection.isEmpty()) {
            return;
        }
        int tileX = (int) worldX;
        int tileY = (int) worldY;
        Entity target = entityAt(worldX, worldY);
        boolean hostile = view.isHostile(target);

        // A selected structure has no legs; a tap sets its rally point instead.
        Entity single = singleSelection();
        if (single != null && single.isBuilding()) {
            if (report(commands.submit(playerId,
                    new PlayerCommand.SetRally(single.id(), tileX, tileY)))) {
                addPing(tileX, tileY, false);
            }
            return;
        }

        int[] ids = selectedIds();
        if (hostile) {
            if (report(commands.submit(playerId, new PlayerCommand.Attack(ids, target.id())))) {
                addPing(tileX, tileY, true);
            }
            return;
        }

        // Harvesters told to go to an ore seam should mine it, not just stand on it.
        if (world.map().ore(tileX, tileY) > 0 && anySelectedIsHarvester()) {
            if (report(commands.submit(playerId, new PlayerCommand.Harvest(ids)))) {
                addPing(tileX, tileY, false);
                return;
            }
        }

        PlayerCommand order = attackMove ? new PlayerCommand.AttackMove(ids, tileX, tileY)
                : new PlayerCommand.Move(ids, tileX, tileY, false);
        if (report(commands.submit(playerId, order))) {
            addPing(tileX, tileY, attackMove);
        }
    }

    private boolean anySelectedIsHarvester() {
        for (int i = 0; i < selection.size(); i++) {
            Entity e = world.entity(selection.get(i).intValue());
            if (e instanceof Unit && ((Unit) e).type().isHarvester()) {
                return true;
            }
        }
        return false;
    }

    private void addPing(int tileX, int tileY, boolean hostile) {
        effects.add(new Effect(hostile ? Effect.Kind.ATTACK_PING : Effect.Kind.MOVE_PING,
                tileX + 0.5f, tileY + 0.5f, tileX + 0.5f, tileY + 0.5f, playerId, 0, PING_MS));
    }

    public void stopSelection() {
        if (report(commands.submit(playerId, new PlayerCommand.Stop(selectedIds())))) {
            showMessage("Holding position");
        }
    }

    public void queueUnit(UnitType type) {
        report(commands.submit(playerId, new PlayerCommand.QueueUnit(type)));
    }

    public void queueBuilding(BuildingType type) {
        report(commands.submit(playerId, new PlayerCommand.QueueBuilding(type)));
    }

    public void cancelLast(PlayerCommand.Line line) {
        report(commands.submit(playerId, new PlayerCommand.CancelQueue(line)));
    }

    public void setPrimary(int buildingId) {
        if (report(commands.submit(playerId, new PlayerCommand.SetPrimary(buildingId)))) {
            showMessage("Primary structure set");
        }
    }

    public boolean placeAt(int tileX, int tileY) {
        boolean placed = report(commands.submit(playerId,
                new PlayerCommand.PlaceBuilding(tileX, tileY)));
        if (placed) {
            placing = null;
        }
        return placed;
    }

    // --- placement helpers used by the renderer -------------------------------------------

    public boolean hasStructureReady() {
        return view.readyStructure() != null;
    }

    public BuildingType readyStructure() {
        return view.readyStructure();
    }

    public boolean isPlacementValid(BuildingType type, int tileX, int tileY) {
        return view.canPlaceCentred(type, tileX, tileY);
    }
}
