package com.ccwolf.core.ai;

import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.order.AttackMoveOrder;
import com.ccwolf.core.order.HarvestOrder;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.sim.Player;
import java.util.ArrayList;
import java.util.List;

/**
 * The skirmish opponent: a priority-driven build order, a harvester quota, and attack waves
 * that grow as the match drags on.
 *
 * <p>It plays by exactly the same rules as the human — same costs, same queues, same
 * placement restrictions — it just issues its orders through code. Call {@link #update} every
 * tick; it throttles itself to the difficulty's decision interval.
 */
public final class SkirmishAi {

    /** Harvesters the AI wants per refinery. */
    private static final int HARVESTERS_PER_REFINERY = 2;

    /** Ticks between attack waves once the army is big enough. */
    private static final int WAVE_COOLDOWN = 25 * GameWorld.TICKS_PER_SECOND;

    /** A structure hit within this many ticks counts as "under attack" for the defence check. */
    private static final int RECENT_DAMAGE_TICKS = 5 * GameWorld.TICKS_PER_SECOND;

    private final int playerId;
    private final Difficulty difficulty;

    private int waveSize;
    private int nextWaveTick;
    private int placementFailures;
    private final int rallyJitter;

    private final List<Unit> scratch = new ArrayList<Unit>();

    public SkirmishAi(int playerId, Difficulty difficulty) {
        this.playerId = playerId;
        this.difficulty = difficulty;
        this.waveSize = difficulty.firstWaveSize();
        this.nextWaveTick = 60 * GameWorld.TICKS_PER_SECOND;
        this.rallyJitter = playerId * 3;
    }

    public int playerId() {
        return playerId;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public int waveSize() {
        return waveSize;
    }

    public void update(GameWorld world) {
        if (world.isGameOver()) {
            return;
        }
        Player me = world.player(playerId);
        if (me.isDefeated()) {
            return;
        }
        if (world.tick() % difficulty.decisionIntervalTicks() != playerId % difficulty
                .decisionIntervalTicks()) {
            return;
        }

        placeReadyStructure(world);
        manageEconomy(world, me);
        manageConstruction(world, me);
        manageArmy(world, me);
        defendBase(world);
        launchWave(world);
    }

    // --- economy --------------------------------------------------------------------------

    private void manageEconomy(GameWorld world, Player me) {
        int refineries = countBuildings(world, BuildingType.REFINERY);
        int harvesters = countUnits(world, true);
        int wanted = Math.max(1, refineries * HARVESTERS_PER_REFINERY);

        // Idle harvesters are pure waste; put them back to work.
        for (int i = 0; i < world.units().size(); i++) {
            Unit u = world.units().get(i);
            if (u.ownerId() == playerId && u.type().isHarvester() && u.isIdle()) {
                u.setOrder(new HarvestOrder());
            }
        }

        if (harvesters < wanted && me.vehicleQueue().isEmpty()
                && me.canAfford(UnitType.HARVESTER.cost())) {
            world.enqueueUnit(playerId, UnitType.HARVESTER);
        }
    }

    // --- base building --------------------------------------------------------------------

    private void manageConstruction(GameWorld world, Player me) {
        if (!me.structureQueue().isEmpty()) {
            return;
        }
        BuildingType next = nextStructure(world, me);
        if (next != null && me.canAfford(next.cost())) {
            world.enqueueBuilding(playerId, next);
        }
    }

    /** The build order, in priority order. Returns null when the base is complete enough. */
    private BuildingType nextStructure(GameWorld world, Player me) {
        if (countBuildings(world, BuildingType.REFINERY) == 0) {
            return BuildingType.REFINERY;
        }
        if (countBuildings(world, BuildingType.BARRACKS) == 0) {
            return BuildingType.BARRACKS;
        }
        // Keep a little power headroom so production never crawls.
        if (me.powerProduced() - me.powerDrawn() < 40) {
            return BuildingType.GENERATOR;
        }
        if (countBuildings(world, BuildingType.WAR_WORKS) == 0) {
            return BuildingType.WAR_WORKS;
        }
        if (countBuildings(world, BuildingType.FLAK_TURRET) < 2) {
            return BuildingType.FLAK_TURRET;
        }
        if (countBuildings(world, BuildingType.REFINERY) < 2) {
            return BuildingType.REFINERY;
        }
        if (countBuildings(world, BuildingType.BARRACKS) < 2 && me.credits() > 2500) {
            return BuildingType.BARRACKS;
        }
        return null;
    }

    /** Finds a legal spot near the command post for whatever finished building. */
    private void placeReadyStructure(GameWorld world) {
        Player me = world.player(playerId);
        if (!me.structureQueue().isHeadReady()) {
            return;
        }
        BuildingType type = me.structureQueue().head().buildingType();
        Building anchor = anchorFor(world, type);
        if (anchor == null) {
            return;
        }

        int originX = anchor.tileX();
        int originY = anchor.tileY();
        for (int r = 1; r <= 8; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue;
                    }
                    int tx = originX + dx * 3;
                    int ty = originY + dy * 3;
                    if (world.isValidPlacement(playerId, type, tx, ty)
                            && world.hasBuildClearance(type, tx, ty)
                            && world.placeQueued(playerId, tx, ty) != null) {
                        placementFailures = 0;
                        return;
                    }
                }
            }
        }
        placementFailures++;
    }

    /**
     * Turrets go towards the enemy; everything else clusters on the command post. A base that
     * builds all its defences behind itself is not defending anything.
     */
    private Building anchorFor(GameWorld world, BuildingType type) {
        Building post = world.findBuilding(playerId, BuildingType.COMMAND_POST);
        if (post == null) {
            List<Building> mine = ownBuildings(world);
            return mine.isEmpty() ? null : mine.get(0);
        }
        if (type != BuildingType.FLAK_TURRET) {
            return post;
        }
        Entity enemy = world.findNearestEnemyAnywhere(playerId, post.x(), post.y(), true);
        if (enemy == null) {
            return post;
        }
        // Pick whichever of our structures sits closest to the enemy and build out from there.
        Building best = post;
        float bestDist = Float.MAX_VALUE;
        List<Building> mine = ownBuildings(world);
        for (int i = 0; i < mine.size(); i++) {
            float d = mine.get(i).distanceTo(enemy);
            if (d < bestDist) {
                bestDist = d;
                best = mine.get(i);
            }
        }
        return best;
    }

    // --- army -----------------------------------------------------------------------------

    private void manageArmy(GameWorld world, Player me) {
        int army = countUnits(world, false);
        if (army >= difficulty.armyCap()) {
            return;
        }
        if (me.credits() < difficulty.creditReserve()) {
            return;
        }

        Faction faction = me.faction();
        // Alternate between the cheap line unit and the specialist, so waves are mixed.
        UnitType line = faction == Faction.REGIME ? UnitType.SOLDAT : UnitType.PARTISAN;
        UnitType heavy = faction == Faction.REGIME ? UnitType.UBERSOLDAT : UnitType.ROCKETEER;
        UnitType fast = faction == Faction.REGIME ? UnitType.PANZERHUND : UnitType.SCOUT_JEEP;
        UnitType armour = faction == Faction.REGIME ? UnitType.PANZERHUND
                : UnitType.CAPTURED_PANZER;

        if (me.infantryQueue().size() < 2) {
            // Mix the roster up so waves are not all one unit; the roll comes from the world's
            // seeded RNG, which keeps a given seed reproducible while making seeds differ.
            boolean rich = me.credits() > difficulty.creditReserve() + heavy.cost() * 2;
            boolean wantHeavy = rich && world.random().nextInt(100) < 35
                    && world.canProduce(playerId, heavy);
            world.enqueueUnit(playerId, wantHeavy ? heavy : line);
        }
        if (me.vehicleQueue().isEmpty()) {
            // Buy armour when the bank allows it, otherwise something cheap and fast.
            UnitType pick = me.credits() > difficulty.creditReserve() + armour.cost()
                    && world.canProduce(playerId, armour) ? armour : fast;
            if (world.canProduce(playerId, pick)
                    && me.credits() > difficulty.creditReserve() + pick.cost()) {
                world.enqueueUnit(playerId, pick);
            }
        }
    }

    /** Anything shooting at our base pulls every idle defender towards it. */
    private void defendBase(GameWorld world) {
        Building victim = null;
        for (int i = 0; i < world.buildings().size(); i++) {
            Building b = world.buildings().get(i);
            if (b.ownerId() != playerId) {
                continue;
            }
            if (b.wasDamagedWithin(world.tick(), RECENT_DAMAGE_TICKS)) {
                victim = b;
                break;
            }
        }
        if (victim == null) {
            return;
        }
        collectIdleFighters(world, scratch);
        for (int i = 0; i < scratch.size(); i++) {
            scratch.get(i).setOrder(new AttackMoveOrder(victim.tileX(), victim.tileY()));
        }
    }

    private void launchWave(GameWorld world) {
        if (world.tick() < nextWaveTick) {
            return;
        }
        collectIdleFighters(world, scratch);
        if (scratch.size() < waveSize) {
            return;
        }

        Building post = world.findBuilding(playerId, BuildingType.COMMAND_POST);
        float fromX = post != null ? post.x() : scratch.get(0).x();
        float fromY = post != null ? post.y() : scratch.get(0).y();
        Entity target = world.findNearestEnemyAnywhere(playerId, fromX, fromY, true);
        if (target == null) {
            return;
        }

        for (int i = 0; i < scratch.size(); i++) {
            // Spread the wave's aim points a little so twenty units do not all converge on one
            // tile and shove each other off it.
            int spreadX = target.tileX() + (i % 3) - 1 + rallyJitter % 2;
            int spreadY = target.tileY() + (i / 3 % 3) - 1;
            scratch.get(i).setOrder(new AttackMoveOrder(spreadX, spreadY));
        }
        // Jitter the next wave so two AIs on the same map do not march in lockstep forever.
        nextWaveTick = world.tick() + WAVE_COOLDOWN
                + world.random().nextInt(10 * GameWorld.TICKS_PER_SECOND);
        waveSize = Math.min(difficulty.armyCap(), waveSize + 2);
    }

    // --- helpers --------------------------------------------------------------------------

    private void collectIdleFighters(GameWorld world, List<Unit> out) {
        out.clear();
        for (int i = 0; i < world.units().size(); i++) {
            Unit u = world.units().get(i);
            if (u.ownerId() == playerId && !u.type().isHarvester() && u.weapon() != null
                    && u.isIdle()) {
                out.add(u);
            }
        }
    }

    private List<Building> ownBuildings(GameWorld world) {
        List<Building> mine = new ArrayList<Building>();
        for (int i = 0; i < world.buildings().size(); i++) {
            Building b = world.buildings().get(i);
            if (b.ownerId() == playerId && b.isAlive()) {
                mine.add(b);
            }
        }
        return mine;
    }

    private int countBuildings(GameWorld world, BuildingType type) {
        int n = 0;
        for (int i = 0; i < world.buildings().size(); i++) {
            Building b = world.buildings().get(i);
            if (b.ownerId() == playerId && b.type() == type && b.isAlive()) {
                n++;
            }
        }
        return n;
    }

    private int countUnits(GameWorld world, boolean harvesters) {
        int n = 0;
        for (int i = 0; i < world.units().size(); i++) {
            Unit u = world.units().get(i);
            if (u.ownerId() == playerId && u.type().isHarvester() == harvesters) {
                n++;
            }
        }
        return n;
    }
}
