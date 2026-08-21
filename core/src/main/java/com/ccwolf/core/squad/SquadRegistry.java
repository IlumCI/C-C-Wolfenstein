package com.ccwolf.core.squad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every squad in the world.
 *
 * <p>Mirrors how {@code GameWorld} already keeps units: a list in creation order for anything
 * that iterates, and a map for anything that looks one up.
 *
 * <p><b>The map is never iterated.</b> {@code HashMap} ordering depends on hash values and
 * capacity, so iterating it and letting the result touch simulation state is the classic way to
 * make a seeded game stop reproducing. Anything that walks squads walks {@link #all()}.
 */
public final class SquadRegistry {

    private final List<Squad> squads = new ArrayList<Squad>();
    private final Map<Integer, Squad> byId = new HashMap<Integer, Squad>();

    /** Creation-ordered, and the only thing anything should iterate. */
    public List<Squad> all() {
        return squads;
    }

    public int size() {
        return squads.size();
    }

    public Squad byId(int squadId) {
        return byId.get(Integer.valueOf(squadId));
    }

    public void add(Squad squad) {
        squads.add(squad);
        byId.put(Integer.valueOf(squad.id()), squad);
    }

    public void remove(Squad squad) {
        squads.remove(squad);
        byId.remove(Integer.valueOf(squad.id()));
    }

    public void clear() {
        squads.clear();
        byId.clear();
    }
}
