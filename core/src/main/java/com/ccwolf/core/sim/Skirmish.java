package com.ccwolf.core.sim;

import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.ai.SkirmishAi;
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

    private Skirmish(GameWorld world, List<SkirmishAi> ais, int humanPlayerId) {
        this.world = world;
        this.ais = ais;
        this.humanPlayerId = humanPlayerId;
    }

    /** One human player against one AI. */
    public static Skirmish createVersusAi(TileMap map, Faction humanFaction, Difficulty difficulty,
                                          long seed) {
        GameWorld world = new GameWorld(map, seed);
        Player human = world.addPlayer(humanFaction, false, humanFaction.displayName());
        Player computer = world.addPlayer(humanFaction.other(), true,
                humanFaction.other().displayName());

        placeBases(world);

        List<SkirmishAi> ais = new ArrayList<SkirmishAi>();
        ais.add(new SkirmishAi(computer.id(), difficulty));
        return new Skirmish(world, ais, human.id());
    }

    /** Two AIs, used by the headless harness to shake out balance and stalls. */
    public static Skirmish createAiVersusAi(TileMap map, Difficulty difficulty, long seed) {
        GameWorld world = new GameWorld(map, seed);
        Player a = world.addPlayer(Faction.RESISTANCE, true, Faction.RESISTANCE.displayName());
        Player b = world.addPlayer(Faction.REGIME, true, Faction.REGIME.displayName());

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
