package com.ccwolf.core.sim;

import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.ai.SkirmishAi;
import com.ccwolf.core.api.CommandBus;
import com.ccwolf.core.api.WorldView;
import com.ccwolf.core.entity.Doctrine;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.map.TileMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A configured 1v1 match: the world, the starting bases, and however many AI opponents.
 * Step this rather than the world directly so the AI gets to think.
 */
public final class Skirmish {

    private final GameWorld world;
    private final List<SkirmishAi> ais;
    private final int humanPlayerId;
    private final CommandBus commandBus;

    private Skirmish(GameWorld world, List<SkirmishAi> ais, int humanPlayerId) {
        this.world = world;
        this.ais = ais;
        this.humanPlayerId = humanPlayerId;
        this.commandBus = new CommandBus(world);
    }

    /** One human player against one AI. */
    public static Skirmish createVersusAi(TileMap map, Faction humanFaction, Difficulty difficulty,
                                          long seed) {
        return createVersusAi(map, humanFaction, difficulty, seed, null,
                Doctrine.pickFor(humanFaction.other(), seed));
    }

    /**
     * One human player against one AI, both fighting to a doctrine.
     *
     * <p>Either may be null, meaning that side fights the way everyone did before doctrines.
     * The AI's is passed in rather than chosen here so that whoever set the match up - the
     * picker, the harness, a test - remains the only thing that decides.
     */
    public static Skirmish createVersusAi(TileMap map, Faction humanFaction, Difficulty difficulty,
                                          long seed, Doctrine humanDoctrine,
                                          Doctrine aiDoctrine) {
        GameWorld world = new GameWorld(map, seed);
        Player human = world.addPlayer(humanFaction, false, humanFaction.displayName(),
                humanDoctrine);
        Player computer = world.addPlayer(humanFaction.other(), true,
                humanFaction.other().displayName(), aiDoctrine);

        placeBases(world);

        List<SkirmishAi> ais = new ArrayList<SkirmishAi>();
        ais.add(new SkirmishAi(computer.id(), difficulty));
        return new Skirmish(world, ais, human.id());
    }

    /**
     * Two AIs, used by the headless harness to shake out balance and stalls.
     *
     * <p>Both declare a doctrine, picked from the seed. This is the switch that turned the
     * feature on: the goldens replay through here, so the moment this line landed they moved -
     * once, deliberately, with the diff in the commit.
     */
    public static Skirmish createAiVersusAi(TileMap map, Difficulty difficulty, long seed) {
        return createAiVersusAi(map, difficulty, seed,
                Doctrine.pickFor(Faction.RESISTANCE, seed),
                Doctrine.pickFor(Faction.REGIME, seed));
    }

    /** Two AIs, each fighting to a doctrine. This is what the balance sweep drives. */
    public static Skirmish createAiVersusAi(TileMap map, Difficulty difficulty, long seed,
                                            Doctrine resistance, Doctrine regime) {
        GameWorld world = new GameWorld(map, seed);
        Player a = world.addPlayer(Faction.RESISTANCE, true, Faction.RESISTANCE.displayName(),
                resistance);
        Player b = world.addPlayer(Faction.REGIME, true, Faction.REGIME.displayName(), regime);

        placeBases(world);

        List<SkirmishAi> ais = new ArrayList<SkirmishAi>();
        ais.add(new SkirmishAi(a.id(), difficulty));
        ais.add(new SkirmishAi(b.id(), difficulty));
        return new Skirmish(world, ais, -1);
    }

    private static void placeBases(GameWorld world) {
        List<int[]> spawns = world.map().spawnPoints();
        for (int i = 0; i < world.players().size(); i++) {
            int[] spawn = spawns.get(i % spawns.size());
            world.createStartingBase(i, spawn[0], spawn[1]);
        }
    }

    public GameWorld world() {
        return world;
    }

    /** The only channel through which anything outside the simulation may change it. */
    public CommandBus commands() {
        return commandBus;
    }

    /** A read-only window for one player, for whatever is drawing the game. */
    public WorldView viewFor(int playerId) {
        return new WorldView(world, playerId);
    }

    public List<SkirmishAi> ais() {
        return Collections.unmodifiableList(ais);
    }

    /** Player id the local human controls, or -1 in an AI-only match. */
    public int humanPlayerId() {
        return humanPlayerId;
    }

    /** Runs the AI brains and then advances the world one tick. */
    public void step() {
        for (int i = 0; i < ais.size(); i++) {
            ais.get(i).update(world);
        }
        world.step();
    }
}
