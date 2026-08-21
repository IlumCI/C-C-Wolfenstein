package com.ccwolf.core.api;

import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.order.AttackMoveOrder;
import com.ccwolf.core.order.AttackOrder;
import com.ccwolf.core.order.HarvestOrder;
import com.ccwolf.core.order.MoveOrder;
import com.ccwolf.core.order.Order;
import com.ccwolf.core.sim.GameWorld;

/**
 * Everything a player — human or otherwise — can ask the simulation to do.
 *
 * <p>This is the entire mutation vocabulary of the game. The Android layer never calls into
 * {@link GameWorld} directly; it builds one of these and hands it to a {@link CommandBus},
 * which is what keeps the simulation testable without a screen and swappable under one.
 *
 * <p>The command types are nested here on purpose: the whole vocabulary is worth reading in
 * one sitting, and thirteen near-identical files would not be.
 */
public abstract class PlayerCommand {

    /** Short description for logs, tests and debugging overlays. */
    public abstract String describe();

    /**
     * Applies the command. Package-private so only {@link CommandBus} can invoke it —
     * ownership and legality checks live there and must not be bypassed.
     */
    abstract CommandResult execute(GameWorld world, int playerId);

    // --- helpers shared by the unit commands ----------------------------------------------

    /** Resolves ids to living units the issuing player actually owns. */
    private static CommandResult applyToUnits(GameWorld world, int playerId, int[] unitIds,
                                              OrderFactory factory, boolean queue) {
        int applied = 0;
        for (int i = 0; i < unitIds.length; i++) {
            Entity e = world.entity(unitIds[i]);
            if (!(e instanceof Unit) || e.ownerId() != playerId || !e.isAlive()) {
                continue;
            }
            Unit unit = (Unit) e;
            Order order = factory.create(world, unit);
            if (order == null) {
                continue;
            }
            if (queue) {
                world.queueOrder(playerId, unit, order);
            } else {
                world.issueOrder(playerId, unit, order);
            }
            applied++;
        }
        return applied > 0 ? CommandResult.accepted()
                : CommandResult.rejected("Nothing of yours to order");
    }

    /** Lets each unit build its own order, so a mixed selection can react differently. */
    private interface OrderFactory {
        Order create(GameWorld world, Unit unit);
    }

    // --- unit orders ----------------------------------------------------------------------

    /** Walk to a tile. */
    public static final class Move extends PlayerCommand {
        private final int[] unitIds;
        private final int tileX;
        private final int tileY;
        private final boolean queued;

        public Move(int[] unitIds, int tileX, int tileY, boolean queued) {
            this.unitIds = unitIds.clone();
            this.tileX = tileX;
            this.tileY = tileY;
            this.queued = queued;
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            if (!world.map().inBounds(tileX, tileY)) {
                return CommandResult.rejected("Off the map");
            }
            return applyToUnits(world, playerId, unitIds, new OrderFactory() {
                @Override
                public Order create(GameWorld world, Unit unit) {
                    return new MoveOrder(tileX, tileY);
                }
            }, queued);
        }

        @Override
        public String describe() {
            return "Move " + unitIds.length + " to " + tileX + "," + tileY;
        }
    }

    /** Chase and shoot one specific entity. */
    public static final class Attack extends PlayerCommand {
        private final int[] unitIds;
        private final int targetId;

        public Attack(int[] unitIds, int targetId) {
            this.unitIds = unitIds.clone();
            this.targetId = targetId;
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            Entity target = world.entity(targetId);
            if (target == null || !target.isAlive()) {
                return CommandResult.rejected("No such target");
            }
            if (!world.areEnemies(playerId, target.ownerId())) {
                return CommandResult.rejected("That is one of ours");
            }
            return applyToUnits(world, playerId, unitIds, new OrderFactory() {
                @Override
                public Order create(GameWorld world, Unit unit) {
                    return unit.weapon() == null ? null : new AttackOrder(targetId);
                }
            }, false);
        }

        @Override
        public String describe() {
            return "Attack #" + targetId;
        }
    }

    /** Advance on a tile, engaging anything met on the way. */
    public static final class AttackMove extends PlayerCommand {
        private final int[] unitIds;
        private final int tileX;
        private final int tileY;

        public AttackMove(int[] unitIds, int tileX, int tileY) {
            this.unitIds = unitIds.clone();
            this.tileX = tileX;
            this.tileY = tileY;
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            if (!world.map().inBounds(tileX, tileY)) {
                return CommandResult.rejected("Off the map");
            }
            return applyToUnits(world, playerId, unitIds, new OrderFactory() {
                @Override
                public Order create(GameWorld world, Unit unit) {
                    return new AttackMoveOrder(tileX, tileY);
                }
            }, false);
        }

        @Override
        public String describe() {
            return "Attack-move to " + tileX + "," + tileY;
        }
    }

    /** Send harvesters back to work. */
    public static final class Harvest extends PlayerCommand {
        private final int[] unitIds;

        public Harvest(int[] unitIds) {
            this.unitIds = unitIds.clone();
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            return applyToUnits(world, playerId, unitIds, new OrderFactory() {
                @Override
                public Order create(GameWorld world, Unit unit) {
                    return unit.type().isHarvester() ? new HarvestOrder() : null;
                }
            }, false);
        }

        @Override
        public String describe() {
            return "Harvest with " + unitIds.length;
        }
    }

    /** Halt and hold position. */
    public static final class Stop extends PlayerCommand {
        private final int[] unitIds;

        public Stop(int[] unitIds) {
            this.unitIds = unitIds.clone();
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            int stopped = 0;
            for (int i = 0; i < unitIds.length; i++) {
                Entity e = world.entity(unitIds[i]);
                if (e instanceof Unit && e.ownerId() == playerId && e.isAlive()) {
                    ((Unit) e).clearOrders();
                    stopped++;
                }
            }
            return stopped > 0 ? CommandResult.accepted()
                    : CommandResult.rejected("Nothing of yours to stop");
        }

        @Override
        public String describe() {
            return "Stop " + unitIds.length;
        }
    }

    // --- structures -----------------------------------------------------------------------

    /** Point a factory's new units at a tile. */
    public static final class SetRally extends PlayerCommand {
        private final int buildingId;
        private final int tileX;
        private final int tileY;

        public SetRally(int buildingId, int tileX, int tileY) {
            this.buildingId = buildingId;
            this.tileX = tileX;
            this.tileY = tileY;
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            Entity e = world.entity(buildingId);
            if (!(e instanceof Building) || e.ownerId() != playerId) {
                return CommandResult.rejected("Not your structure");
            }
            ((Building) e).setRally(tileX, tileY);
            return CommandResult.accepted();
        }

        @Override
        public String describe() {
            return "Rally #" + buildingId + " to " + tileX + "," + tileY;
        }
    }

    /** Sell a structure back for part of its cost. */
    public static final class Sell extends PlayerCommand {
        private final int buildingId;

        public Sell(int buildingId) {
            this.buildingId = buildingId;
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            int refund = world.sellBuilding(playerId, buildingId);
            return refund < 0 ? CommandResult.rejected("Not your structure")
                    : CommandResult.accepted();
        }

        @Override
        public String describe() {
            return "Sell #" + buildingId;
        }
    }

    /** Turn the repair crews on or off for a structure. */
    public static final class Repair extends PlayerCommand {
        private final int buildingId;
        private final boolean on;

        public Repair(int buildingId, boolean on) {
            this.buildingId = buildingId;
            this.on = on;
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            return world.setRepairing(playerId, buildingId, on) ? CommandResult.accepted()
                    : CommandResult.rejected("Not your structure");
        }

        @Override
        public String describe() {
            return (on ? "Repair #" : "Stop repairing #") + buildingId;
        }
    }

    /** Choose which barracks or war works new units come out of. */
    public static final class SetPrimary extends PlayerCommand {
        private final int buildingId;

        public SetPrimary(int buildingId) {
            this.buildingId = buildingId;
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            return world.setPrimaryProducer(playerId, buildingId) ? CommandResult.accepted()
                    : CommandResult.rejected("That structure builds nothing");
        }

        @Override
        public String describe() {
            return "Primary #" + buildingId;
        }
    }

    // --- production -----------------------------------------------------------------------

    /** Put a unit on a production line. */
    public static final class QueueUnit extends PlayerCommand {
        private final UnitType type;

        public QueueUnit(UnitType type) {
            this.type = type;
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            String blocker = world.productionBlocker(playerId, type);
            if (blocker != null) {
                return CommandResult.rejected(blocker);
            }
            return world.enqueueUnit(playerId, type) ? CommandResult.accepted()
                    : CommandResult.rejected("Cannot build " + type.displayName());
        }

        @Override
        public String describe() {
            return "Queue " + type.displayName();
        }
    }

    /** Put a structure on the build queue; it waits to be placed when it finishes. */
    public static final class QueueBuilding extends PlayerCommand {
        private final BuildingType type;

        public QueueBuilding(BuildingType type) {
            this.type = type;
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            String blocker = world.productionBlocker(playerId, type);
            if (blocker != null) {
                return CommandResult.rejected(blocker);
            }
            return world.enqueueBuilding(playerId, type) ? CommandResult.accepted()
                    : CommandResult.rejected("Cannot build " + type.displayName());
        }

        @Override
        public String describe() {
            return "Queue " + type.displayName();
        }
    }

    /** Which production line a queue command refers to. */
    public enum Line { INFANTRY, VEHICLE, STRUCTURE }

    /** Cancel the newest entry on a line and take the refund. */
    public static final class CancelQueue extends PlayerCommand {
        private final Line line;

        public CancelQueue(Line line) {
            this.line = line;
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            com.ccwolf.core.economy.ProductionQueue queue = queueFor(world, playerId);
            if (queue.isEmpty()) {
                return CommandResult.rejected("Nothing queued");
            }
            world.cancelLast(playerId, queue);
            return CommandResult.accepted();
        }

        private com.ccwolf.core.economy.ProductionQueue queueFor(GameWorld world, int playerId) {
            switch (line) {
                case INFANTRY:
                    return world.player(playerId).infantryQueue();
                case VEHICLE:
                    return world.player(playerId).vehicleQueue();
                case STRUCTURE:
                default:
                    return world.player(playerId).structureQueue();
            }
        }

        @Override
        public String describe() {
            return "Cancel last " + line;
        }
    }

    /** Site the finished structure sitting at the head of the build queue. */
    public static final class PlaceBuilding extends PlayerCommand {
        private final int tileX;
        private final int tileY;

        public PlaceBuilding(int tileX, int tileY) {
            this.tileX = tileX;
            this.tileY = tileY;
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            com.ccwolf.core.economy.ProductionQueue queue =
                    world.player(playerId).structureQueue();
            if (!queue.isHeadReady()) {
                return CommandResult.rejected("Nothing ready to place");
            }
            BuildingType type = queue.head().buildingType();
            // The tile given is where the player tapped; the structure centres on it.
            int originX = tileX - type.tilesWide() / 2;
            int originY = tileY - type.tilesHigh() / 2;
            if (!world.isValidPlacement(playerId, type, originX, originY)) {
                return CommandResult.rejected("Cannot build there");
            }
            return world.placeQueued(playerId, originX, originY) != null
                    ? CommandResult.accepted() : CommandResult.rejected("Cannot build there");
        }

        @Override
        public String describe() {
            return "Place at " + tileX + "," + tileY;
        }
    }
}
