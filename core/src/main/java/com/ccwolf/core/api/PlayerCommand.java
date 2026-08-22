package com.ccwolf.core.api;

import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.order.AttackMoveOrder;
import com.ccwolf.core.order.AttackOrder;
import com.ccwolf.core.order.BombardOrder;
import com.ccwolf.core.order.EntrenchOrder;
import com.ccwolf.core.order.HarvestOrder;
import com.ccwolf.core.order.HijackOrder;
import com.ccwolf.core.order.SabotageOrder;
import com.ccwolf.core.order.MoveOrder;
import com.ccwolf.core.order.Order;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.squad.Formation;
import com.ccwolf.core.squad.Squad;
import com.ccwolf.core.squad.SquadOrder;
import java.util.ArrayList;
import java.util.List;

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

    // --- helpers shared by the squad commands ----------------------------------------------

    /**
     * Resolves ids to living squads the issuing player actually owns.
     *
     * <p>Squad commands are their own classes rather than the unit commands taught to accept
     * squad ids. Overloading would be fewer classes but would make an id mean two things at the
     * one seam the codebase is most careful about, and the caller always knows which it picked.
     */
    private static CommandResult applyToSquads(GameWorld world, int playerId, int[] squadIds,
                                               SquadAction action) {
        int applied = 0;
        for (int i = 0; i < squadIds.length; i++) {
            Squad squad = world.squads().byId(squadIds[i]);
            if (squad == null || squad.ownerId() != playerId || squad.isWipedOut()) {
                continue;
            }
            action.apply(world, playerId, squad);
            applied++;
        }
        return applied > 0 ? CommandResult.accepted()
                : CommandResult.rejected("No squads of yours to order");
    }

    private interface SquadAction {
        void apply(GameWorld world, int playerId, Squad squad);
    }

    // --- squad orders ---------------------------------------------------------------------

    /** Send squads somewhere, ignoring what they pass on the way. */
    public static final class SquadMove extends PlayerCommand {
        private final int[] squadIds;
        private final int tileX;
        private final int tileY;

        public SquadMove(int[] squadIds, int tileX, int tileY) {
            this.squadIds = squadIds.clone();
            this.tileX = tileX;
            this.tileY = tileY;
        }

        @Override
        public String describe() {
            return "Squad move to " + tileX + "," + tileY;
        }

        @Override
        CommandResult execute(final GameWorld world, int playerId) {
            return applyToSquads(world, playerId, squadIds, new SquadAction() {
                @Override
                public void apply(GameWorld w, int player, Squad squad) {
                    w.orderSquadTo(player, squad, SquadOrder.MOVE, tileX, tileY);
                }
            });
        }
    }

    /** Send squads somewhere, stopping to deal with anything hostile on the way. */
    public static final class SquadAttackMove extends PlayerCommand {
        private final int[] squadIds;
        private final int tileX;
        private final int tileY;

        public SquadAttackMove(int[] squadIds, int tileX, int tileY) {
            this.squadIds = squadIds.clone();
            this.tileX = tileX;
            this.tileY = tileY;
        }

        @Override
        public String describe() {
            return "Squad attack-move to " + tileX + "," + tileY;
        }

        @Override
        CommandResult execute(final GameWorld world, int playerId) {
            return applyToSquads(world, playerId, squadIds, new SquadAction() {
                @Override
                public void apply(GameWorld w, int player, Squad squad) {
                    w.orderSquadTo(player, squad, SquadOrder.ATTACK_MOVE, tileX, tileY);
                }
            });
        }
    }

    /** Put squads onto one specific target. */
    public static final class SquadAttack extends PlayerCommand {
        private final int[] squadIds;
        private final int targetId;

        public SquadAttack(int[] squadIds, int targetId) {
            this.squadIds = squadIds.clone();
            this.targetId = targetId;
        }

        @Override
        public String describe() {
            return "Squad attack #" + targetId;
        }

        @Override
        CommandResult execute(final GameWorld world, int playerId) {
            Entity target = world.entity(targetId);
            if (target == null || !target.isAlive()) {
                return CommandResult.rejected("Nothing there to attack");
            }
            if (target.ownerId() == playerId) {
                return CommandResult.rejected("That is yours");
            }
            return applyToSquads(world, playerId, squadIds, new SquadAction() {
                @Override
                public void apply(GameWorld w, int player, Squad squad) {
                    w.orderSquadAttack(player, squad, targetId);
                }
            });
        }
    }

    /** Halt squads where they stand. They still fight what comes to them. */
    public static final class SquadStop extends PlayerCommand {
        private final int[] squadIds;

        public SquadStop(int[] squadIds) {
            this.squadIds = squadIds.clone();
        }

        @Override
        public String describe() {
            return "Squad hold";
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            return applyToSquads(world, playerId, squadIds, new SquadAction() {
                @Override
                public void apply(GameWorld w, int player, Squad squad) {
                    w.orderSquadHold(player, squad);
                }
            });
        }
    }

    /**
     * Take a piece of ground and hold it properly.
     *
     * <p>The one order in the game whose payoff is entirely in what the ground becomes rather
     * than in what the men do. Given time and quiet, a squad turns a tile into somewhere that
     * costs an attacker to cross and shelters whoever is in it.
     */
    public static final class SquadEntrench extends PlayerCommand {
        private final int[] squadIds;
        private final int tileX;
        private final int tileY;
        private final boolean inPlace;

        /** Dig in on a named tile. */
        public SquadEntrench(int[] squadIds, int tileX, int tileY) {
            this.squadIds = squadIds.clone();
            this.tileX = tileX;
            this.tileY = tileY;
            this.inPlace = false;
        }

        /**
         * Dig in where each squad already is.
         *
         * <p>Its own constructor rather than a tile the caller works out, because with several
         * squads selected there is no one tile that is right: sending them all to the first
         * squad's position would gather the lot onto one spot, which is the opposite of what
         * telling a line to dig in means.
         */
        public SquadEntrench(int[] squadIds) {
            this.squadIds = squadIds.clone();
            this.tileX = -1;
            this.tileY = -1;
            this.inPlace = true;
        }

        @Override
        public String describe() {
            return inPlace ? "Squad dig in where it stands"
                    : "Squad dig in at " + tileX + "," + tileY;
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            if (!inPlace && !world.map().inBounds(tileX, tileY)) {
                return CommandResult.rejected("Off the map");
            }
            return applyToSquads(world, playerId, squadIds, new SquadAction() {
                @Override
                public void apply(GameWorld w, int player, Squad squad) {
                    int x = inPlace ? squad.anchorTileX() : tileX;
                    int y = inPlace ? squad.anchorTileY() : tileY;
                    w.orderSquadTo(player, squad, SquadOrder.ENTRENCH, x, y);
                }
            });
        }
    }

    /** Change the shape squads stand in. */
    public static final class SetFormation extends PlayerCommand {
        private final int[] squadIds;
        private final Formation formation;

        public SetFormation(int[] squadIds, Formation formation) {
            this.squadIds = squadIds.clone();
            this.formation = formation;
        }

        @Override
        public String describe() {
            return "Form " + formation;
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            return applyToSquads(world, playerId, squadIds, new SquadAction() {
                @Override
                public void apply(GameWorld w, int player, Squad squad) {
                    squad.setFormation(formation);
                }
            });
        }
    }

    /** Break named members out of a squad; two or more of them form a new one. */
    public static final class SplitSquad extends PlayerCommand {
        private final int squadId;
        private final int[] unitIds;

        public SplitSquad(int squadId, int[] unitIds) {
            this.squadId = squadId;
            this.unitIds = unitIds.clone();
        }

        @Override
        public String describe() {
            return "Break up squad #" + squadId;
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            Squad squad = world.squads().byId(squadId);
            if (squad == null || squad.ownerId() != playerId) {
                return CommandResult.rejected("No such squad of yours");
            }
            List<Unit> leaving = new ArrayList<Unit>();
            for (int i = 0; i < unitIds.length; i++) {
                Entity e = world.entity(unitIds[i]);
                if (e instanceof Unit && e.ownerId() == playerId && e.isAlive()) {
                    leaving.add((Unit) e);
                }
            }
            if (leaving.isEmpty()) {
                return CommandResult.rejected("Nobody to break out");
            }
            world.splitSquad(squad, leaving);
            return CommandResult.accepted();
        }
    }

    /** Queue replacements to bring a worn squad back up to strength. */
    public static final class Reinforce extends PlayerCommand {
        private final int squadId;

        public Reinforce(int squadId) {
            this.squadId = squadId;
        }

        @Override
        public String describe() {
            return "Reinforce squad #" + squadId;
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            Squad squad = world.squads().byId(squadId);
            if (squad == null || squad.ownerId() != playerId || squad.isWipedOut()) {
                return CommandResult.rejected("No such squad of yours");
            }
            if (squad.shortfall() <= 0) {
                return CommandResult.rejected("Already at strength");
            }
            int queued = world.reinforceSquad(playerId, squad);
            return queued > 0 ? CommandResult.accepted()
                    : CommandResult.rejected("Cannot train replacements right now");
        }
    }

    /** Queue a whole squad rather than one man. */
    public static final class QueueSquad extends PlayerCommand {
        private final UnitType type;

        public QueueSquad(UnitType type) {
            this.type = type;
        }

        @Override
        public String describe() {
            return "Train " + type.displayName() + " squad";
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            return world.enqueueSquad(playerId, type)
                    ? CommandResult.accepted()
                    : CommandResult.rejected("Cannot train that right now");
        }
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

    /** Dig one man in where he stands, or on a tile he is sent to. Vehicles refuse. */
    public static final class Entrench extends PlayerCommand {
        private final int[] unitIds;
        private final int tileX;
        private final int tileY;

        public Entrench(int[] unitIds, int tileX, int tileY) {
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
                    // A tank crew has no shovel, and sending one to sit on a tile pretending
                    // to dig would be a quiet way of losing a tank.
                    return unit.type().isVehicle() ? null : new EntrenchOrder(tileX, tileY);
                }
            }, false);
        }

        @Override
        public String describe() {
            return "Dig in " + unitIds.length + " at " + tileX + "," + tileY;
        }
    }

    /**
     * Put a barrage on a piece of ground.
     *
     * <p>The one order in the game that names a place rather than a target, which is what
     * indirect fire is: by the time the shells arrive, whether anybody is still standing there
     * is no longer the gunner's business.
     */
    public static final class Bombard extends PlayerCommand {
        private final int[] unitIds;
        private final int tileX;
        private final int tileY;

        public Bombard(int[] unitIds, int tileX, int tileY) {
            this.unitIds = unitIds.clone();
            this.tileX = tileX;
            this.tileY = tileY;
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            if (!world.map().inBounds(tileX, tileY)) {
                return CommandResult.rejected("Off the map");
            }
            // Refused out loud, at the moment of asking. A gun that silently declined to fire
            // because nobody had been over there lately would be the most confusing thing in
            // the game - the player would read it as the order not registering.
            if (!world.canObserve(playerId, tileX, tileY)) {
                return CommandResult.rejected("Nobody can see that");
            }
            return applyToUnits(world, playerId, unitIds, new OrderFactory() {
                @Override
                public Order create(GameWorld world, Unit unit) {
                    return unit.type().isArtillery() ? new BombardOrder(tileX, tileY) : null;
                }
            }, false);
        }

        @Override
        public String describe() {
            return "Bombard " + tileX + "," + tileY;
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
                    if (unit.weapon() == null) {
                        return null;
                    }
                    // Tapping an enemy with a gun selected means "put your shells there", not
                    // "drive at it". An AttackOrder would have the battery close on its target
                    // until the target was inside the one range it cannot fire at.
                    if (unit.type().isArtillery()) {
                        return new BombardOrder(target.tileX(), target.tileY());
                    }
                    return new AttackOrder(targetId);
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

    /**
     * Send specialists at a target and let each one do what it does: an Infiltrator boards a
     * vehicle and steals it, a Saboteur plants a charge and switches it off.
     *
     * <p>One command rather than two because the player never thinks in terms of "hijack" and
     * "sabotage" — they tap the enemy thing with the specialist selected and expect the right
     * thing to happen. Anything in the selection that cannot do either is left alone.
     */
    public static final class Infiltrate extends PlayerCommand {
        private final int[] unitIds;
        private final int targetId;

        public Infiltrate(int[] unitIds, int targetId) {
            this.unitIds = unitIds.clone();
            this.targetId = targetId;
        }

        @Override
        CommandResult execute(GameWorld world, int playerId) {
            final Entity target = world.entity(targetId);
            if (target == null || !target.isAlive()) {
                return CommandResult.rejected("No such target");
            }
            if (!world.areEnemies(playerId, target.ownerId())) {
                return CommandResult.rejected("That is one of ours");
            }

            CommandResult result = applyToUnits(world, playerId, unitIds, new OrderFactory() {
                @Override
                public Order create(GameWorld world, Unit unit) {
                    UnitType type = unit.type();
                    if (type == UnitType.INFILTRATOR) {
                        // Vehicles can be driven away; a bunker cannot.
                        return target.isBuilding() ? null : new HijackOrder(targetId);
                    }
                    if (type == UnitType.SABOTEUR) {
                        return canBeSabotaged(target) ? new SabotageOrder(targetId) : null;
                    }
                    return null;
                }
            }, false);

            if (!result.isAccepted()) {
                return CommandResult.rejected(target.isBuilding()
                        ? "Send a Saboteur to a structure"
                        : "Send an Infiltrator after a vehicle");
            }
            return result;
        }

        /** Structures and heavy walkers have something to switch off; a rifleman does not. */
        private static boolean canBeSabotaged(Entity target) {
            if (target.isBuilding()) {
                return true;
            }
            UnitType type = ((Unit) target).type();
            return type.isVehicle() || type == UnitType.UBERSOLDAT;
        }

        @Override
        public String describe() {
            return "Infiltrate #" + targetId;
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
