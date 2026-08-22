package com.ccwolf.core.ai;

import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.order.AttackMoveOrder;
import com.ccwolf.core.order.BombardOrder;
import com.ccwolf.core.order.HarvestOrder;
import com.ccwolf.core.order.SabotageOrder;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.sim.InfluenceGrid;
import com.ccwolf.core.squad.Squad;
import com.ccwolf.core.squad.SquadOrder;
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

    /** Ticks between sabotage runs, so the AI does not spend its whole economy on charges. */
    private static final int SABOTAGE_INTERVAL = 50 * GameWorld.TICKS_PER_SECOND;

    /** How many weak points on the front a wave is split between. */
    private static final int WEAK_POINTS = 3;

    /** How far past the front to look when judging how thick the enemy is behind it, in cells. */
    private static final int PROBE_CELLS = 4;

    /** How far out from the base to walk looking for the front, in cells. */
    private static final int DIG_WALK_CELLS = 3;

    /** A structure hit within this many ticks counts as "under attack" for the defence check. */
    private static final int RECENT_DAMAGE_TICKS = 5 * GameWorld.TICKS_PER_SECOND;

    /** How many squads are kept in the ground at once, out of the pool waves are drawn from. */
    private static final int DUG_IN_SQUADS = 2;

    /** How many guns one side keeps. They are expensive and they do not defend themselves. */
    private static final int MAX_GUNS = 2;

    /** How long between buying one gun and considering the next. */
    private static final int GUN_COOLDOWN = 60 * GameWorld.TICKS_PER_SECOND;

    /** How often a battery is given a fresh aim point. */
    private static final int GUN_RETASK = 20 * GameWorld.TICKS_PER_SECOND;

    private final int playerId;
    private final Difficulty difficulty;

    private int waveSize;
    private int nextWaveTick;
    private int placementFailures;
    private final int rallyJitter;

    /**
     * The thinnest places on the front, best first. Fixed arrays and a bounded insertion, so
     * finding them allocates nothing and sorts nothing.
     */
    private final int[] weakCellX = new int[WEAK_POINTS];
    private final int[] weakCellY = new int[WEAK_POINTS];
    private final float[] weakCrust = new float[WEAK_POINTS];
    private int weakCount;
    private int nextGunTick = 120 * GameWorld.TICKS_PER_SECOND;

    private int nextGunOrderTick;

    private int nextSabotageTick = 90 * GameWorld.TICKS_PER_SECOND;

    private final List<Unit> scratch = new ArrayList<Unit>();

    /** Refilled and discarded each decision, like {@link #scratch}, so nothing accumulates. */
    private final List<Squad> squadScratch = new ArrayList<Squad>();

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
        manageRepairs(world, me);
        manageConstruction(world, me);
        reinforceWornSquads(world, me);
        // Guns get first refusal on the vehicle queue, before manageArmy refills it with a
        // tank. Running after it meant the queue was never empty on the tick this looked, so
        // the AI went a whole match without ever buying one.
        manageGuns(world, me);
        manageArmy(world, me);
        manageSpecialOperations(world, me);
        defendBase(world);
        digInTheGarrison(world);
        pressAdvantage(world);
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

    /**
     * Keeps the crews working on anything badly knocked about, and stops paying once a
     * structure is nearly whole again. The AI has to use the base-management tools the player
     * has, or half the loop only exists on one side of the match.
     */
    private void manageRepairs(GameWorld world, Player me) {
        boolean canAfford = me.credits() > difficulty.creditReserve();
        for (int i = 0; i < world.buildings().size(); i++) {
            Building b = world.buildings().get(i);
            if (b.ownerId() != playerId || !b.isAlive()) {
                continue;
            }
            if (!b.isRepairing() && canAfford && b.healthFraction() < REPAIR_THRESHOLD) {
                world.setRepairing(playerId, b.id(), true);
            } else if (b.isRepairing() && (!canAfford || b.healthFraction() > 0.97f)) {
                world.setRepairing(playerId, b.id(), false);
            }
        }
    }

    /** Damage level at which the AI starts paying for repairs. */
    private static final float REPAIR_THRESHOLD = 0.7f;

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
        // An MG nest is a third the price of a turret and stops an infantry rush cold, so it
        // comes first; the Pak gun follows once the enemy can field armour.
        if (countBuildings(world, BuildingType.MG_NEST) < 2) {
            return BuildingType.MG_NEST;
        }
        if (countBuildings(world, BuildingType.FLAK_TURRET) < 1) {
            return BuildingType.FLAK_TURRET;
        }
        if (countBuildings(world, BuildingType.PAK_GUN) < 1) {
            return BuildingType.PAK_GUN;
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
            UnitType pick = pickInfantry(world, me, faction, line, heavy);
            // Infantry come as squads now, but a squad is eight men's worth of credits in one
            // go. A player who has just lost an army and is scraping along cannot afford one at
            // all - and would never rebuild, which is exactly what happened: a beaten AI sat on
            // six hundred credits with no units for the rest of the match. Falling back to one
            // man keeps a losing side in the game.
            if (!world.enqueueSquad(playerId, pick)) {
                world.enqueueUnit(playerId, pick);
            }
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

    /**
     * Chooses the next infantryman.
     *
     * <p>Weighted rather than round-robin: the line unit is always the backbone, and the
     * specialists appear once there is money spare for them. The rolls come from the world's
     * seeded RNG, so a given seed still replays identically.
     */
    private UnitType pickInfantry(GameWorld world, Player me, Faction faction, UnitType line,
                                  UnitType heavy) {
        boolean rich = me.credits() > difficulty.creditReserve() + heavy.cost() * 2;
        int roll = world.random().nextInt(100);

        UnitType marksman = faction == Faction.REGIME
                ? UnitType.SCHARFSCHUTZE : UnitType.MARKSMAN;
        UnitType specialist = faction == Faction.REGIME
                ? UnitType.STURMPIONIER : UnitType.GRENADIER;

        if (rich && roll < 30 && world.canProduce(playerId, heavy)) {
            return heavy;
        }
        if (roll < 50 && world.canProduce(playerId, specialist)) {
            return specialist;
        }
        if (rich && roll < 65 && world.canProduce(playerId, marksman)) {
            return marksman;
        }
        return line;
    }

    /**
     * The Resistance's cheapest way to hurt a base it cannot storm: send a saboteur at
     * whatever is shooting, and switch it off.
     */
    private void manageSpecialOperations(GameWorld world, Player me) {
        if (me.faction() != Faction.RESISTANCE || world.tick() < nextSabotageTick) {
            return;
        }
        if (!world.canProduce(playerId, UnitType.SABOTEUR)
                || me.credits() < difficulty.creditReserve() + UnitType.SABOTEUR.cost()) {
            return;
        }

        // Send any saboteur already standing about before paying for another.
        for (int i = 0; i < world.units().size(); i++) {
            Unit u = world.units().get(i);
            if (u.ownerId() != playerId || u.type() != UnitType.SABOTEUR || !u.isIdle()) {
                continue;
            }
            Entity target = world.findNearestEnemyAnywhere(playerId, u.x(), u.y(), true);
            if (target != null) {
                u.setOrder(new SabotageOrder(target.id()));
                nextSabotageTick = world.tick() + SABOTAGE_INTERVAL;
                return;
            }
        }
        world.enqueueUnit(playerId, UnitType.SABOTEUR);
        nextSabotageTick = world.tick() + SABOTAGE_INTERVAL / 2;
    }

    /**
     * Buys guns, and points them at something.
     *
     * <p>Shaped like {@code manageSpecialOperations}: gated on a cooldown, on prerequisites and
     * on having money spare, and it reuses a gun it already owns before paying for another.
     *
     * <p>Which gun is a credit threshold rather than a roll. {@code pickInfantry} spends exactly
     * one {@code nextInt(100)} per decision, and the invariant the whole simulation rests on is
     * that the number of draws per tick cannot depend on which branch was taken - so nothing
     * here touches the random number generator at all.
     */
    private void manageGuns(GameWorld world, Player me) {
        int guns = countGuns(world);

        if (guns < MAX_GUNS && world.tick() >= nextGunTick
                && me.vehicleQueue().isEmpty()) {
            UnitType pick = gunFor(world, me);
            if (pick != null && world.canProduce(playerId, pick)
                    && me.credits() > difficulty.creditReserve() + pick.cost()) {
                world.enqueueUnit(playerId, pick);
                nextGunTick = world.tick() + GUN_COOLDOWN;
            }
        }
        if (guns == 0) {
            return;
        }

        // Re-task on an interval. A gun left on its original order goes on shelling a field
        // that the war moved away from twenty minutes ago.
        if (world.tick() < nextGunOrderTick) {
            return;
        }
        nextGunOrderTick = world.tick() + GUN_RETASK;

        Entity aim = counterBatteryTarget(world);
        if (aim == null) {
            Building post = world.findBuilding(playerId, BuildingType.COMMAND_POST);
            float fromX = post != null ? post.x() : anyAttackerX();
            float fromY = post != null ? post.y() : anyAttackerY();
            aim = world.findNearestEnemyAnywhere(playerId, fromX, fromY, false);
        }
        if (aim == null) {
            return;
        }

        for (int i = 0; i < world.units().size(); i++) {
            Unit u = world.units().get(i);
            if (u.ownerId() == playerId && u.type().isArtillery()) {
                world.issueOrder(playerId, u, new BombardOrder(aim.tileX(), aim.tileY()));
            }
        }
    }

    /**
     * An enemy battery that has fired recently, if there is one.
     *
     * <p>Counter-battery, made real for the side that cannot see. The reveal that firing causes
     * is a fog rule, and the AI has never read fog - so for it the mechanic has to be an
     * explicit preference, keyed on the same thing the reveal is: a gun that has just fired.
     */
    private Entity counterBatteryTarget(GameWorld world) {
        Entity best = null;
        int firedLatest = -1;
        for (int i = 0; i < world.units().size(); i++) {
            Unit u = world.units().get(i);
            if (u.ownerId() == playerId || !u.isAlive() || !u.type().isArtillery()) {
                continue;
            }
            if (world.tick() - u.lastFiredTick() > GameWorld.COUNTER_BATTERY_TICKS) {
                continue;
            }
            if (u.lastFiredTick() > firedLatest) {
                firedLatest = u.lastFiredTick();
                best = u;
            }
        }
        return best;
    }

    /** The heaviest gun we can afford now, or the cheap one, or nothing. */
    private UnitType gunFor(GameWorld world, Player me) {
        if (me.faction() == Faction.REGIME) {
            boolean rich = me.credits()
                    > difficulty.creditReserve() + UnitType.RESONANZKANONE.cost();
            if (rich && world.canProduce(playerId, UnitType.RESONANZKANONE)) {
                return UnitType.RESONANZKANONE;
            }
            return UnitType.NEBELWERFER;
        }
        return UnitType.FELDKANONE;
    }

    private int countGuns(GameWorld world) {
        int n = 0;
        for (int i = 0; i < world.units().size(); i++) {
            Unit u = world.units().get(i);
            if (u.ownerId() == playerId && u.isAlive() && u.type().isArtillery()) {
                n++;
            }
        }
        return n;
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
        collectIdleSquads(world, squadScratch);
        for (int i = 0; i < squadScratch.size(); i++) {
            world.orderSquadTo(playerId, squadScratch.get(i), SquadOrder.ATTACK_MOVE,
                    victim.tileX(), victim.tileY());
        }
        collectIdleFighters(world, scratch);
        for (int i = 0; i < scratch.size(); i++) {
            scratch.get(i).setOrder(new AttackMoveOrder(victim.tileX(), victim.tileY()));
        }
    }

    /**
     * The thinnest places on the front, found by looking at what is behind it.
     *
     * <p>A front cell alone says nothing about whether attacking there is a good idea — the
     * enemy's strongest point is a front cell too. What matters is the crust: march from the
     * front cell toward the enemy for a few cells and add up how much of theirs is stacked
     * along the way. A low total is a thin shell with nothing behind it; a high one is the
     * approach to their base.
     *
     * <p>Row-major, strictly-less insertion into a fixed three-slot array, so ties go to the
     * first cell found and there is no sort, no allocation and no dependence on iteration
     * order. Nothing here touches the random number generator, and nothing here may: the AI
     * runs before the world steps, so on the first decision of a match the field is all zeros
     * and this finds nothing at all. Every caller falls through to its old target in that case
     * rather than returning, because a return would make the number of draws per tick depend
     * on the state of the influence field.
     */
    private void findWeakPoints(GameWorld world) {
        weakCount = 0;
        int across = world.controlCellsAcross();
        int down = world.controlCellsDown();
        for (int cy = 0; cy < down; cy++) {
            for (int cx = 0; cx < across; cx++) {
                if (!world.isFrontCell(playerId, cx, cy)) {
                    continue;
                }
                // Which way the enemy lies: the neighbour that is most theirs. Fixed order,
                // strictly less, so a tie keeps the earlier direction.
                int stepX = 0;
                int stepY = 0;
                float deepest = 0f;
                float west = world.controlAtCell(playerId, cx - 1, cy);
                if (west < deepest) {
                    deepest = west;
                    stepX = -1;
                    stepY = 0;
                }
                float east = world.controlAtCell(playerId, cx + 1, cy);
                if (east < deepest) {
                    deepest = east;
                    stepX = 1;
                    stepY = 0;
                }
                float north = world.controlAtCell(playerId, cx, cy - 1);
                if (north < deepest) {
                    deepest = north;
                    stepX = 0;
                    stepY = -1;
                }
                float south = world.controlAtCell(playerId, cx, cy + 1);
                if (south < deepest) {
                    deepest = south;
                    stepX = 0;
                    stepY = 1;
                }
                if (stepX == 0 && stepY == 0) {
                    continue;
                }

                float crust = 0f;
                for (int step = 1; step <= PROBE_CELLS; step++) {
                    float ahead = world.controlAtCell(
                            playerId, cx + stepX * step, cy + stepY * step);
                    if (ahead < 0f) {
                        crust -= ahead;
                    }
                }
                keepWeakPoint(cx, cy, crust);
            }
        }
    }

    /** Bounded insertion into the three slots, thinnest first. */
    private void keepWeakPoint(int cellX, int cellY, float crust) {
        int slot = weakCount < WEAK_POINTS ? weakCount : WEAK_POINTS - 1;
        if (weakCount == WEAK_POINTS && crust >= weakCrust[slot]) {
            return;
        }
        while (slot > 0 && crust < weakCrust[slot - 1]) {
            weakCrust[slot] = weakCrust[slot - 1];
            weakCellX[slot] = weakCellX[slot - 1];
            weakCellY[slot] = weakCellY[slot - 1];
            slot--;
        }
        weakCrust[slot] = crust;
        weakCellX[slot] = cellX;
        weakCellY[slot] = cellY;
        if (weakCount < WEAK_POINTS) {
            weakCount++;
        }
    }

    /**
     * Where the nth attacker should be sent: round-robin across the weak points found.
     *
     * <p>Round-robin rather than all-on-the-best, because a wave that piles onto one cell is
     * the {@code manageGuns} mistake — every gun sent to the same spot — with more men.
     */
    private int weakAimX(int index, int fallbackTileX) {
        if (weakCount == 0) {
            return fallbackTileX;
        }
        return weakCellX[index % weakCount] * InfluenceGrid.CELL_TILES
                + InfluenceGrid.CELL_TILES / 2;
    }

    private int weakAimY(int index, int fallbackTileY) {
        if (weakCount == 0) {
            return fallbackTileY;
        }
        return weakCellY[index % weakCount] * InfluenceGrid.CELL_TILES
                + InfluenceGrid.CELL_TILES / 2;
    }

    /**
     * Puts the squads that are standing about to work with a shovel.
     *
     * <p>An army waiting for the next wave used to stand in a heap by the factory doing
     * nothing. Sending it to dig instead costs nothing — these are exactly the squads with no
     * other job — and turns the approach to the base into ground an attacker has to pay for.
     *
     * <p>Only a couple of squads at a time, and always on the enemy's side of the base. Digging
     * the whole army in would empty the pool that waves are drawn from, and a line dug behind
     * the command post defends nothing, in exactly the way turrets built behind it defend
     * nothing.
     *
     * <p>Where the line goes is now walked out of the control field rather than measured off
     * the vector to the nearest enemy. Steepest fall in control, a whole cell at a time, until
     * it reaches the front or runs out of steps — so the trench is cut where the ground
     * actually changes hands, which is not usually on the straight line to the nearest thing
     * that shoots. It also takes no square root: whole cells and signs throughout.
     */
    private void digInTheGarrison(GameWorld world) {
        Building post = world.findBuilding(playerId, BuildingType.COMMAND_POST);
        if (post == null) {
            return;
        }
        Entity enemy = world.findNearestEnemyAnywhere(playerId, post.x(), post.y(), true);
        if (enemy == null) {
            return;
        }

        collectIdleSquads(world, squadScratch);
        int dug = 0;
        for (int i = 0; i < squadScratch.size(); i++) {
            if (squadScratch.get(i).order() == SquadOrder.ENTRENCH) {
                dug++;
            }
        }

        int cellX = post.tileX() / InfluenceGrid.CELL_TILES;
        int cellY = post.tileY() / InfluenceGrid.CELL_TILES;
        int stepX = 0;
        int stepY = 0;
        for (int step = 0; step < DIG_WALK_CELLS; step++) {
            int nextX = 0;
            int nextY = 0;
            float lowest = world.controlAtCell(playerId, cellX, cellY);
            float west = world.controlAtCell(playerId, cellX - 1, cellY);
            float east = world.controlAtCell(playerId, cellX + 1, cellY);
            float north = world.controlAtCell(playerId, cellX, cellY - 1);
            float south = world.controlAtCell(playerId, cellX, cellY + 1);
            // Fixed order, strictly less, so a tie keeps the earlier direction.
            if (west < lowest) {
                lowest = west;
                nextX = -1;
                nextY = 0;
            }
            if (east < lowest) {
                lowest = east;
                nextX = 1;
                nextY = 0;
            }
            if (north < lowest) {
                lowest = north;
                nextX = 0;
                nextY = -1;
            }
            if (south < lowest) {
                lowest = south;
                nextX = 0;
                nextY = 1;
            }
            if (nextX == 0 && nextY == 0) {
                break;
            }
            cellX += nextX;
            cellY += nextY;
            stepX = nextX;
            stepY = nextY;
            if (world.isFrontCell(playerId, cellX, cellY)) {
                break;
            }
        }

        if (stepX == 0 && stepY == 0) {
            // No gradient to follow - the field is flat here, or this is the first decision of
            // the match and it is all zeros. Head for the enemy's cell instead, which is the
            // old behaviour reduced to whole cells and signs.
            stepX = Integer.signum(enemy.tileX() / InfluenceGrid.CELL_TILES - cellX);
            stepY = Integer.signum(enemy.tileY() / InfluenceGrid.CELL_TILES - cellY);
            if (stepX == 0 && stepY == 0) {
                return;
            }
            cellX += stepX * DIG_WALK_CELLS;
            cellY += stepY * DIG_WALK_CELLS;
        }

        int lineX = cellX * InfluenceGrid.CELL_TILES + InfluenceGrid.CELL_TILES / 2;
        int lineY = cellY * InfluenceGrid.CELL_TILES + InfluenceGrid.CELL_TILES / 2;

        for (int i = 0; i < squadScratch.size() && dug < DUG_IN_SQUADS; i++) {
            Squad squad = squadScratch.get(i);
            if (squad.order() == SquadOrder.ENTRENCH) {
                continue;
            }
            // A frontage rather than a point: two squads on the same tile would be two squads
            // fighting each other's separation, and one hole between them. Sideways means
            // across the direction of the walk.
            int spread = dug * 2 - 1;
            int tileX = lineX - stepY * spread * 3;
            int tileY = lineY + stepX * spread * 3;
            if (!world.map().inBounds(tileX, tileY) || !world.map().isPassable(tileX, tileY)) {
                continue;
            }
            world.orderSquadTo(playerId, squad, SquadOrder.ENTRENCH, tileX, tileY);
            dug++;
        }
    }

    /**
     * Throws everything at the enemy once their army is broken.
     *
     * <p>Without this the AI has no finishing blow. It attacks on a cooldown with whatever
     * happens to be standing idle, so an opponent reduced to nothing but buildings is left
     * alone long enough to rebuild — matches turned into forty-minute grinds where one side
     * hit zero units and was never followed up. Squads made this worse, because a squad that
     * stops to fight stays committed to that fight and never returns to the idle pool.
     */
    private void pressAdvantage(GameWorld world) {
        int mine = countUnits(world, false);
        if (mine < difficulty.firstWaveSize()) {
            return;
        }
        int theirs = countEnemyFighters(world);
        if (theirs * BREAKTHROUGH_RATIO > mine) {
            return;
        }

        Building post = world.findBuilding(playerId, BuildingType.COMMAND_POST);
        float fromX = post != null ? post.x() : 0f;
        float fromY = post != null ? post.y() : 0f;
        Entity target = world.findNearestEnemyAnywhere(playerId, fromX, fromY, true);
        if (target == null) {
            return;
        }

        // Through the thinnest part of what is left of their line, rather than at whatever
        // happens to be nearest. When their army really is broken there is barely a front to
        // find, and this falls back to the nearest enemy, which is the old behaviour.
        findWeakPoints(world);

        // Everything, not just what is idle.
        List<Squad> all = world.squads().all();
        for (int i = 0; i < all.size(); i++) {
            Squad squad = all.get(i);
            if (squad.ownerId() == playerId && !squad.isWipedOut()) {
                world.orderSquadTo(playerId, squad, SquadOrder.ATTACK_MOVE,
                        weakAimX(i, target.tileX()) + (i % 3) * 2 - 2,
                        weakAimY(i, target.tileY()) + (i / 3 % 3) * 2 - 2);
            }
        }
        collectIdleFighters(world, scratch);
        for (int i = 0; i < scratch.size(); i++) {
            scratch.get(i).setOrder(new AttackMoveOrder(
                    weakAimX(i, target.tileX()), weakAimY(i, target.tileY())));
        }
    }

    /** Armed, mobile enemies - what actually stands between us and their base. */
    private int countEnemyFighters(GameWorld world) {
        int count = 0;
        for (int i = 0; i < world.units().size(); i++) {
            Unit u = world.units().get(i);
            if (u.ownerId() != playerId && u.isAlive() && u.weapon() != null
                    && !u.type().isHarvester()) {
                count++;
            }
        }
        return count;
    }

    private void launchWave(GameWorld world) {
        if (world.tick() < nextWaveTick) {
            return;
        }
        collectIdleSquads(world, squadScratch);
        collectIdleFighters(world, scratch);
        // Counted in men rather than in units of command, so the difficulty tuning that was
        // written against individuals still means what it meant.
        if (idleStrength() < waveSize) {
            return;
        }

        Building post = world.findBuilding(playerId, BuildingType.COMMAND_POST);
        float fromX = post != null ? post.x() : anyAttackerX();
        float fromY = post != null ? post.y() : anyAttackerY();
        Entity target = world.findNearestEnemyAnywhere(playerId, fromX, fromY, true);
        if (target == null) {
            return;
        }

        // Where the enemy is thinnest, not where they are nearest. Up to three points, with
        // squads round-robined across them, so the wave arrives genuinely abreast instead of
        // all on one tile.
        //
        // No return between here and the cooldown draw below, whatever the field says. That
        // draw is unconditional once the guards above have passed, so bailing out on an empty
        // front would make the number of draws per tick a function of the influence field -
        // which is exactly the invariant that keeps two runs of the same seed identical.
        findWeakPoints(world);

        // Spread the aim points so the wave arrives across a frontage rather than piling onto
        // one tile. A squad already spreads its own men, so this is per squad, not per man.
        for (int i = 0; i < squadScratch.size(); i++) {
            int spreadX = weakAimX(i, target.tileX()) + (i % 3) * 2 - 2 + rallyJitter % 2;
            int spreadY = weakAimY(i, target.tileY()) + (i / 3 % 3) * 2 - 2;
            world.orderSquadTo(playerId, squadScratch.get(i), SquadOrder.ATTACK_MOVE,
                    spreadX, spreadY);
        }
        for (int i = 0; i < scratch.size(); i++) {
            int spreadX = weakAimX(i, target.tileX()) + (i % 3) - 1 + rallyJitter % 2;
            int spreadY = weakAimY(i, target.tileY()) + (i / 3 % 3) - 1;
            scratch.get(i).setOrder(new AttackMoveOrder(spreadX, spreadY));
        }
        // Jitter the next wave so two AIs on the same map do not march in lockstep forever.
        nextWaveTick = world.tick() + WAVE_COOLDOWN
                + world.random().nextInt(10 * GameWorld.TICKS_PER_SECOND);
        waveSize = Math.min(difficulty.armyCap(), waveSize + 2);
    }

    // --- helpers --------------------------------------------------------------------------

    /**
     * Armed units of ours that are not doing anything and not in a squad.
     *
     * <p>Squad members are excluded because a squad is commanded as one thing — ordering its
     * members individually would break them out of it, one man at a time, and quietly undo the
     * whole feature.
     */
    /** How far ahead in fighting men we must be before committing everything. */
    private static final int BREAKTHROUGH_RATIO = 3;

    /** Below this fraction of strength, a squad is worth topping up rather than leaving. */
    private static final float REINFORCE_BELOW = 0.7f;

    /** Where a wave is measured from when the command post has been lost. */
    private float anyAttackerX() {
        if (!squadScratch.isEmpty()) {
            return squadScratch.get(0).anchorX();
        }
        return scratch.isEmpty() ? 0f : scratch.get(0).x();
    }

    private float anyAttackerY() {
        if (!squadScratch.isEmpty()) {
            return squadScratch.get(0).anchorY();
        }
        return scratch.isEmpty() ? 0f : scratch.get(0).y();
    }

    private void collectIdleFighters(GameWorld world, List<Unit> out) {
        out.clear();
        for (int i = 0; i < world.units().size(); i++) {
            Unit u = world.units().get(i);
            // A weapon with a dead zone is excluded on purpose. Everything downstream of this
            // list - defendBase, pressAdvantage, launchWave - issues an attack-move, and an
            // attack-move walks a unit onto its target. Send a gun on one and it marches into
            // the enemy base at point-blank range and dies without firing a shot.
            if (u.ownerId() == playerId && !u.type().isHarvester() && u.weapon() != null
                    && !u.weapon().hasMinRange() && u.isIdle() && !u.isInSquad()) {
                out.add(u);
            }
        }
    }

    /** Our squads that have finished what they were doing and are standing about. */
    private void collectIdleSquads(GameWorld world, List<Squad> out) {
        out.clear();
        List<Squad> all = world.squads().all();
        for (int i = 0; i < all.size(); i++) {
            Squad squad = all.get(i);
            // A dug-in squad counts as idle. Digging is what a formation does while it is
            // waiting, not a commitment it has made: the hole stays in the ground when the
            // squad leaves for a wave, so the next squad through inherits the work rather
            // than starting again. Treating it as busy would give an AI that dug in once and
            // never attacked again.
            if (squad.ownerId() == playerId && !squad.isWipedOut()
                    && (squad.order() == SquadOrder.HOLD
                        || squad.order() == SquadOrder.ENTRENCH)) {
                out.add(squad);
            }
        }
    }

    /** Men available to attack, counting squad members - so wave sizing keeps its old meaning. */
    private int idleStrength() {
        int total = squadScratch.size();
        for (int i = 0; i < squadScratch.size(); i++) {
            total += squadScratch.get(i).strength() - 1;
        }
        return total + scratch.size();
    }

    /**
     * Tops up squads that have taken losses.
     *
     * <p>Rebuilding a worn squad is usually better value than training a fresh one, and it is
     * what stops an AI army slowly turning into a crowd of three-man remnants.
     */
    private void reinforceWornSquads(GameWorld world, Player me) {
        if (me.credits() < difficulty.creditReserve()) {
            return;
        }
        List<Squad> all = world.squads().all();
        for (int i = 0; i < all.size(); i++) {
            Squad squad = all.get(i);
            if (squad.ownerId() != playerId || squad.isWipedOut()) {
                continue;
            }
            if (squad.strengthFraction() <= REINFORCE_BELOW && me.infantryQueue().size() < 3) {
                world.reinforceSquad(playerId, squad);
                return; // One at a time, so replacements are not all queued at once.
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
