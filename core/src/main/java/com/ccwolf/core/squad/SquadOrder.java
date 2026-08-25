package com.ccwolf.core.squad;

/**
 * What a squad has been told to do.
 *
 * <p>An enum and a few fields rather than the {@code Order} interface the units use. A squad has
 * four things it can be doing and the logic for all of them lives in one place, so a hierarchy
 * of classes would be ceremony around a switch. Routing is not one of them: a broken squad is
 * not obeying an order, which is exactly why it is a flag on the squad and not a value here.
 */
public enum SquadOrder {

    /** Stand where you are. Still fights whatever comes into range. */
    HOLD,

    /** Get to a place. Does not stop for a fight it does not have to have. */
    MOVE,

    /** Get to a place, but stop and deal with anything hostile on the way. */
    ATTACK_MOVE,

    /** Kill one specific thing, and follow it to do so. */
    ATTACK,

    /**
     * Take a piece of ground and dig into it.
     *
     * <p>Moves like an attack-move — it stops for what it meets on the way — and then does not
     * move again. What makes it different is what the men do once they are standing still: they
     * put the ground up around themselves, so a position held long enough becomes one that has
     * to be shelled rather than walked into.
     */
    ENTRENCH
}
