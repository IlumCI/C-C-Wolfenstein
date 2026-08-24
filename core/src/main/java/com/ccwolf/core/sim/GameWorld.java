package com.ccwolf.core.sim;

import com.ccwolf.core.combat.Doctrines;
import com.ccwolf.core.combat.Earthworks;
import com.ccwolf.core.combat.Suppression;
import com.ccwolf.core.combat.Weapon;
import com.ccwolf.core.combat.WeaponClass;
import com.ccwolf.core.diag.TickProfiler;
import com.ccwolf.core.economy.ProductionItem;
import com.ccwolf.core.economy.ProductionQueue;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Doctrine;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.event.GameEvent;
import com.ccwolf.core.fog.FogGrid;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.core.order.HarvestOrder;
import com.ccwolf.core.order.JoinSquadOrder;
import com.ccwolf.core.order.MoveOrder;
import com.ccwolf.core.order.RoutOrder;
import com.ccwolf.core.order.Order;
import com.ccwolf.core.order.SquadMemberOrder;
import com.ccwolf.core.squad.Formation;
import com.ccwolf.core.squad.Squad;
import com.ccwolf.core.squad.SquadOrder;
import com.ccwolf.core.squad.SquadRegistry;
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

    /**
     * How often each side's record of what it has seen is refreshed.
     *
     * <p>The same cadence as fog, and for the same reason — nothing about it needs to be exact.
     * Unlike fog, it runs whether or not fog is switched on, because the harness and every AI
     * test play with fog off and a rule that vanished there would be a rule measured in a world
     * it does not apply to.
     */
    private static final int SPOTTING_INTERVAL = 5;

    /**
     * How often the influence field is rebuilt.
     *
     * <p>The same cadence as spotting, and independent of it — in particular this is not a
     * divisor of MORALE_INTERVAL and nothing should be built on the assumption that it is. One
     * consequence worth knowing: a squad's broken flag only refreshes every morale tick, so
     * three influence passes in four see a stale one. Harmless at this resolution.
     */
    private static final int CONTROL_INTERVAL = 5;

    /**
     * What an artillery piece is worth as an occupier of ground.
     *
     * <p>Very little. A battery holds nothing where it stands — that is the whole premise of
     * counter-battery, and of it being kept out of the AI's idle-fighter list.
     */
    private static final float ARTILLERY_HOLD = 0.2f;

    /**
     * How long a piece of ground stays worth shelling after the last man saw it.
     *
     * <p>Fifteen seconds: long enough that a spotter can look, duck back and still call the
     * shot, short enough that a battery cannot keep firing at a map it walked across once.
     */
    public static final int SPOTTING_MEMORY = 15 * TICKS_PER_SECOND;

    /**
     * How long a battery stays visible to the enemy after firing.
     *
     * <p>Ten seconds — long enough to be shot at, short enough that a gun which fires and moves
     * has bought something. This is the whole of counter-battery: the price of shooting is that
     * everyone now knows where you are.
     */
    public static final int COUNTER_BATTERY_TICKS = 10 * TICKS_PER_SECOND;

    /** How often an idle gun goes looking for a fire mission. Staggered by unit id. */
    private static final int ARTILLERY_ACQUIRE_INTERVAL = 10;

    /**
     * How many men have to be standing together before a gun will spend a shell on them.
     *
     * <p>The most load-bearing number in the artillery model. At one, a battery plinks away at
     * every lone scout on the map and becomes miserable to play against; at two, it is
     * something you provoke by massing - which is what makes it the answer to a dug-in line
     * rather than a tax on moving at all.
     */
    private static final int MIN_WORTHWHILE_CLUSTER = 2;

    /** How often uranium seams creep back, in ticks. */
    private static final int ORE_REGROW_INTERVAL = 100;

    /** Uranium added to each seam per regrowth pass. */
    private static final int ORE_REGROW_AMOUNT = 3;

    /** How long sabotage keeps a structure off line. */
    private static final int SABOTAGE_STRUCTURE_TICKS = 20 * TICKS_PER_SECOND;

    /** How long a sabotaged walker stays frozen. */
    private static final int SABOTAGE_UNIT_TICKS = 12 * TICKS_PER_SECOND;

    /** How often concealment is re-checked against nearby enemies. */
    private static final int STEALTH_CHECK_INTERVAL = 5;

    /** How close an enemy must be to see a concealed unit, in tiles. */
    public static final float STEALTH_REVEAL_RADIUS = 2.6f;

    /**
     * Ticks between repair instalments, and hit points per instalment.
     *
     * <p>These were originally three times faster, and the result was that a base under
     * sustained attack spent credits on repairs faster than four harvesters could bring
     * uranium in — the AI simply went bankrupt patching walls. Repairs are meant to be a
     * steady drain you choose to accept, not a race the economy loses.
     */
    private static final int REPAIR_INTERVAL = 10;

    private static final int REPAIR_HP_PER_STEP = 8;

    /**
     * The most a unit may be displaced by separation in one tick, as a multiple of its radius.
     *
     * <p>A crowd sums a lot of small pushes into one large one, and left uncapped a unit in the
     * middle of a press is flung further than it could ever move under its own power.
     *
     * <p>Sized against the radius rather than the walking speed, which was the first thing
     * tried and was wrong: a clamp of a fraction of a tick's walk is far smaller than the
     * overlap of two stacked units, so a crowd took a second or more to come apart. Spawn
     * points never cleared, {@code isTileCrowded} kept rejecting, and the AI quietly stopped
     * producing units altogether. One radius a tick separates a stacked pair in two or three
     * ticks while still bounding the pathological case.
     */
    private static final float SEPARATION_MAX_STEP = 1.0f;

    /** How far out from a factory door a spawning unit or squad will look for room. */
    private static final int SPAWN_SEARCH_RADIUS = 8;

    /** Ticks between a squad's target sweeps, staggered by squad id. */
    private static final int SQUAD_SCAN_INTERVAL = 6;

    /** How far past its reach a squad will hang on to a target before letting go. */
    private static final float SQUAD_LEASH = 3f;

    /** How far a member may be from its slot before the squad waits for it. */
    private static final float SQUAD_COHESION = 3.5f;

    /** How long a squad will wait for a straggler before marching without him. */
    private static final int MAX_COHESION_WAIT = 40;

    /** Morale and stamina are judged once a second: they are meant to lag events, not track them. */
    private static final int MORALE_INTERVAL = TICKS_PER_SECOND;

    /**
     * Morale lost per second at full casualties, scaled by the square of what is missing.
     *
     * <p>Set against two things pulling in opposite directions. Too low and a squad is wiped
     * out long before its nerve gives way, which makes the whole system decorative. Too high
     * and attacks dissolve before they land: at 34, twenty seeds gave eleven stalemates
     * against eight at this value, because every assault broke on contact and the AI has no
     * way yet to concentrate force or exploit a success.
     *
     * <p>That dependency is worth naming. The stalemate rate here is limited by how well the
     * opponent fights, not by the combat model, and the operational AI is where it gets fixed.
     */
    private static final float MORALE_LOSS_WEIGHT = 22f;

    /** Morale lost per second with the whole squad pinned. */
    private static final float MORALE_PINNED_WEIGHT = 5f;

    /** The share of full morale a squad wiped down to nothing could still recover to. */
    private static final float WORN_MORALE_FLOOR = 0.45f;

    /** Morale regained per second out of contact, before exhaustion is applied. */
    private static final float MORALE_RECOVERY = 4f;

    /** How long a fresh army's squads run for once broken. */
    private static final int BREAK_TICKS = 12 * TICKS_PER_SECOND;

    /** What a rallied squad comes back with. Shaken, not restored. */
    private static final int RALLY_MORALE = 45;

    /** An enemy this close keeps a broken squad running. */
    private static final float RALLY_SAFE_RADIUS = 10f;

    /** Ticks within which damage or firing still counts as being in contact. */
    private static final int RECENT_CONTACT_TICKS = 3 * TICKS_PER_SECOND;

    /** Ticks within which losing a man still counts as being in contact. */
    private static final int RECENT_CASUALTY_TICKS = 8 * TICKS_PER_SECOND;

    /** Fraction of an army that must be in contact before it starts tiring. */
    private static final float STAMINA_CONTACT_THRESHOLD = 0.2f;

    /** Stamina lost per second with the whole army engaged. */
    private static final float STAMINA_DRAIN = 2f;

    /** Stamina regained per second out of contact. */
    private static final int STAMINA_RECOVERY = 1;

    /**
     * How fast the anchor walks, as a fraction of what its members can manage.
     *
     * <p>Must be less than one. At full speed a member that is out of station can never take it
     * up, because the slot it is chasing runs away exactly as fast as it does: the squad stalls,
     * waits, creeps forward, stalls again. The first version of this walked a squad one and a
     * third tiles in thirty seconds. The margin is what lets a formation form up while moving.
     */
    private static final float ANCHOR_SPEED_FRACTION = 0.8f;

    /** Repairing a structure from scrap costs this fraction of building it new. */
    private static final float REPAIR_COST_FACTOR = 0.5f;

    /** How often an idle armed unit looks around for something to shoot. */
    private static final int ACQUIRE_INTERVAL = 8;

    private final TileMap map;
    private final OccupancyGrid grid;
    private final Mover mover = new Mover();

    /** Squads, and the roster of who is in them. */
    private final SquadRegistry squads = new SquadRegistry();

    /** Reused by the squad pass so walking a formation allocates nothing. */
    private final float[] slotScratch = new float[2];

    /**
     * Where the tick goes. Off unless something switches it on, so the normal path pays one
     * predictable branch per phase rather than a pair of nanoTime calls.
     */
    private final TickProfiler profiler = new TickProfiler();

    {
        mover.setProfiler(profiler);
    }
    private final Random random;
    private final SpatialIndex spatialIndex;

    private final List<Player> players = new ArrayList<Player>();
    private final Map<Integer, Entity> entitiesById = new HashMap<Integer, Entity>();
    private final List<Unit> units = new ArrayList<Unit>();
    private final List<Building> buildings = new ArrayList<Building>();
    private final List<FogGrid> fogGrids = new ArrayList<FogGrid>();

    /**
     * What each side has seen, and when. One per player, alongside the fog grids but
     * deliberately not part of them — see {@link SightMemory} for why.
     */
    private final List<SightMemory> sightMemories = new ArrayList<SightMemory>();

    /**
     * How much of the map each side holds. One per player, parallel to the fog and sight lists.
     *
     * <p>Recomputed from scratch every pass — nothing here carries over between ticks, so it
     * cannot drift independently of the units and buildings it is derived from.
     */
    private final List<InfluenceGrid> influences = new ArrayList<InfluenceGrid>();

    /** What each cell's ground is worth from the earthworks on it. Shared: trenches have no owner. */
    private float[] groundBonus;

    /** Bumped whenever the influence field is rebuilt, so a renderer knows when to rebake. */
    private int controlVersion;

    /** Ticks between doses for a man standing in gas: half a second per dose. */
    public static final int GAS_HURT_EVERY = 10;

    /** Hit points per level of concentration per dose. A few seconds of a full cloud kills. */
    public static final int GAS_DAMAGE_PER_DOSE = 9;

    /** Suppression per level per dose: men in gas are not returning fire. */
    public static final int GAS_SUPPRESSION_PER_DOSE = 4;

    /** Rounds in the air. Empty until something fires indirectly. */
    private final ShellLayer shells = new ShellLayer();

    /** Gas on the ground. Empty until a Gas War player fields the thing that vents it. */
    private final GasLayer gas;
    private final List<GameEvent> events = new ArrayList<GameEvent>();

    private final List<Unit> queryScratch = new ArrayList<Unit>();

    /**
     * Its own list, not queryScratch.
     *
     * <p>Choosing a fire mission scores every candidate against every other candidate, which
     * means holding one list while walking it twice. Borrowing the shared scratch would work
     * until the day something inside that loop calls findNearestEnemy, and then it would fail
     * in a way that only shows up under load.
     */
    private final List<Unit> bombardScratch = new ArrayList<Unit>();

    private int nextEntityId = 1;
    private int tick;
    private boolean gameOver;
    private int winnerId = -1;
    private boolean fogEnabled = true;

    public GameWorld(TileMap map, long seed) {
        this.map = map;
        this.gas = new GasLayer(map.width(), map.height());
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

    /**
     * Forms the given units into a squad, and puts them under its orders.
     *
     * <p>Squad ids come from the same counter as entity ids, so a squad id can never be
     * mistaken for a unit id — the command layer deals in both and they cross the same seam.
     *
     * @return the new squad, or null if fewer than two eligible units were offered
     */
    public Squad formSquad(int ownerId, List<Unit> members) {
        if (members == null || members.size() < 2) {
            return null;
        }
        int[] ids = new int[Math.min(members.size(), Formation.MAX_SLOTS)];
        int count = 0;
        UnitType type = null;
        float sumX = 0f;
        float sumY = 0f;
        for (int i = 0; i < members.size() && count < ids.length; i++) {
            Unit unit = members.get(i);
            if (unit == null || !unit.isAlive() || unit.ownerId() != ownerId) {
                continue;
            }
            // One type per squad. Mixed squads would multiply the formation, morale and
            // production work for a gain that has not been asked for yet.
            if (type == null) {
                type = unit.type();
            } else if (unit.type() != type) {
                continue;
            }
            detachFromSquad(unit);
            ids[count++] = unit.id();
            sumX += unit.x();
            sumY += unit.y();
        }
        if (count < 2) {
            return null;
        }
        if (count < ids.length) {
            int[] trimmed = new int[count];
            System.arraycopy(ids, 0, trimmed, 0, count);
            ids = trimmed;
        }

        Squad squad = new Squad(nextEntityId++, ownerId, type, ids, sumX / count, sumY / count,
                Doctrines.spread(player(ownerId).doctrine()));
        squads.add(squad);
        for (int slot = 0; slot < ids.length; slot++) {
            Unit unit = (Unit) entity(ids[slot]);
            unit.joinSquad(squad.id(), slot);
            unit.setOrder(new SquadMemberOrder(squad.id()));
        }
        return squad;
    }

    /**
     * Breaks members out of a squad.
     *
     * <p>Two or more become a squad of their own; a single unit becomes an individual. Either
     * way they stop taking orders from the one they left.
     *
     * @return the new squad, or null if the split produced individuals
     */
    public Squad splitSquad(Squad squad, List<Unit> leaving) {
        if (squad == null || leaving == null || leaving.isEmpty()) {
            return null;
        }
        List<Unit> taken = new ArrayList<Unit>(leaving.size());
        for (int i = 0; i < leaving.size(); i++) {
            Unit unit = leaving.get(i);
            if (unit != null && unit.isAlive() && squad.contains(unit.id())) {
                taken.add(unit);
            }
        }
        if (taken.isEmpty()) {
            return null;
        }
        for (int i = 0; i < taken.size(); i++) {
            detachFromSquad(taken.get(i));
            taken.get(i).clearOrders();
        }
        return taken.size() >= 2 ? formSquad(squad.ownerId(), taken) : null;
    }

    /** Sends a squad somewhere, fighting on the way or not depending on the order. */
    public void orderSquadTo(int playerId, Squad squad, SquadOrder order, int tileX, int tileY) {
        // A broken squad takes no orders. That is the whole cost of losing one: you do not get
        // to simply tell a formation that has run to stand and fight, any more than a real
        // commander would. Getting them back into the line takes time you do not control.
        if (squad == null || squad.ownerId() != playerId || squad.isWipedOut()
                || squad.isBroken()) {
            return;
        }
        squad.setDestination(order, tileX, tileY);
        refreshSquadOrders(squad);
    }

    public void orderSquadAttack(int playerId, Squad squad, int targetId) {
        // A broken squad takes no orders. That is the whole cost of losing one: you do not get
        // to simply tell a formation that has run to stand and fight, any more than a real
        // commander would. Getting them back into the line takes time you do not control.
        if (squad == null || squad.ownerId() != playerId || squad.isWipedOut()
                || squad.isBroken()) {
            return;
        }
        squad.setAttackTarget(targetId);
        refreshSquadOrders(squad);
    }

    public void orderSquadHold(int playerId, Squad squad) {
        // A broken squad takes no orders. That is the whole cost of losing one: you do not get
        // to simply tell a formation that has run to stand and fight, any more than a real
        // commander would. Getting them back into the line takes time you do not control.
        if (squad == null || squad.ownerId() != playerId || squad.isWipedOut()
                || squad.isBroken()) {
            return;
        }
        squad.hold();
        refreshSquadOrders(squad);
    }

    /**
     * Puts every member back under the squad's orders.
     *
     * <p>Needed because members can be left holding a stale individual order — a leash walk to a
     * slot, say — that would otherwise outlive the command that replaced it.
     */
    private void refreshSquadOrders(Squad squad) {
        for (int slot = 0; slot < squad.slotCount(); slot++) {
            int memberId = squad.memberAt(slot);
            if (memberId < 0) {
                continue;
            }
            Entity member = entity(memberId);
            if (member instanceof Unit) {
                ((Unit) member).setOrder(new SquadMemberOrder(squad.id()));
            }
        }
    }

    /**
     * Folds a replacement into a squad that has room for him.
     *
     * @return true if he joined; false if the squad was already at strength
     */
    public boolean attachToSquad(Squad squad, Unit unit) {
        if (squad == null || unit == null || !unit.isAlive() || squad.isWipedOut()) {
            return false;
        }
        int slot = squad.addMember(unit.id());
        if (slot < 0) {
            // Somebody else filled the last gap while this man was walking.
            return false;
        }
        detachFromSquad(unit);
        unit.joinSquad(squad.id(), slot);
        unit.setOrder(new SquadMemberOrder(squad.id()));
        return true;
    }

    /**
     * Queues replacements for an under-strength squad.
     *
     * <p>They are trained at the barracks like anything else and walk to the squad, so a worn
     * squad is rebuilt rather than replaced — and one that has been left forward and bleeding
     * costs its owner the walk as well as the credits.
     *
     * @return how many replacements were queued
     */
    public int reinforceSquad(int playerId, Squad squad) {
        if (squad == null || squad.ownerId() != playerId || squad.isWipedOut()) {
            return 0;
        }
        int wanted = squad.shortfall();
        if (wanted <= 0) {
            return 0;
        }
        Player p = player(playerId);
        ProductionQueue queue = p.infantryQueue();
        int queued = 0;
        for (int i = 0; i < wanted && !queue.isFull(); i++) {
            if (!p.spend(squad.type().cost())) {
                break;
            }
            ProductionItem item = ProductionItem.forUnit(squad.type());
            item.setJoinSquadId(squad.id());
            queue.add(item);
            queued++;
        }
        return queued;
    }

    /** Any structure this player owns, preferring the command post. Where broken men run to. */
    public Building findAnyBuilding(int ownerId) {
        Building post = findBuilding(ownerId, BuildingType.COMMAND_POST);
        if (post != null) {
            return post;
        }
        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            if (b.ownerId() == ownerId && b.isAlive()) {
                return b;
            }
        }
        return null;
    }

    public SquadRegistry squads() {
        return squads;
    }

    /** The squad a unit belongs to, or null if it fights alone. */
    public Squad squadOf(Unit unit) {
        return unit == null || !unit.isInSquad() ? null : squads.byId(unit.squadId());
    }

    /**
     * Takes a unit out of its squad.
     *
     * <p>The one place membership is broken, so everything that has to happen when it does
     * happens here: any caller anywhere gets the whole job done.
     */
    public void detachFromSquad(Unit unit) {
        if (unit == null || !unit.isInSquad()) {
            return;
        }
        Squad squad = squads.byId(unit.squadId());
        if (squad != null) {
            if (!unit.isAlive()) {
                squad.noteCasualty(tick);
            }
            if (squad.removeMember(unit.id())) {
                squads.remove(squad);
            }
        }
        unit.leaveSquad();
    }

    public Mover mover() {
        return mover;
    }

    public Random random() {
        return random;
    }

    public TickProfiler profiler() {
        return profiler;
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
        return addPlayer(faction, ai, name, null);
    }

    /**
     * Adds a side fighting to a doctrine.
     *
     * <p>A null doctrine is the ordinary case and means the side fights the way everybody did
     * before doctrines existed. A doctrine belonging to the other faction is refused here rather
     * than quietly ignored: the picker, the harness flag and the tests all come through this
     * door, and a Kreisau Circle in gas masks would otherwise be a bug three layers away from
     * whatever caused it.
     */
    public Player addPlayer(Faction faction, boolean ai, String name, Doctrine doctrine) {
        if (doctrine != null && !doctrine.availableTo(faction)) {
            throw new IllegalArgumentException(
                    doctrine + " belongs to " + doctrine.faction() + ", not " + faction);
        }
        Player p = new Player(players.size(), faction, ai, name, STARTING_CREDITS, doctrine);
        players.add(p);
        fogGrids.add(new FogGrid(map.width(), map.height()));
        sightMemories.add(new SightMemory(map.width(), map.height()));
        influences.add(new InfluenceGrid(map.width(), map.height()));
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
        for (int r = 0; r <= SPAWN_SEARCH_RADIUS; r++) {
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
    /**
     * Gives one unit an order of its own.
     *
     * <p>Doing that to a squad member takes it out of the squad. That is the whole break-up
     * mechanic, and it lives here rather than in the command layer so that every existing path
     * — the HUD's tap-to-order, the AI, a test — gets it without knowing about squads at all.
     * A squad moves its members with {@code SquadMemberOrder}, which is exempt.
     */
    public void issueOrder(int playerId, Unit unit, Order order) {
        if (unit == null || unit.ownerId() != playerId || !unit.isAlive()) {
            return;
        }
        if (!(order instanceof SquadMemberOrder)) {
            detachFromSquad(unit);
        }
        unit.setOrder(order);
    }

    public void queueOrder(int playerId, Unit unit, Order order) {
        if (unit == null || unit.ownerId() != playerId || !unit.isAlive()) {
            return;
        }
        if (!(order instanceof SquadMemberOrder)) {
            detachFromSquad(unit);
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
        if (type.requiredDoctrine() != null && p.doctrine() != type.requiredDoctrine()) {
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

    /**
     * Queues a whole squad of a type that forms them.
     *
     * <p>Falls back to a single unit for types that fight alone, so the caller does not have to
     * know which is which — the sidebar offers what the type says it is.
     */
    public boolean enqueueSquad(int playerId, UnitType type) {
        if (!type.formsSquads()) {
            return enqueueUnit(playerId, type);
        }
        Player p = player(playerId);
        if (!canProduce(playerId, type)) {
            return false;
        }
        ProductionQueue queue = p.queueFor(type.producedBy());
        if (queue.isFull()) {
            return false;
        }
        ProductionItem item = ProductionItem.forSquad(type, type.squadSize());
        if (!p.spend(item.cost())) {
            events.add(GameEvent.at(GameEvent.Type.INSUFFICIENT_FUNDS, playerId, -1, 0, 0));
            return false;
        }
        queue.add(item);
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

    /**
     * Applies sabotage: the target is switched off for a while, and the saboteur walks away.
     *
     * <p>Heavier things shrug it off sooner — a walker reboots faster than a power station
     * comes back on line.
     */
    public void sabotage(Unit saboteur, Entity target) {
        int duration = target.isBuilding() ? SABOTAGE_STRUCTURE_TICKS : SABOTAGE_UNIT_TICKS;
        target.disableUntil(tick + duration);
        if (target.isBuilding()) {
            ((Building) target).setSabotaged(true);
        }
        events.add(new GameEvent(GameEvent.Type.SABOTAGED, target.ownerId(), target.id(),
                target.x(), target.y(), target.x(), target.y(), duration));
    }

    /**
     * Transfers a vehicle to the infiltrator's side and consumes the infiltrator.
     *
     * <p>Ownership is not just a field: the captured unit has to forget its old orders, and
     * anything the previous owner had pointed at it has to be cleaned up.
     */
    public void hijack(Unit infiltrator, Unit vehicle) {
        int newOwner = infiltrator.ownerId();
        events.add(GameEvent.at(GameEvent.Type.HIJACKED, newOwner, vehicle.id(),
                vehicle.x(), vehicle.y()));
        transferOwnership(vehicle, newOwner);
        infiltrator.kill();
    }

    /**
     * Moves an entity to another player.
     *
     * <p>Used by hijacking today and by anything else that changes sides later. Orders are
     * cleared because a unit's queue refers to targets chosen for its old allegiance — a
     * captured hound that kept its orders would drive straight back and attack its new owner.
     */
    public void transferOwnership(Entity entity, int newOwnerId) {
        if (entity == null || !entity.isAlive() || entity.ownerId() == newOwnerId) {
            return;
        }
        int previousOwner = entity.ownerId();
        // Detach before the owner changes, while the squad still recognises it as one of theirs.
        // A hijacked unit cannot stay in its old owner's formation, and this is the code path
        // most easily forgotten - it is also where the population counts would have to move.
        if (!entity.isBuilding()) {
            detachFromSquad((Unit) entity);
        }
        entity.setOwnerId(newOwnerId);

        if (entity.isBuilding()) {
            Building b = (Building) entity;
            b.setRepairing(false);
            if (b.type().isProducer()) {
                player(previousOwner).clearPrimaryProducer(b.type());
            }
        } else {
            Unit u = (Unit) entity;
            u.clearOrders();
            u.setVelocity(0f, 0f);
            if (u.type().isHarvester()) {
                // A stolen harvester should go back to work for its new owner, not stand idle.
                u.setOrder(new HarvestOrder());
            }
        }
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
        if (type.requiredDoctrine() != null && p.doctrine() != type.requiredDoctrine()) {
            return "Needs the " + type.requiredDoctrine().displayName() + " doctrine";
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
        profiler.beginTick();

        // Remember where everything was before it moves, so the renderer can interpolate.
        profiler.begin(TickProfiler.Phase.SNAPSHOT);
        for (int i = 0; i < units.size(); i++) {
            units.get(i).snapshotPosition();
        }
        profiler.end(TickProfiler.Phase.SNAPSHOT);

        profiler.begin(TickProfiler.Phase.INDEX);
        spatialIndex.rebuild(units);
        profiler.end(TickProfiler.Phase.INDEX);

        profiler.begin(TickProfiler.Phase.POWER);
        updatePower();
        profiler.end(TickProfiler.Phase.POWER);

        profiler.begin(TickProfiler.Phase.PRODUCTION);
        updateProduction();
        profiler.end(TickProfiler.Phase.PRODUCTION);

        profiler.begin(TickProfiler.Phase.SABOTAGE);
        updateSabotage();
        profiler.end(TickProfiler.Phase.SABOTAGE);

        profiler.begin(TickProfiler.Phase.STEALTH);
        updateStealth();
        profiler.end(TickProfiler.Phase.STEALTH);

        recoverSuppression();
        if (tick % MORALE_INTERVAL == 0) {
            updateMorale();
            updateStamina();
        }

        profiler.begin(TickProfiler.Phase.SQUADS);
        updateSquads();
        profiler.end(TickProfiler.Phase.SQUADS);

        profiler.begin(TickProfiler.Phase.UNITS);
        updateUnits();
        profiler.end(TickProfiler.Phase.UNITS);

        profiler.begin(TickProfiler.Phase.BUILDINGS);
        updateBuildings();
        profiler.end(TickProfiler.Phase.BUILDINGS);

        // After both things that can fire, so a shell from a gun and a shell from a structure of
        // the same range land on the same tick; and well before removeDead, so anything a shell
        // kills leaves the world on the tick it was killed rather than the one after.
        profiler.begin(TickProfiler.Phase.SHELLS);
        updateShells();
        profiler.end(TickProfiler.Phase.SHELLS);

        // After the shells, because a shell landing this tick vents its cloud this tick; and
        // before removeDead for the same reason the shells are - a man gas takes leaves on the
        // tick it took him. Free when the map is clean, which is every match without Gas War.
        profiler.begin(TickProfiler.Phase.GAS);
        gas.step(tick, map);
        applyGas();
        profiler.end(TickProfiler.Phase.GAS);

        profiler.begin(TickProfiler.Phase.REPAIRS);
        updateRepairs();
        profiler.end(TickProfiler.Phase.REPAIRS);

        profiler.begin(TickProfiler.Phase.SEPARATION);
        applySeparation();
        profiler.end(TickProfiler.Phase.SEPARATION);

        profiler.begin(TickProfiler.Phase.REMOVE_DEAD);
        removeDead();
        profiler.end(TickProfiler.Phase.REMOVE_DEAD);

        profiler.begin(TickProfiler.Phase.ORE);
        if (tick % ORE_REGROW_INTERVAL == 0) {
            map.regrowOre(ORE_REGROW_AMOUNT);
        }
        profiler.end(TickProfiler.Phase.ORE);

        // After removeDead, so the dead hold nothing, and before spotting, with the other
        // derived-knowledge phases.
        profiler.begin(TickProfiler.Phase.CONTROL);
        if (tick == 1 || tick % CONTROL_INTERVAL == 0) {
            updateInfluence();
        }
        profiler.end(TickProfiler.Phase.CONTROL);

        profiler.begin(TickProfiler.Phase.SPOTTING);
        if (tick == 1 || tick % SPOTTING_INTERVAL == 0) {
            updateSpotting();
        }
        profiler.end(TickProfiler.Phase.SPOTTING);

        profiler.begin(TickProfiler.Phase.FOG);
        // Also on tick 1: waiting for the first interval leaves the opening frames black.
        if (tick == 1 || tick % FOG_INTERVAL == 0) {
            updateFog();
        }
        profiler.end(TickProfiler.Phase.FOG);

        profiler.begin(TickProfiler.Phase.VICTORY);
        checkVictory();
        profiler.end(TickProfiler.Phase.VICTORY);
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
            advanceUnitQueue(p, p.airQueue(), BuildingType.HELIPAD, rate);
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
        int exitX = factory.tileX() + factory.tilesWide() / 2;
        int exitY = factory.tileY() + factory.tilesHigh();

        if (head.isSquad()) {
            Squad squad = spawnSquadNear(p.id(), head.unitType(), head.count(), exitX, exitY);
            if (squad == null) {
                return; // Not enough room outside the factory yet; try again next tick.
            }
            queue.removeHead();
            for (int i = 0; i < head.count(); i++) {
                p.noteUnitBuilt();
            }
            events.add(GameEvent.at(GameEvent.Type.SQUAD_TRAINED, p.id(), squad.id(),
                    squad.anchorX(), squad.anchorY()));
            orderSquadTo(p.id(), squad, SquadOrder.MOVE, factory.rallyX(), factory.rallyY());
            return;
        }

        Unit spawned = spawnUnitNear(p.id(), head.unitType(), exitX, exitY);
        if (spawned == null) {
            return; // Exit blocked; try again next tick.
        }
        queue.removeHead();
        p.noteUnitBuilt();
        events.add(GameEvent.at(GameEvent.Type.UNIT_TRAINED, p.id(), spawned.id(),
                spawned.x(), spawned.y()));
        if (head.joinSquadId() >= 0 && squads.byId(head.joinSquadId()) != null) {
            spawned.setOrder(new JoinSquadOrder(head.joinSquadId()));
        } else if (spawned.type().isHarvester()) {
            spawned.setOrder(new HarvestOrder());
        } else {
            spawned.setOrder(new MoveOrder(factory.rallyX(), factory.rallyY()));
        }
    }

    /**
     * Puts a whole squad on the ground outside a factory, or none of it.
     *
     * <p>All or nothing on purpose. Trickling members out as room appears would mean squads
     * that exist at half strength while the rest of them is still queued, and reinforcement
     * logic to fold the stragglers in — a lot of machinery to avoid waiting a few ticks for the
     * doorway to clear.
     *
     * @return the new squad, or null if there was not room for all of it
     */
    public Squad spawnSquadNear(int ownerId, UnitType type, int count, int tileX, int tileY) {
        List<int[]> pocket = findPocket(count, tileX, tileY);
        if (pocket == null) {
            return null;
        }
        List<Unit> members = new ArrayList<Unit>(count);
        for (int i = 0; i < pocket.size(); i++) {
            int[] tile = pocket.get(i);
            members.add(spawnUnit(ownerId, type, tile[0] + 0.5f, tile[1] + 0.5f));
        }
        return formSquad(ownerId, members);
    }

    /**
     * Finds {@code count} walkable, uncrowded tiles near a point.
     *
     * <p>Searched ring by ring so a squad appears clustered around its factory door rather than
     * smeared along whichever direction happened to be scanned first.
     *
     * @return the tiles, or null if that many could not be found
     */
    private List<int[]> findPocket(int count, int tileX, int tileY) {
        List<int[]> found = new ArrayList<int[]>(count);
        for (int radius = 0; radius <= SPAWN_SEARCH_RADIUS && found.size() < count; radius++) {
            for (int dy = -radius; dy <= radius && found.size() < count; dy++) {
                for (int dx = -radius; dx <= radius && found.size() < count; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) {
                        continue;
                    }
                    int x = tileX + dx;
                    int y = tileY + dy;
                    if (x < 0 || y < 0 || x >= map.width() || y >= map.height()) {
                        continue;
                    }
                    if (grid.isBlocked(x, y) || isTileCrowded(x, y)) {
                        continue;
                    }
                    found.add(new int[] {x, y});
                }
            }
        }
        return found.size() == count ? found : null;
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

    /**
     * One anchor, one route, one enemy scan per squad.
     *
     * <p>This is where a squad earns its keep. Eight men following an anchor cost one
     * pathfinding search between them instead of eight, and one target sweep instead of eight —
     * and those two are the expensive parts of a unit's tick. Everything the members then do is
     * steering at a point a few tiles away, which is free.
     *
     * <p>Runs before {@code updateUnits}, so members read an anchor that has already moved this
     * tick rather than trailing it by one.
     */
    private void updateSquads() {
        List<Squad> all = squads.all();
        for (int i = 0; i < all.size(); i++) {
            Squad squad = all.get(i);
            if (squad.isWipedOut() || squad.isBroken()) {
                // Running men are not looking for targets and are not being led anywhere.
                continue;
            }
            updateSquadTarget(squad);
            advanceSquadAnchor(squad);
        }
    }

    /**
     * Finds the squad something to shoot at, once for everybody.
     *
     * <p>Staggered by squad id so that seventy squads do not all sweep on the same tick and
     * turn one tick in six into a spike.
     */
    private void updateSquadTarget(Squad squad) {
        Entity target = squad.engagedTargetId() >= 0 ? entity(squad.engagedTargetId()) : null;
        if (target != null && !target.isAlive()) {
            target = null;
        }
        // Let go of anything that has run far enough past the squad's reach.
        if (target != null) {
            float reach = squadReach(squad) + SQUAD_LEASH;
            float dx = target.x() - squad.anchorX();
            float dy = target.y() - squad.anchorY();
            if (dx * dx + dy * dy > reach * reach) {
                target = null;
            }
        }

        if (squad.order() == SquadOrder.ATTACK) {
            // An explicit attack order overrides whatever the sweep found.
            Entity ordered = entity(squad.orderTargetId());
            squad.setEngagedTargetId(ordered != null && ordered.isAlive() ? ordered.id() : -1);
            if (ordered == null || !ordered.isAlive()) {
                squad.hold();
            }
            return;
        }

        if (target == null && squad.order() != SquadOrder.MOVE
                && (tick + squad.id()) % SQUAD_SCAN_INTERVAL == 0) {
            target = findNearestEnemy(squad.ownerId(), squad.anchorX(), squad.anchorY(),
                    squadReach(squad), true);
        }
        squad.setEngagedTargetId(target == null ? -1 : target.id());
    }

    /** How far a squad can see or shoot, whichever is further — the radius it sweeps. */
    private float squadReach(Squad squad) {
        return Math.max(squad.type().sight(), squadFiringRange(squad));
    }

    /** How close a squad has to be before its members can actually shoot. */
    private float squadFiringRange(Squad squad) {
        Weapon weapon = squad.type().weapon();
        // A shade inside the real range, so members settling into slots are not left straddling
        // it with half the squad unable to fire.
        return weapon == null ? 0f : weapon.range() * 0.8f;
    }

    /**
     * Walks the anchor along the squad's route.
     *
     * <p>Two things stop it. A squad that has found something to fight stands and fights rather
     * than walking away mid-firefight. And a squad whose rearmost man has fallen behind waits
     * for him, or the formation strings out into single file and stops being a formation.
     */
    private void advanceSquadAnchor(Squad squad) {
        if (squad.order() == SquadOrder.HOLD) {
            return;
        }

        // A squad with something to fight closes on it, whether it went looking for that fight
        // or walked into it. Stopping at the moment of contact is wrong: a squad sees further
        // than it shoots, so it would freeze a tile or two outside its own range and stand
        // there. Close to weapon range, then stop.
        Entity engaged = squad.engagedTargetId() >= 0 ? entity(squad.engagedTargetId()) : null;
        if (engaged != null && !engaged.isAlive()) {
            engaged = null;
        }

        int destX;
        int destY;
        if (engaged != null && squad.order() != SquadOrder.MOVE) {
            if (squad.anchorDistanceTo(engaged.x(), engaged.y()) <= squadFiringRange(squad)) {
                return; // In range; stand and shoot.
            }
            destX = engaged.tileX();
            destY = engaged.tileY();
        } else if (squad.order() == SquadOrder.ATTACK) {
            return; // Ordered onto something that is gone.
        } else {
            destX = squad.destTileX();
            destY = squad.destTileY();
        }
        if (destX < 0 || destY < 0) {
            return;
        }

        if (!squad.hasPathTo(destX, destY)) {
            int[] route = mover.pathfinder().findPath(grid, squad.anchorTileX(),
                    squad.anchorTileY(), destX, destY);
            profiler.countAstarSearch(mover.pathfinder().nodesExpanded());
            if (route == null || route.length == 0) {
                // No route, which for a squad already standing on the tile it was sent to
                // means it has nothing left to walk. Digging in wants that read as arrival,
                // not as a failed order: an entrench order onto your own position is "dig
                // where you stand", and dropping it to HOLD would leave a squad ordered to
                // dig standing about with a shovel it never gets out.
                if (squad.order() == SquadOrder.ENTRENCH) {
                    squad.arrivedToDig();
                } else {
                    squad.hold();
                }
                return;
            }
            squad.setPath(route, destX, destY);
        }

        // Waiting for stragglers has to be able to give up. A member whose slot falls inside a
        // building can never reach it, so an unconditional wait is a deadlock - and was one:
        // every AI squad stood at its factory door for a whole match. Past the limit the squad
        // marches and the laggard catches up on his own, pathing properly once he is far
        // enough behind to need to.
        if (isSquadStrungOut(squad)) {
            if (squad.noteWaiting() < MAX_COHESION_WAIT) {
                return;
            }
        } else {
            squad.clearWaiting();
        }

        float step = squad.type().speed() * ANCHOR_SPEED_FRACTION * TICK_SECONDS;
        while (step > 0f && !squad.pathComplete()) {
            int packed = squad.path()[squad.pathIndex()];
            float waypointX = AStar.packX(packed) + 0.5f;
            float waypointY = AStar.packY(packed) + 0.5f;
            float dx = waypointX - squad.anchorX();
            float dy = waypointY - squad.anchorY();
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            if (distance <= step) {
                squad.setAnchor(waypointX, waypointY);
                squad.advancePath();
                step -= distance;
                if (distance > 1e-4f) {
                    squad.setHeading((float) StrictMath.atan2(dy, dx));
                }
            } else {
                squad.setAnchor(squad.anchorX() + dx / distance * step,
                        squad.anchorY() + dy / distance * step);
                squad.setHeading((float) StrictMath.atan2(dy, dx));
                step = 0f;
            }
        }

        if (squad.pathComplete()) {
            squad.clearPath();
            if (squad.order() == SquadOrder.ENTRENCH) {
                squad.arrivedToDig();
            } else if (squad.order() != SquadOrder.ATTACK) {
                squad.hold();
            }
        }
    }

    /**
     * True while somebody is far enough behind that the squad should wait.
     *
     * <p>Without this the anchor walks at full speed regardless and anyone held up by terrain or
     * a building is left behind permanently, which turns a formation into a queue.
     */
    private boolean isSquadStrungOut(Squad squad) {
        for (int slot = 0; slot < squad.slotCount(); slot++) {
            int memberId = squad.memberAt(slot);
            if (memberId < 0) {
                continue;
            }
            Entity member = entity(memberId);
            if (member == null) {
                continue;
            }
            squad.slotPosition(slot, slotScratch);
            float dx = member.x() - slotScratch[0];
            float dy = member.y() - slotScratch[1];
            if (dx * dx + dy * dy > SQUAD_COHESION * SQUAD_COHESION) {
                return true;
            }
        }
        return false;
    }

    /**
     * Nerve: squads break, run, and come back shaken.
     *
     * <p>Runs once a second rather than every tick. Morale is a slow quantity and the whole
     * point of it is that it lags what is happening — a squad should not break because of one
     * bad tick, and should not recover the instant the shooting stops.
     */
    private void updateMorale() {
        List<Squad> all = squads.all();
        for (int i = 0; i < all.size(); i++) {
            Squad squad = all.get(i);
            if (squad.isWipedOut()) {
                continue;
            }
            if (squad.isBroken()) {
                if (tick >= squad.rallyAtTick() && squadIsSafe(squad)) {
                    squad.rally(RALLY_MORALE);
                    events.add(GameEvent.at(GameEvent.Type.SQUAD_RALLIED, squad.ownerId(),
                            squad.id(), squad.anchorX(), squad.anchorY()));
                    refreshSquadOrders(squad);
                }
                continue;
            }

            squad.changeMorale(moraleChangeFor(squad));
            if (squad.morale() <= 0) {
                breakSquad(squad);
            }
        }
    }

    /**
     * What a second of this squad's situation does to its nerve.
     *
     * <p>Losses hurt most, and hurt more the worse the squad already is - the last two men of a
     * section are far closer to running than the first two were. Being pinned wears it down.
     * Being out of contact rebuilds it, but slowly, and never past what the army as a whole can
     * sustain.
     */
    private int moraleChangeFor(Squad squad) {
        int suppressed = 0;
        int alive = 0;
        boolean inContact = false;
        for (int slot = 0; slot < squad.slotCount(); slot++) {
            int memberId = squad.memberAt(slot);
            if (memberId < 0) {
                continue;
            }
            Entity member = entity(memberId);
            if (!(member instanceof Unit)) {
                continue;
            }
            Unit unit = (Unit) member;
            alive++;
            if (unit.isProne()) {
                suppressed++;
            }
            if (unit.wasDamagedWithin(tick, RECENT_CONTACT_TICKS)
                    || tick - unit.lastFiredTick() < RECENT_CONTACT_TICKS) {
                inContact = true;
            }
        }
        // Losing men counts too, and has to be checked separately: the survivors of a squad
        // being killed one shot at a time carry no mark of it at all.
        if (tick - squad.lastCasualtyTick() < RECENT_CASUALTY_TICKS) {
            inContact = true;
        }
        if (alive == 0) {
            return 0;
        }

        if (inContact) {
            // Losses tell hardest, and tell more the worse the squad already is: the last two
            // men of a section are far closer to running than the first two were.
            int lossPenalty = Math.round(MORALE_LOSS_WEIGHT * (1f - squad.strengthFraction())
                    * (1f - squad.strengthFraction()));
            int pinnedPenalty = Math.round(MORALE_PINNED_WEIGHT * suppressed / (float) alive);
            if (lossPenalty + pinnedPenalty > 0) {
                return -(lossPenalty + pinnedPenalty);
            }
        }

        // Out of contact it recovers - but only up to what a squad this badly cut about can
        // manage. Applying the casualty penalty unconditionally was wrong and was a slow
        // catastrophe: a half-strength squad bled morale even asleep at home, so it broke,
        // ran, rallied, and broke again forever. By the end of a match thirty-eight squads out
        // of thirty-nine were routing and no attack ever landed.
        int ceiling = Math.round(100f * (WORN_MORALE_FLOOR
                + (1f - WORN_MORALE_FLOOR) * squad.strengthFraction()));
        if (squad.morale() >= ceiling) {
            return 0;
        }
        float recovery = MORALE_RECOVERY * (1f - player(squad.ownerId()).exhaustion());
        return Math.max(1, Math.round(recovery));
    }

    private void breakSquad(Squad squad) {
        // A tired army stays broken longer.
        float exhaustion = player(squad.ownerId()).exhaustion();
        int ticks = Math.round(BREAK_TICKS * (1f + exhaustion));
        squad.breakAt(tick + ticks);
        events.add(GameEvent.at(GameEvent.Type.SQUAD_BROKEN, squad.ownerId(), squad.id(),
                squad.anchorX(), squad.anchorY()));

        for (int slot = 0; slot < squad.slotCount(); slot++) {
            int memberId = squad.memberAt(slot);
            if (memberId < 0) {
                continue;
            }
            Entity member = entity(memberId);
            if (member instanceof Unit) {
                ((Unit) member).setOrder(
                        RoutOrder.towardsHome(this, (Unit) member, squad.rallyAtTick()));
            }
        }
    }

    /** True once nobody hostile is close enough to keep a broken squad running. */
    private boolean squadIsSafe(Squad squad) {
        return findNearestEnemy(squad.ownerId(), squad.anchorX(), squad.anchorY(),
                RALLY_SAFE_RADIUS, false) == null;
    }

    /**
     * How much fight each army has left.
     *
     * <p>Drains with casualties and with how much of the army is in contact; recovers in the
     * quiet. What it buys is the difference between a long match ending and a long match going
     * on forever: two evenly matched sides do not stay evenly matched, because the one that has
     * been fighting harder gets tired first.
     */
    private void updateStamina() {
        for (int p = 0; p < players.size(); p++) {
            Player player = players.get(p);
            if (player.isDefeated()) {
                continue;
            }
            int engaged = 0;
            int mine = 0;
            for (int i = 0; i < units.size(); i++) {
                Unit u = units.get(i);
                if (u.ownerId() != player.id() || !u.isAlive() || u.type().isHarvester()) {
                    continue;
                }
                mine++;
                // Firing counts as being in contact. Waiting for damage was too narrow a test:
                // men who are shot usually die rather than lingering as evidence, so an army
                // could fight all match and never register as engaged - stamina sat at a
                // hundred from the first tick to the last.
                if (u.suppression() > 0 || u.wasDamagedWithin(tick, RECENT_CONTACT_TICKS)
                        || tick - u.lastFiredTick() < RECENT_CONTACT_TICKS) {
                    engaged++;
                }
            }
            if (mine == 0) {
                player.changeStamina(STAMINA_RECOVERY);
                continue;
            }
            float contact = engaged / (float) mine;
            if (contact > STAMINA_CONTACT_THRESHOLD) {
                player.changeStamina(-Math.max(1, Math.round(STAMINA_DRAIN * contact)));
            } else {
                player.changeStamina(STAMINA_RECOVERY);
            }
        }
    }

    /** Everyone sheds a little of what has been shot at them, faster with cover to use. */
    private void recoverSuppression() {
        for (int i = 0; i < units.size(); i++) {
            Unit u = units.get(i);
            if (u.isAlive() && u.suppression() > 0) {
                u.recoverSuppression(map.cover(u.tileX(), u.tileY()) > 0);
            }
        }
    }

    private void updateUnits() {
        for (int i = 0; i < units.size(); i++) {
            Unit u = units.get(i);
            if (!u.isAlive()) {
                continue;
            }
            if (u.isDisabled(tick)) {
                // Sabotage: the machine is dead where it stands until the charge burns out.
                u.setVelocity(0f, 0f);
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
                if (u.type().isArtillery()) {
                    autoBombard(u);
                } else if ((tick + u.id()) % ACQUIRE_INTERVAL == 0) {
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
        // The ring, not the disc: a gun with a scout at one tile and a section at six must not
        // pick the scout, fail its own range check and then stand there saying nothing.
        Entity target = findNearestEnemyBetween(u.ownerId(), u.x(), u.y(), w.minRange(),
                w.range(), true, w);
        if (target != null) {
            u.faceToward(target.x(), target.y());
            tryAttack(u, target);
        }
    }

    /**
     * An idle gun looks for somewhere worth shelling.
     *
     * <p>Deliberately not {@code autoDefend}. Nearest-target is exactly the wrong rule for a
     * weapon with a hole in the middle of its range, and it is the wrong rule for indirect fire
     * generally: a gun does not spend a five-second reload on whoever happens to be closest.
     *
     * <p>What it looks for instead is a <b>crowd</b>. Every candidate is scored by how many
     * other enemies are standing inside one blast radius of it, and a mission is only fired if
     * at least two men would be caught. That single threshold is most of the balance of the
     * whole weapon: it makes artillery the answer to massed infantry and a waste of a reload
     * against skirmishers, which is exactly the deadlock it was built to break.
     *
     * <p>Stationary and dug-in targets score higher, which turns the chosen drawback - shells
     * land where they were aimed, so a moving squad walks out from under them - from a pure
     * miss into a targeting incentive. A gun prefers a line that has stopped to dig.
     */
    private void autoBombard(Unit gun) {
        Weapon weapon = gun.weapon();
        // Cheapest possible gate first: a gun on a two-hundred-tick reload spends almost all of
        // its life here, and the scan below is the expensive part.
        if (weapon == null || !gun.weaponReady()) {
            return;
        }
        if ((tick + gun.id()) % ARTILLERY_ACQUIRE_INTERVAL != 0) {
            return;
        }

        bombardScratch.clear();
        spatialIndex.query(gun.x(), gun.y(), weapon.range() + 1f, bombardScratch);

        int bestScore = 0;
        int bestId = Integer.MAX_VALUE;
        int bestTileX = -1;
        int bestTileY = -1;
        float blast = Math.max(1f, weapon.blastRadius());

        for (int i = 0; i < bombardScratch.size(); i++) {
            Unit candidate = bombardScratch.get(i);
            if (!candidate.isAlive() || !areEnemies(gun.ownerId(), candidate.ownerId())) {
                continue;
            }
            float gap = gun.distanceTo(candidate);
            if (gap > weapon.range() || gap < weapon.minRange()) {
                continue;
            }
            int tileX = candidate.tileX();
            int tileY = candidate.tileY();
            // Somewhere nobody of ours has seen lately is somewhere we may not fire on.
            if (!canObserve(gun.ownerId(), tileX, tileY)) {
                continue;
            }

            int caught = 0;
            for (int j = 0; j < bombardScratch.size(); j++) {
                Unit other = bombardScratch.get(j);
                if (other.isAlive() && areEnemies(gun.ownerId(), other.ownerId())
                        && other.distanceTo(candidate.x(), candidate.y()) <= blast) {
                    caught++;
                }
            }
            if (caught < MIN_WORTHWHILE_CLUSTER) {
                continue;
            }

            int score = caught;
            if (!candidate.isMoving()) {
                score += 2;
            }
            if (map.cover(tileX, tileY) > 0) {
                score += 1;
            }
            // Ties break on the lowest entity id, never on a float distance: the same crowd
            // must produce the same aim point on a replay.
            if (score > bestScore || (score == bestScore && candidate.id() < bestId)) {
                bestScore = score;
                bestId = candidate.id();
                bestTileX = tileX;
                bestTileY = tileY;
            }
        }

        if (bestTileX >= 0) {
            tryBombard(gun, bestTileX, bestTileY);
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
                Entity target = findNearestEnemy(b.ownerId(), b.x(), b.y(), w.range(), false, w);
                if (target != null) {
                    tryAttack(b, target);
                }
            }
        }
    }

    /**
     * Refreshes the cached sabotage flag on structures.
     *
     * <p>{@code isOperational()} is called from a dozen places that have no idea what tick it
     * is, so the tick-dependent state is resolved once here rather than threading the clock
     * through all of them.
     */
    private void updateSabotage() {
        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            boolean off = b.isDisabled(tick);
            if (off != b.isSabotaged()) {
                b.setSabotaged(off);
                events.add(GameEvent.at(off ? GameEvent.Type.SABOTAGED
                        : GameEvent.Type.SABOTAGE_ENDED, b.ownerId(), b.id(), b.x(), b.y()));
            }
        }
    }

    /** Enemies standing close enough see through concealment, whatever the fog says. */
    private void updateStealth() {
        if (tick % STEALTH_CHECK_INTERVAL != 0) {
            return;
        }
        for (int i = 0; i < units.size(); i++) {
            Unit hidden = units.get(i);
            if (!hidden.type().isStealthy() || !hidden.isAlive()) {
                continue;
            }
            Entity spotter = findNearestEnemy(hidden.ownerId(), hidden.x(), hidden.y(),
                    STEALTH_REVEAL_RADIUS, true);
            if (spotter != null) {
                hidden.markRevealed(tick + STEALTH_CHECK_INTERVAL * 2);
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
    /**
     * Pushes overlapping units apart.
     *
     * <p>Three things about how this is done matter more than the pushing itself.
     *
     * <p><b>Pairs are resolved once, and both units move.</b> Each overlapping pair is handled
     * by whichever unit has the lower id, which pushes both halves at the same time. Before,
     * every pair was visited twice — and the second visit measured against a neighbour that had
     * already moved this tick, so the result depended on list order and crowds oscillated.
     *
     * <p><b>Displacement is accumulated and applied afterwards.</b> Same reason: a unit should
     * be pushed by where its neighbours were at the top of the tick, not by where the ones
     * ahead of it in the list have already been shoved to.
     *
     * <p><b>A push may not drive a unit backwards.</b> This is the important one. Separation
     * used to move units bodily against the direction they were walking, so in a crowd they
     * stopped making headway, tripped {@code Mover}'s stuck detector, and threw their route away
     * — at five hundred a side that accounted for roughly half of all the pathfinding in the
     * game. Removing the component of the push that opposes a unit's own movement makes it
     * slide past its neighbours instead of being knocked back into them.
     */
    private void applySeparation() {
        // Pass one: work out where everyone should be pushed, moving nobody.
        for (int i = 0; i < units.size(); i++) {
            Unit a = units.get(i);
            if (!a.isAlive()) {
                continue;
            }
            queryScratch.clear();
            float reach = a.radius() * 2f + 1f;
            spatialIndex.query(a.x(), a.y(), reach, queryScratch);
            profiler.countSeparationPairs(queryScratch.size());

            for (int j = 0; j < queryScratch.size(); j++) {
                Unit b = queryScratch.get(j);
                // The lower id owns the pair, so it is resolved exactly once.
                if (b == a || !b.isAlive() || b.id() < a.id()) {
                    continue;
                }
                // Different altitudes never touch: a gyro parked over a tank is two layers,
                // not a collision.
                if (a.type().isAir() != b.type().isAir()) {
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
                float pushX = dx / d * overlap;
                float pushY = dy / d * overlap;
                a.addSeparationPush(pushX, pushY);
                b.addSeparationPush(-pushX, -pushY);
            }
        }

        // Pass two: clamp, project, and move.
        for (int i = 0; i < units.size(); i++) {
            Unit a = units.get(i);
            float pushX = a.separationPushX();
            float pushY = a.separationPushY();
            a.clearSeparationPush();
            if (!a.isAlive() || (pushX == 0f && pushY == 0f)) {
                continue;
            }

            // Slide, do not reverse: strip out whatever part of the push opposes the direction
            // this unit is already travelling in.
            float vx = a.velocityX();
            float vy = a.velocityY();
            float speedSq = vx * vx + vy * vy;
            if (speedSq > 1e-6f) {
                float inverse = 1f / (float) Math.sqrt(speedSq);
                float headingX = vx * inverse;
                float headingY = vy * inverse;
                float against = pushX * headingX + pushY * headingY;
                if (against < 0f) {
                    pushX -= against * headingX;
                    pushY -= against * headingY;
                }
            }

            // Nothing gets shoved further in one tick than it could have walked. Without this,
            // a unit deep in a crowd is flung about by the sum of everyone touching it.
            float limit = SEPARATION_MAX_STEP * a.radius();
            float pushSq = pushX * pushX + pushY * pushY;
            if (pushSq > limit * limit) {
                float scale = limit / (float) Math.sqrt(pushSq);
                pushX *= scale;
                pushY *= scale;
            }
            if (pushX == 0f && pushY == 0f) {
                continue;
            }

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

    private static float clamp(float v, int max) {
        return Math.max(0.01f, Math.min(max - 0.01f, v));
    }

    private void removeDead() {
        for (int i = units.size() - 1; i >= 0; i--) {
            Unit u = units.get(i);
            if (!u.isAlive()) {
                detachFromSquad(u);
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

    /**
     * Refreshes each side's memory of the ground it can see.
     *
     * <p>Buildings stamp as well as units. Without that a base with its army away could not
     * call fire on somebody walking up to its own gate, which is the one moment it most wants
     * to.
     */
    private void updateSpotting() {
        for (int i = 0; i < units.size(); i++) {
            Unit u = units.get(i);
            sightMemories.get(u.ownerId()).see(u.x(), u.y(), tick);
        }
        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            sightMemories.get(b.ownerId()).see(b.x(), b.y(), tick);
        }
    }

    /**
     * Rebuilds every side's picture of what it holds.
     *
     * <p>Scatter, amplify by the ground, then spread. The order matters: the earthworks bonus
     * multiplies what is actually standing in a cell before that weight is spread outward, so a
     * dug-in position projects its amplified strength rather than amplifying whatever drifts in.
     *
     * <p>Iterates the unit and building lists in order, which makes list order part of the
     * result — the same rule the state digest already documents for itself.
     */
    private void updateInfluence() {
        if (groundBonus == null) {
            InfluenceGrid first = influences.get(0);
            groundBonus = new float[first.cellsAcross() * first.cellsDown()];
        }
        InfluenceGrid shape = influences.get(0);
        InfluenceGrid.groundBonus(map, groundBonus, shape.cellsAcross(), shape.cellsDown());

        for (int i = 0; i < influences.size(); i++) {
            influences.get(i).clear();
        }
        for (int i = 0; i < units.size(); i++) {
            Unit u = units.get(i);
            if (u.isAlive()) {
                influences.get(u.ownerId()).add(u.x(), u.y(), holdWeight(u));
            }
        }
        for (int i = 0; i < buildings.size(); i++) {
            Building b = buildings.get(i);
            if (b.isAlive() && b.isComplete()) {
                influences.get(b.ownerId()).add(b.x(), b.y(), b.hp());
            }
        }
        for (int i = 0; i < influences.size(); i++) {
            influences.get(i).applyGroundBonus(groundBonus);
            influences.get(i).spread();
        }
        controlVersion++;
    }

    /** Changes whenever the field has moved. Presentation only. */
    public int controlVersion() {
        return controlVersion;
    }

    /**
     * What one unit is worth as an occupier of ground.
     *
     * <p>Hit points rather than cost, and the difference is not academic: the Command Post costs
     * nothing to build, so a cost-weighted field would read the most important building on the
     * map as neutral ground and the tank factory as the strongpoint. Hit points are what it
     * costs to remove a thing from a piece of ground, which is what holding it means.
     *
     * <p>Anything unarmed holds nothing — that covers harvesters, saboteurs and infiltrators
     * without naming any of them. Artillery holds almost nothing, tested on the same dead-zone
     * predicate the AI's idle-fighter list uses, so the two definitions cannot drift apart. Men
     * who are running hold nothing at all.
     */
    private float holdWeight(Unit u) {
        // An aircraft holds nothing: ground is held by what stands on it, and the front should
        // neither advance nor waver because a machine flew across.
        if (u.type().isAir()) {
            return 0f;
        }
        Weapon weapon = u.weapon();
        if (weapon == null) {
            return 0f;
        }
        Squad squad = squadOf(u);
        if (squad != null && squad.isBroken()) {
            return 0f;
        }
        float weight = u.hp();
        return weapon.hasMinRange() ? weight * ARTILLERY_HOLD : weight;
    }

    /** One side's raw influence over a tile. */
    public float influence(int playerId, int tileX, int tileY) {
        return influences.get(playerId).atTile(tileX, tileY);
    }

    /**
     * Who holds a tile and by how much: mine, less whichever enemy has the most there.
     *
     * <p>The strongest enemy rather than the sum of them. With two players the two are the same
     * number, but nothing enforces two, and summing would make a side surrounded by two weak
     * enemies read as more thoroughly beaten than one facing a single strong enemy — which
     * inverts what a front means.
     */
    public float control(int playerId, int tileX, int tileY) {
        return controlAtCell(playerId, tileX / InfluenceGrid.CELL_TILES,
                tileY / InfluenceGrid.CELL_TILES);
    }

    public float controlAtCell(int playerId, int cellX, int cellY) {
        float mine = influences.get(playerId).atCell(cellX, cellY);
        float worst = 0f;
        for (int i = 0; i < influences.size(); i++) {
            if (!areEnemies(playerId, i)) {
                continue;
            }
            float theirs = influences.get(i).atCell(cellX, cellY);
            if (theirs > worst) {
                worst = theirs;
            }
        }
        return mine - worst;
    }

    /**
     * True where this player's ground touches the enemy's.
     *
     * <p>Strict signs on both sides, so a cell of exactly zero belongs to nobody and is not a
     * front by itself. No epsilon, and no float equality anywhere.
     */
    public boolean isFrontCell(int playerId, int cellX, int cellY) {
        if (controlAtCell(playerId, cellX, cellY) <= 0f) {
            return false;
        }
        return controlAtCell(playerId, cellX - 1, cellY) < 0f
                || controlAtCell(playerId, cellX + 1, cellY) < 0f
                || controlAtCell(playerId, cellX, cellY - 1) < 0f
                || controlAtCell(playerId, cellX, cellY + 1) < 0f;
    }

    public int controlCellsAcross() {
        return influences.get(0).cellsAcross();
    }

    public int controlCellsDown() {
        return influences.get(0).cellsDown();
    }

    /** What this player has seen, and when. */
    public SightMemory sightMemory(int playerId) {
        return sightMemories.get(playerId);
    }

    /**
     * True if this player may call fire on a tile: seen recently, whether or not seen now.
     *
     * <p>The loose rule rather than the strict one. Requiring a spotter to be looking at the
     * moment of firing would make artillery a two-unit combination and nothing else; requiring
     * only that somebody has been there lately makes reconnaissance worth doing without making
     * it mandatory.
     */
    public boolean canObserve(int playerId, int tileX, int tileY) {
        return sightMemories.get(playerId).seenWithin(tileX, tileY, tick, SPOTTING_MEMORY);
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
        return tryAttack(attacker, target, attacker.weapon());
    }

    /**
     * Fires a specific weapon rather than the attacker's own — used by tests to exercise a
     * weapon in isolation, and by anything that gives a unit a one-off attack.
     */
    /**
     * Whether a weapon can touch a target at all, before range or readiness are asked.
     *
     * <p>One predicate, used by the shot itself and by every target acquisition, so a rifleman
     * neither hits an aircraft nor spends his life chasing one he can never hit. Ground is
     * always reachable; the sky only by weapons that say so.
     */
    public static boolean canEngage(Weapon weapon, Entity target) {
        if (weapon == null) {
            return false;
        }
        if (target.isBuilding()) {
            return true;
        }
        return !((Unit) target).type().isAir() || weapon.isAntiAir();
    }

    public boolean tryAttack(Entity attacker, Entity target, Weapon weapon) {
        if (weapon == null || target == null || !target.isAlive()) {
            return false;
        }
        if (!canEngage(weapon, target)) {
            return false;
        }
        boolean ready = attacker.isBuilding() ? ((Building) attacker).weaponReady()
                : ((Unit) attacker).weaponReady();
        if (!ready) {
            return false;
        }
        if (!inWeaponRange(attacker, target, weapon)) {
            return false;
        }

        int damage = resolveDamage(weapon, target);
        boolean killed = target.applyDamage(damage, attacker.id(), tick);
        suppress(target, Suppression.perShot(weapon.weaponClass()));
        flattenGround(target, weapon);
        if (weapon.hasBlast()) {
            applyBlast(attacker, target, weapon);
        }
        if (attacker.isBuilding()) {
            ((Building) attacker).startWeaponCooldown();
        } else {
            ((Unit) attacker).startWeaponCooldown();
            ((Unit) attacker).noteFired(tick);
        }

        events.add(GameEvent.shot(attacker.ownerId(), attacker.id(), attacker.x(), attacker.y(),
                target.x(), target.y(), damage, weapon.weaponClass(), kindOf(target)));
        events.add(GameEvent.at(GameEvent.Type.UNDER_ATTACK, target.ownerId(), target.id(),
                target.x(), target.y()));
        if (killed) {
            events.add(GameEvent.destroyed(target.ownerId(), target.id(), target.x(), target.y(),
                    kindOf(target)));
        }
        return true;
    }

    /**
     * What a shot actually takes off, once the ground and the target's posture are accounted for.
     *
     * <p>The single place damage is decided, which is why cover and being prone can be added
     * here rather than in every weapon. Vehicles ignore infantry cover: a wall a rifleman
     * shelters behind is not cover for a tank, it is scenery.
     */
    private int resolveDamage(Weapon weapon, Entity target) {
        int base = weapon.damageAgainst(target.armor());
        if (target.isBuilding()) {
            return base;
        }
        Unit unit = (Unit) target;
        float multiplier = 1f;
        if (!unit.type().isVehicle()) {
            int cover = map.cover(unit.tileX(), unit.tileY());
            // Clamped to FULL_COVER, not MAX_COVER. The ordinary reduction is a fraction of the
            // way to four and stays that way however deep tiles are allowed to get, so adding a
            // fifth level did not quietly weaken every trench and every ruin on the map.
            multiplier *= Suppression.damageInCover(weapon.weaponClass(),
                    Math.min(cover, TileMap.FULL_COVER), TileMap.FULL_COVER,
                    Doctrines.coverScale(player(unit.ownerId()).doctrine()));
            // And a roof, which is a different thing rather than more of the same: it stops
            // what comes down and nothing that comes flat.
            multiplier *= Earthworks.overhead(weapon.weaponClass(), cover);
            if (unit.isProne()) {
                multiplier *= Suppression.damageWhenProne();
            }
        }
        return Math.max(1, Math.round(base * multiplier));
    }

    /**
     * Books one tick of a man's digging, raising his tile's cover when he has done enough.
     *
     * <p>The one place earthworks are created, so the conditions on them are stated once. A
     * vehicle has no shovel. A man with his head down is not using his - unless his side dug
     * that rule out from under itself, which is what Deep Works is. And a tile that is
     * already as deep as this side digs takes no more work — checked before banking the tick,
     * so a squad sitting on finished ground is not silently throwing away effort it could be
     * spending a tile over.
     */
    public void digIn(Unit unit) {
        if (unit == null || !unit.isAlive() || unit.type().isVehicle()) {
            return;
        }
        Doctrine doctrine = player(unit.ownerId()).doctrine();
        if (unit.isProne() && !Doctrines.digsUnderFire(doctrine)) {
            return;
        }
        int x = unit.tileX();
        int y = unit.tileY();
        if (map.cover(x, y) >= Doctrines.maxDepth(doctrine)) {
            return;
        }
        if (unit.dig(Doctrines.digTicks(doctrine))) {
            map.addCover(x, y, 1);
            // Whose works these are, for the look of them. First to break ground keeps it.
            map.setBuilder(x, y, player(unit.ownerId()).faction());
        }
    }

    /**
     * Strips earth from the ground around a blast.
     *
     * <p>The counterplay that stops a front freezing. Infantry harden a line for free given
     * time; high explosive is what takes it back down, and it does so far faster than men dig.
     * A weapon that cannot move soil skips the sweep entirely, which is most of them.
     *
     * <p>Called from the shot rather than from the blast, because most of the things that churn
     * ground in this game do not splash — a rocket and a tank shell are direct hits, and a
     * direct hit is exactly what wrecks a hole. A weapon that does splash strips a wider patch,
     * which is the whole of the answer to a dug-in line until the guns arrive.
     */
    private void flattenGround(Entity epicentre, Weapon weapon) {
        // tileX() is (int) x, not a rounding. Keeping the truncation is what makes this
        // delegate identical to the code it replaced rather than merely equivalent.
        flattenGroundAt(epicentre.tileX(), epicentre.tileY(), weapon);
    }

    /**
     * The same, aimed at a tile rather than at whoever is standing on it.
     *
     * <p>A shell lands on ground. Everything else in this game hits a thing, which is why the
     * blast routines were all written around an {@code Entity} — and why they all needed a
     * point-keyed form before artillery could exist.
     */
    private void flattenGroundAt(int cx, int cy, Weapon weapon) {
        int levels = Earthworks.flattening(weapon.weaponClass());
        if (levels <= 0) {
            return;
        }
        int radius = (int) weapon.blastRadius();
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                int dx = x - cx;
                int dy = y - cy;
                if (dx * dx + dy * dy > radius * radius) {
                    continue;
                }
                // Full effect at the crater, half out at the edge: a near miss shakes a
                // trench loose, a direct hit fills it in.
                boolean atCentre = dx * dx + dy * dy <= 1;
                map.addCover(x, y, -(atCentre ? levels : Math.max(1, levels / 2)));
            }
        }
    }

    /** Rounds currently in the air, for the renderer and for the digest. */
    public ShellLayer shells() {
        return shells;
    }

    /** Gas on the ground, for the renderer and for anything deciding where not to stand. */
    public GasLayer gas() {
        return gas;
    }

    /**
     * Lands everything whose time has come.
     *
     * <p>A shell holds a point and two integers, so it does not care whether the gun that fired
     * it still exists. Ownership is read off the shell rather than off the firer, which means a
     * battery captured mid-flight does not retrospectively change whose round is in the air.
     */
    private void updateShells() {
        int landed = 0;
        for (int i = 0; i < shells.count(); i++) {
            if (shells.impactTick(i) > tick) {
                continue;
            }
            impactShell(i);
            landed++;
        }
        if (landed > 0) {
            shells.removeLanded(tick);
        }
        profiler.countShellsInFlight(shells.count());
    }

    private void impactShell(int i) {
        Weapon weapon = shells.weapon(i);
        float x = shells.toX(i);
        float y = shells.toY(i);

        flattenGroundAt(shells.toTileX(i), shells.toTileY(i), weapon);
        // No direct-hit victim: a shell lands on ground. Whoever is standing at the impact
        // point takes the blast at full falloff, once - unlike every other blast weapon, which
        // damages its target and then splashes it a second time.
        applyBlastAt(shells.ownerId(i), shells.firedById(i), x, y, weapon, null);
        if (weapon.weaponClass() == WeaponClass.GAS) {
            gas.release(shells.toTileX(i), shells.toTileY(i), GasLayer.PER_SHELL);
        }

        events.add(GameEvent.at(GameEvent.Type.SHELL_IMPACT, shells.ownerId(i),
                shells.firedById(i), x, y));
    }

    /**
     * Puts a round in the air from a gun towards a tile.
     *
     * <p>The indirect-fire counterpart to {@code tryAttack}, and the differences are the whole
     * design: it is aimed at a place rather than a thing, nothing is decided until it arrives,
     * and firing gives the gun's own position away.
     *
     * @return true if the gun fired
     */
    public boolean tryBombard(Unit gun, int tileX, int tileY) {
        return tryBombard(gun, tileX, tileY, gun.weapon());
    }

    /**
     * The same, fired with something other than the gun's own weapon.
     *
     * <p>Mirrors {@code tryAttack}'s weapon-taking form, and exists for the same reason: it
     * lets the shell layer be exercised without depending on which units happen to carry a
     * gun this week.
     */
    public boolean tryBombard(Unit gun, int tileX, int tileY, Weapon weapon) {
        if (weapon == null || !gun.isAlive() || !gun.weaponReady()
                || !map.inBounds(tileX, tileY)) {
            return false;
        }
        float gap = gun.distanceTo(tileX + 0.5f, tileY + 0.5f);
        if (gap > weapon.range() || gap < weapon.minRange()) {
            return false;
        }

        int rounds = shells.fire(gun.ownerId(), gun.id(), weapon, gun.x(), gun.y(),
                tileX, tileY, weapon.salvo(), tick, TICKS_PER_SECOND);
        gun.faceToward(tileX + 0.5f, tileY + 0.5f);
        gun.startWeaponCooldown();
        gun.noteFired(tick);
        revealBattery(gun, weapon);

        events.add(GameEvent.shot(gun.ownerId(), gun.id(), gun.x(), gun.y(),
                tileX + 0.5f, tileY + 0.5f,
                ShellLayer.flightTicks(gun.x(), gun.y(), tileX + 0.5f, tileY + 0.5f,
                        TICKS_PER_SECOND),
                weapon.weaponClass(), GameEvent.TargetKind.NONE));
        return rounds > 0;
    }

    /**
     * A gun that has fired has told everyone where it is.
     *
     * <p>Two halves, and the second is the one that makes counter-battery a real move rather
     * than a line in a design document. Marking the gun revealed lets the enemy <em>see</em> it
     * through fog; stamping their sight memory at its position lets them <em>shoot back at</em>
     * it, because indirect fire needs somewhere it is allowed to aim. Without the second half
     * the reveal would only help a player who already had eyes on the battery, which is exactly
     * the player who did not need telling.
     */
    private void revealBattery(Unit gun, Weapon weapon) {
        if (!weapon.hasMinRange()) {
            return;
        }
        gun.markRevealed(tick + COUNTER_BATTERY_TICKS);
        for (int i = 0; i < players.size(); i++) {
            if (areEnemies(i, gun.ownerId())) {
                sightMemories.get(i).markTile(gun.tileX(), gun.tileY(), tick);
            }
        }
    }

    /** Rattles a target. Structures and vehicles do not flinch; the men inside are not modelled. */
    /**
     * What standing in the cloud costs.
     *
     * <p>Every {@code HURT_EVERY} ticks, a dose per level of concentration, and three rules that
     * are each one line of the doctrine. Cover does not reduce it - the trench is where gas
     * pools, so this bypasses {@code resolveDamage} entirely rather than special-casing it
     * there. Vehicles are sealed. And the Regime is masked: its infantry marched into this
     * doctrine wearing the answer, which is why gas is their weapon and not a coin-flip.
     *
     * <p>The kill is attributed to nobody ({@code attackerId} -1, the field's own default):
     * a cloud outlives the gun that threw it and can outlive the gunner's whole army.
     */
    private void applyGas() {
        if (!gas.any() || tick % GAS_HURT_EVERY != 0) {
            return;
        }
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            if (!unit.isAlive() || unit.type().isVehicle()) {
                continue;
            }
            int dose = gas.at(unit.tileX(), unit.tileY());
            if (dose <= 0 || player(unit.ownerId()).faction() == Faction.REGIME) {
                continue;
            }
            suppress(unit, dose * GAS_SUPPRESSION_PER_DOSE);
            boolean killed = unit.applyDamage(dose * GAS_DAMAGE_PER_DOSE, -1, tick);
            events.add(GameEvent.at(GameEvent.Type.UNDER_ATTACK, unit.ownerId(), unit.id(),
                    unit.x(), unit.y()));
            if (killed) {
                events.add(GameEvent.destroyed(unit.ownerId(), unit.id(), unit.x(), unit.y(),
                        GameEvent.TargetKind.INFANTRY));
            }
        }
    }

    private void suppress(Entity target, int amount) {
        if (target != null && !target.isBuilding() && !((Unit) target).type().isVehicle()) {
            ((Unit) target).addSuppression(amount);
        }
    }

    /**
     * Splash: everything hostile inside the blast radius takes a share of the damage, falling
     * off with distance from the point of impact.
     *
     * <p>Only enemies are hit. Friendly fire is the correct simulation and the wrong game — an
     * AI that shells its own advancing infantry is an AI that loses to itself.
     */
    private void applyBlast(Entity attacker, Entity epicentre, Weapon weapon) {
        applyBlastAt(attacker.ownerId(), attacker.id(), epicentre.x(), epicentre.y(),
                weapon, epicentre);
    }

    /**
     * The same, centred on a point.
     *
     * <p>The attacker is decomposed into the only two things the blast ever wanted from it —
     * an owner, to decide who is an enemy, and an id, to attribute the kill. That is what lets
     * a shell go on exploding correctly after the gun that fired it has been destroyed.
     *
     * @param directHit the entity that took the direct hit and has already been damaged, or
     *     null for a shell, which lands on ground and hits nobody twice
     */
    private void applyBlastAt(int ownerId, int attackerId, float ex, float ey, Weapon weapon,
                              Entity directHit) {
        float radius = weapon.blastRadius();

        // Scans the unit list rather than the spatial index on purpose: the index is only
        // rebuilt at the top of a tick, and tryAttack is public API that must not quietly
        // depend on that having happened. Blasts are infrequent enough for a linear pass.
        for (int i = 0; i < units.size(); i++) {
            Unit other = units.get(i);
            if (other == directHit || !other.isAlive()
                    || !areEnemies(ownerId, other.ownerId())) {
                continue;
            }
            if (Math.abs(other.x() - ex) > radius + 1f
                    || Math.abs(other.y() - ey) > radius + 1f) {
                continue;
            }
            splashOne(attackerId, other, weapon, ex, ey, radius);
        }
        for (int i = 0; i < buildings.size(); i++) {
            Building other = buildings.get(i);
            if (other == directHit || !other.isAlive()
                    || !areEnemies(ownerId, other.ownerId())) {
                continue;
            }
            splashOne(attackerId, other, weapon, ex, ey, radius);
        }
    }

    private void splashOne(int attackerId, Entity victim, Weapon weapon, float ex, float ey,
                           float radius) {
        if (!canEngage(weapon, victim)) {
            return;
        }
        float distance = victim.distanceTo(ex, ey) - victim.radius();
        if (distance > radius) {
            return;
        }
        float falloff = 1f - 0.75f * Math.max(0f, distance) / radius;
        int damage = Math.max(1, Math.round(resolveDamage(weapon, victim) * falloff));
        // The beaten zone is wider than the killing zone: men near a blast keep their heads
        // down whether or not anything reached them.
        suppress(victim, Math.round(Suppression.perShot(weapon.weaponClass()) * falloff));
        boolean killed = victim.applyDamage(damage, attackerId, tick);
        events.add(GameEvent.at(GameEvent.Type.UNDER_ATTACK, victim.ownerId(), victim.id(),
                victim.x(), victim.y()));
        if (killed) {
            events.add(GameEvent.destroyed(victim.ownerId(), victim.id(), victim.x(),
                    victim.y(), kindOf(victim)));
        }
    }

    /** Classifies an entity for the presentation layer: flesh, machine or masonry. */
    private static GameEvent.TargetKind kindOf(Entity e) {
        if (e.isBuilding()) {
            return GameEvent.TargetKind.STRUCTURE;
        }
        return ((Unit) e).type().isVehicle() ? GameEvent.TargetKind.VEHICLE
                : GameEvent.TargetKind.INFANTRY;
    }

    /** Range is measured to the target's edge, so big structures are hittable from outside. */
    public boolean inWeaponRange(Entity attacker, Entity target) {
        return inWeaponRange(attacker, target, attacker.weapon());
    }

    /**
     * The same, for a shot fired with something other than the attacker's own weapon.
     *
     * <p>This existed only in effect until now: {@code tryAttack} takes a weapon and then
     * range-checked against {@code attacker.weapon()} regardless, so a shot fired with a
     * borrowed weapon was measured against the wrong one. Nothing in the game does that — only
     * tests — which is why it never showed, and why fixing it moves no digest.
     */
    public boolean inWeaponRange(Entity attacker, Entity target, Weapon weapon) {
        if (weapon == null) {
            return false;
        }
        float gap = attacker.distanceTo(target) - target.radius();
        if (gap > weapon.range()) {
            return false;
        }
        // A minimum of zero has to mean "no minimum", not "no closer than zero". The gap is
        // measured to the target's edge and goes negative when two things overlap, so a plain
        // gap >= minRange comparison would have stopped a man hitting what he is standing on -
        // which is to say, it would have switched melee off.
        return !weapon.hasMinRange() || gap >= weapon.minRange();
    }

    /**
     * Nearest hostile entity within {@code radius} of a point.
     *
     * @param includeBuildings whether structures are considered; turrets ignore them so they
     *     shoot the thing walking at them rather than a wall in the distance
     */
    public Entity findNearestEnemy(int ownerId, float x, float y, float radius,
                                   boolean includeBuildings) {
        return findNearestEnemyBetween(ownerId, x, y, 0f, radius, includeBuildings, null);
    }

    /** The same, seeing only what {@code shooter} could actually hit. */
    public Entity findNearestEnemy(int ownerId, float x, float y, float radius,
                                   boolean includeBuildings, Weapon shooter) {
        return findNearestEnemyBetween(ownerId, x, y, 0f, radius, includeBuildings, shooter);
    }

    /**
     * Nearest hostile entity in a ring around a point, rather than a disc.
     *
     * <p>A weapon with a dead zone cannot use "nearest": a gun with a scout at one tile and a
     * whole section at six would pick the scout, fail its own range check and then stand there
     * saying nothing. Searching the ring it can actually reach is the difference between a
     * drawback and a bug.
     *
     * @param minRadius nothing closer than this counts, measured to the target's edge
     */
    public Entity findNearestEnemyBetween(int ownerId, float x, float y, float minRadius,
                                          float radius, boolean includeBuildings) {
        return findNearestEnemyBetween(ownerId, x, y, minRadius, radius, includeBuildings, null);
    }

    /**
     * The same, filtered to what a given weapon can engage.
     *
     * <p>The filter exists for the air layer and it matters in both directions: a rifleman who
     * acquires a gyrocopter chases a thing he can never hit for the rest of his life, and a
     * flak turret that acquires the nearest infantryman ignores the gunship it was built for.
     * Null means unfiltered, which is what spotting and strategic queries want.
     */
    public Entity findNearestEnemyBetween(int ownerId, float x, float y, float minRadius,
                                          float radius, boolean includeBuildings,
                                          Weapon shooter) {
        Entity best = null;
        float bestDist = Float.MAX_VALUE;

        queryScratch.clear();
        spatialIndex.query(x, y, radius + 1f, queryScratch);
        for (int i = 0; i < queryScratch.size(); i++) {
            Unit u = queryScratch.get(i);
            if (!u.isAlive() || !areEnemies(ownerId, u.ownerId())) {
                continue;
            }
            if (shooter != null && !canEngage(shooter, u)) {
                continue;
            }
            float d = u.distanceTo(x, y) - u.radius();
            // You cannot shoot what you cannot see, unless you have walked into it.
            if (u.isConcealed(tick) && d > STEALTH_REVEAL_RADIUS) {
                continue;
            }
            if (d <= radius && (minRadius <= 0f || d >= minRadius) && d < bestDist) {
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
                if (d <= radius && (minRadius <= 0f || d >= minRadius) && d < bestDist) {
                    bestDist = d;
                    best = b;
                }
            }
        }
        return best;
    }

    /**
     * Somewhere to stand that is far enough back to shoot from.
     *
     * <p>Everything else in the game reads "out of range" as "walk closer". A weapon with a
     * dead zone needs the opposite, and this is the one place that knows how: step directly
     * away from whatever is crowding the gun until it is a tile clear of the minimum, and give
     * up rather than walk into a wall.
     *
     * <p>Straight-line and square-root only — no trig — because where a unit stands feeds the
     * state digest, and {@code Math.sqrt} is exactly specified where {@code atan2} is not.
     *
     * @param out receives the tile; untouched if there is nowhere sensible to go
     * @return true if a tile was found
     */
    public boolean standOffTile(Unit unit, float fromX, float fromY, float minRange, int[] out) {
        float dx = unit.x() - fromX;
        float dy = unit.y() - fromY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001f) {
            // Standing exactly on it. Any direction will do; pick one deterministically.
            dx = 1f;
            dy = 0f;
            length = 1f;
        }
        dx /= length;
        dy /= length;
        float standOff = minRange + 1.5f;

        // Straight back first, then fanned to either side. Giving up because the one tile
        // directly behind happens to be a rock would leave a battery standing in the open being
        // eaten, which is not a drawback, it is the gun failing to obey a sensible order.
        for (int attempt = 0; attempt < 5; attempt++) {
            float ax = dx;
            float ay = dy;
            if (attempt > 0) {
                // Blend in the perpendicular, alternating sides and widening. All arithmetic:
                // a rotation would want trig, and where a unit stands feeds the digest.
                float lean = (attempt + 1) / 2 * 0.5f * ((attempt % 2 == 0) ? -1f : 1f);
                ax = dx - dy * lean;
                ay = dy + dx * lean;
                float len = (float) Math.sqrt(ax * ax + ay * ay);
                ax /= len;
                ay /= len;
            }
            int tileX = (int) (fromX + ax * standOff);
            int tileY = (int) (fromY + ay * standOff);
            if (map.inBounds(tileX, tileY) && map.isPassable(tileX, tileY)) {
                out[0] = tileX;
                out[1] = tileY;
                return true;
            }
        }
        return false;
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
